package dev.basehunter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Scans chunks as they load for player-made blocks and groups them into base candidates.
 * Everything runs on the client main thread (chunk events + tick), no extra threads.
 */
public class BaseDetector {
	public static final BaseDetector INSTANCE = new BaseDetector();

	private static final int DECOR_CAP_PER_CHUNK = 6;
	private static final int MAX_HITS_PER_CHUNK = 400;
	private static final int SCAN_DELAY_TICKS = 4;
	private static final int SCANS_PER_TICK = 3;

	public record Hit(BlockPos pos, BlockInfo info) {}

	private static final class ChunkData {
		final List<Hit> hits = new ArrayList<>();
	}

	public static final class Base {
		public final ClientLevel level;
		public BlockPos center;
		public AABB bounds;
		public int chests;
		public int score;
		public List<BlockPos> containers = new ArrayList<>();
		public final long foundAtTick;

		Base(ClientLevel level, long tick) {
			this.level = level;
			this.foundAtTick = tick;
		}
	}

	private record Pending(LevelChunk chunk, long key, long dueTick) {}

	private final Map<Long, ChunkData> chunks = new HashMap<>();
	private final Set<Long> loaded = new HashSet<>();
	private final ArrayDeque<Pending> queue = new ArrayDeque<>();
	public final List<Base> bases = new ArrayList<>();
	private long tick;

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
	}

	private static long key(LevelChunk chunk) {
		return key(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
	}

	public void reset() {
		chunks.clear();
		loaded.clear();
		queue.clear();
		bases.clear();
	}

	public void onChunkLoad(LevelChunk chunk) {
		long k = key(chunk);
		loaded.add(k);
		queue.add(new Pending(chunk, k, tick + SCAN_DELAY_TICKS));
	}

	public void onChunkUnload(LevelChunk chunk) {
		long k = key(chunk);
		loaded.remove(k);
		chunks.remove(k);
	}

	public void tick(Minecraft mc) {
		tick++;
		if (mc.level == null) return;
		int budget = SCANS_PER_TICK;
		while (budget-- > 0 && !queue.isEmpty() && queue.peekFirst().dueTick() <= tick) {
			Pending p = queue.pollFirst();
			if (!loaded.contains(p.key())) continue;
			try {
				scan(mc.level, p.chunk(), p.key());
			} catch (Exception e) {
				BaseHunterClient.LOGGER.warn("Chunk scan failed", e);
			}
		}
	}

	private void scan(ClientLevel level, LevelChunk chunk, long k) {
		LevelChunkSection[] sections = chunk.getSections();
		int minY = level.getMinY();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		ChunkData data = new ChunkData();
		int decor = 0;

		for (int i = 0; i < sections.length && data.hits.size() < MAX_HITS_PER_CHUNK; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(BlockInfo.IS_INTERESTING)) continue;
			int baseY = minY + i * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						BlockInfo info = BlockInfo.of(state);
						if (info == null) continue;
						if (info.category() == BlockInfo.Category.DECOR && decor++ >= DECOR_CAP_PER_CHUNK) continue;
						data.hits.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z), info));
					}
				}
			}
		}

		if (data.hits.isEmpty()) {
			chunks.remove(k);
			return;
		}
		chunks.put(k, data);
		evaluate(level, chunk, data);
	}

	/** Looks at the 3x3 chunk area around a freshly scanned chunk for dense clusters. */
	private void evaluate(ClientLevel level, LevelChunk chunk, ChunkData centre) {
		Config cfg = Config.INSTANCE;
		int cx = chunk.getPos().getMinBlockX() >> 4;
		int cz = chunk.getPos().getMinBlockZ() >> 4;
		List<Hit> around = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkData d = chunks.get(key(cx + dx, cz + dz));
				if (d != null) around.addAll(d.hits);
			}
		}
		if (around.size() < 2) return;

		double r2 = (double) cfg.clusterRadius * cfg.clusterRadius;
		Base best = null;
		int bestScore = -1;
		int anchors = 0;
		for (Hit anchor : centre.hits) {
			if (anchors++ > 200) break;
			int chests = 0, score = 0;
			for (Hit h : around) {
				if (anchor.pos().distSqr(h.pos()) > r2) continue;
				score += h.info().weight();
				if (h.info().category() == BlockInfo.Category.CONTAINER) chests++;
			}
			if (score > bestScore && (chests >= cfg.minChests || score >= cfg.minScore)) {
				bestScore = score;
				Base b = new Base(level, tick);
				b.chests = chests;
				b.score = score;
				b.center = anchor.pos();
				best = b;
			}
		}
		if (best == null) return;

		// Collect the members of the winning cluster for drawing.
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Hit h : around) {
			if (best.center.distSqr(h.pos()) > r2) continue;
			BlockPos p = h.pos();
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
			if (h.info().category() != BlockInfo.Category.DECOR && best.containers.size() < 80) best.containers.add(p);
		}
		best.bounds = new AABB(minX - 0.5, minY - 0.5, minZ - 0.5, maxX + 1.5, maxY + 1.5, maxZ + 1.5);
		best.center = BlockPos.containing(best.bounds.getCenter());
		register(level, best);
	}

	private void register(ClientLevel level, Base candidate) {
		for (int i = 0; i < bases.size(); i++) {
			Base existing = bases.get(i);
			if (existing.level != level) continue;
			if (existing.center.distSqr(candidate.center) <= 48.0 * 48.0) {
				// Same base seen again (neighbouring chunk finished loading): keep the stronger reading, no new alert.
				if (candidate.score > existing.score) bases.set(i, candidate);
				return;
			}
		}
		bases.add(candidate);
		Alerts.baseFound(candidate);
	}

	public void clearBases() {
		bases.clear();
	}
}
