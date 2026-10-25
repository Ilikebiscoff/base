package dev.basehunter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks the GitHub "latest" release for a newer build, downloads it next to the current jar as
 * basehunter.jar.new, and swaps it in when the game closes (a running jar can't be replaced on Windows).
 */
public final class Updater {
	private static final String API = "https://api.github.com/repos/Ilikebiscoff/base/releases/tags/latest";

	public enum Status { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

	private static final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
	private static volatile String remoteCommit = "";
	private static volatile String assetUrl = "";
	private static volatile boolean notify;
	private static boolean hookInstalled;

	private Updater() {}

	public static Status status() {
		return status.get();
	}

	private static String localCommit() {
		try {
			return FabricLoader.getInstance().getModContainer(BaseHunterClient.MOD_ID).orElseThrow()
				.getMetadata().getCustomValue(BaseHunterClient.MOD_ID).getAsObject().get("commit").getAsString();
		} catch (Exception e) {
			return "dev";
		}
	}

	private static Path currentJar() {
		try {
			ModContainer mod = FabricLoader.getInstance().getModContainer(BaseHunterClient.MOD_ID).orElseThrow();
			List<Path> paths = mod.getOrigin().getPaths();
			Path p = paths.get(0);
			return Files.isRegularFile(p) ? p : null; // null in a dev environment
		} catch (Exception e) {
			return null;
		}
	}

	public static void checkAsync(boolean manual) {
		if (!manual && !Config.INSTANCE.autoUpdate) return;
		if (!status.compareAndSet(Status.IDLE, Status.CHECKING)
			&& !status.compareAndSet(Status.UP_TO_DATE, Status.CHECKING)
			&& !status.compareAndSet(Status.FAILED, Status.CHECKING)) return;
		Thread t = new Thread(() -> {
			try {
				HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
					.connectTimeout(Duration.ofSeconds(10)).build();
				HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(API))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "BaseHunter-Updater")
					.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
				JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
				remoteCommit = json.get("body").getAsString().trim();
				assetUrl = json.getAsJsonArray("assets").get(0).getAsJsonObject().get("browser_download_url").getAsString();

				String local = localCommit();
				if (local.equals("dev") || local.equals(remoteCommit)) {
					status.set(Status.UP_TO_DATE);
					return;
				}
				status.set(Status.AVAILABLE);
				if (Config.INSTANCE.autoUpdate) download();
				else notify = true;
			} catch (Exception e) {
				BaseHunterClient.LOGGER.warn("Update check failed: {}", e.toString());
				status.set(Status.FAILED);
			}
		}, "BaseHunter-Updater");
		t.setDaemon(true);
		t.start();
	}

	private static void download() throws Exception {
		Path jar = currentJar();
		if (jar == null) {
			status.set(Status.UP_TO_DATE); // dev environment, nothing to replace
			return;
		}
		status.set(Status.DOWNLOADING);
		Path target = jar.resolveSibling("basehunter.jar.new");
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(10)).build();
		HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(assetUrl))
			.header("User-Agent", "BaseHunter-Updater").build(), HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) throw new IllegalStateException("download HTTP " + res.statusCode());
		try (InputStream in = res.body()) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
		if (Files.size(target) < 10_000) throw new IllegalStateException("download too small");
		installSwapHook(jar, target);
		status.set(Status.READY);
		notify = true;
	}

	private static synchronized void installSwapHook(Path oldJar, Path newJar) {
		if (hookInstalled) return;
		hookInstalled = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
				String o = oldJar.toAbsolutePath().toString(), n = newJar.toAbsolutePath().toString();
				ProcessBuilder pb = windows
					? new ProcessBuilder("cmd", "/c", "ping -n 4 127.0.0.1 >nul & move /y \"" + n + "\" \"" + o + "\"")
					: new ProcessBuilder("sh", "-c", "sleep 3; mv -f '" + n + "' '" + o + "'");
				pb.start();
			} catch (Exception ignored) {
			}
		}, "BaseHunter-Swap"));
	}

	/** Chat notice once the background thread has something to say. */
	public static void tick(Minecraft mc) {
		if (!notify || mc.player == null) return;
		notify = false;
		Status s = status.get();
		if (s == Status.READY) {
			mc.player.sendSystemMessage(Component.literal("[BaseHunter] Update downloaded - restart the game to apply it."));
		} else if (s == Status.AVAILABLE) {
			mc.player.sendSystemMessage(Component.literal("[BaseHunter] New version available - open the menu (Right Shift) and press Update."));
		}
	}

	public static Component statusLabel() {
		return Component.literal(switch (status.get()) {
			case IDLE -> "Check for updates";
			case CHECKING -> "Checking...";
			case UP_TO_DATE -> "Up to date (click to recheck)";
			case AVAILABLE -> "Update available - click";
			case DOWNLOADING -> "Downloading...";
			case READY -> "Update ready - restart game";
			case FAILED -> "Update check failed - retry";
		});
	}

	public static void onButton() {
		switch (status.get()) {
			case AVAILABLE -> {
				Thread t = new Thread(() -> {
					try {
						download();
					} catch (Exception e) {
						BaseHunterClient.LOGGER.warn("Update download failed: {}", e.toString());
						status.set(Status.FAILED);
					}
				}, "BaseHunter-Updater");
				t.setDaemon(true);
				t.start();
			}
			case IDLE, UP_TO_DATE, FAILED -> checkAsync(true);
			default -> {}
		}
	}
}
