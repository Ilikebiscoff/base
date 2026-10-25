package dev.basehunter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Teleport-scan loop: scan for a base -> /rtp -> click the world icon -> click the region icon -> wait for the
 * teleport -> scan again. Stops when a base is found (if "stop on find" is on).
 */
public class AutoFinder {
	public static final AutoFinder INSTANCE = new AutoFinder();

	public enum State { OFF, SCAN, SEND_RTP, MENU_1, MENU_2, WAIT_TELEPORT }

	private static final int MENU_TIMEOUT = 120;     // 6s per menu step before retrying /rtp
	private static final int TELEPORT_TIMEOUT = 20 * 40;
	private static final int CLICK_COOLDOWN = 6;

	private State state = State.OFF;
	private int timer;
	private int sinceClick;
	private Vec3 origin = Vec3.ZERO;
	private Object originLevel;
	public int teleports;

	public State state() {
		return state;
	}

	public boolean active() {
		return state != State.OFF;
	}

	public void toggle() {
		if (active()) stop("Stopped"); else start();
	}

	public void start() {
		state = State.SCAN;
		timer = 0;
		teleports = 0;
		say("Auto base search started");
	}

	public void stop(String why) {
		if (state == State.OFF) return;
		state = State.OFF;
		say(why + " - auto search off");
	}

	private void say(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[BaseHunter] " + msg));
	}

	public void tick(Minecraft mc) {
		if (state == State.OFF) return;
		if (mc.player == null || mc.level == null || mc.gameMode == null) return;
		Config cfg = Config.INSTANCE;
		timer++;
		sinceClick++;

		switch (state) {
			case SCAN -> {
				if (timer >= cfg.scanSeconds * 20) {
					state = State.SEND_RTP;
					timer = 0;
				}
			}
			case SEND_RTP -> {
				origin = mc.player.position();
				originLevel = mc.level;
				mc.player.connection.sendCommand(cfg.rtpCommand);
				state = State.MENU_1;
				timer = 0;
			}
			case MENU_1 -> {
				if (timer > MENU_TIMEOUT) { state = State.SEND_RTP; timer = 0; return; }
				if (clickByName(mc, cfg.firstClick)) { state = State.MENU_2; timer = 0; }
			}
			case MENU_2 -> {
				if (timer > MENU_TIMEOUT) { state = State.SEND_RTP; timer = 0; return; }
				if (clickByName(mc, cfg.secondClick)) { state = State.WAIT_TELEPORT; timer = 0; }
			}
			case WAIT_TELEPORT -> {
				boolean moved = mc.level != originLevel || mc.player.position().distanceToSqr(origin) > 64 * 64;
				if (moved) {
					if (mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
					teleports++;
					state = State.SCAN;
					timer = 0;
				} else if (timer > TELEPORT_TIMEOUT) {
					state = State.SEND_RTP;
					timer = 0;
				}
			}
			default -> {}
		}
	}

	/** Clicks the first slot whose item name contains {@code name} in the open container. Returns true if clicked. */
	private boolean clickByName(Minecraft mc, String name) {
		if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
		if (sinceClick < CLICK_COOLDOWN) return false;
		AbstractContainerMenu menu = mc.player.containerMenu;
		String needle = name.toLowerCase(Locale.ROOT);
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
				sinceClick = 0;
				return true;
			}
		}
		return false;
	}
}
