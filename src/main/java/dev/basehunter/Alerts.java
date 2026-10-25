package dev.basehunter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;

/** Sound + screen flash when a base is found. */
public final class Alerts {
	private static final int FLASH_TICKS = 50;
	private static final int[] SOUND_TICKS = {0, 4, 8, 14, 18, 22};

	private static int flashLeft = 0;
	private static int soundClock = -1;

	private Alerts() {}

	public static void baseFound(BaseDetector.Base base) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.INSTANCE;
		if (cfg.alertFlash) flashLeft = FLASH_TICKS;
		if (cfg.alertSound) soundClock = 0;
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(String.format(
				"[BaseHunter] Possible base at %d, %d, %d  (%d containers, score %d)",
				base.center.getX(), base.center.getY(), base.center.getZ(), base.chests, base.score
			)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		if (cfg.stopOnFind) AutoFinder.INSTANCE.stop("Base found");
	}

	/** Plays the alert without needing a real base (used by the GUI test button). */
	public static void test() {
		Config cfg = Config.INSTANCE;
		if (cfg.alertFlash) flashLeft = FLASH_TICKS;
		if (cfg.alertSound) soundClock = 0;
	}

	public static void tick(Minecraft mc) {
		if (flashLeft > 0) flashLeft--;
		if (soundClock >= 0) {
			for (int i = 0; i < SOUND_TICKS.length; i++) {
				if (SOUND_TICKS[i] == soundClock) {
					float pitch = (i % 2 == 0) ? 1.6f : 1.1f;
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 1.0f));
				}
			}
			soundClock++;
			if (soundClock > SOUND_TICKS[SOUND_TICKS.length - 1]) soundClock = -1;
		}
	}

	public static void renderFlash(GuiGraphicsExtractor g) {
		if (flashLeft <= 0) return;
		float fade = flashLeft / (float) FLASH_TICKS;
		boolean on = (flashLeft / 4) % 2 == 0;
		int alpha = (int) ((on ? 150 : 40) * fade);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color(alpha, 255, 30, 30));
	}
}
