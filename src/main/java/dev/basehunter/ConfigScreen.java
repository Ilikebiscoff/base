package dev.basehunter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Built only from stock buttons (no custom drawing) so it doesn't depend on the GUI render API.
 * Number buttons cycle through presets on click.
 */
public class ConfigScreen extends Screen {
	private static final int W = 150;
	private static final int H = 20;
	private static final int GAP = 4;

	private int col, row;

	public ConfigScreen() {
		super(Component.literal("BaseHunter"));
	}

	@Override
	protected void init() {
		Config c = Config.INSTANCE;
		int left = this.width / 2 - W - GAP / 2;
		int top = this.height / 2 - 124;
		col = 0;
		row = 0;

		Button title = Button.builder(Component.literal("BaseHunter  -  config"), b -> {})
			.bounds(this.width / 2 - W - GAP / 2, top - 26, W * 2 + GAP, H).build();
		title.active = false;
		this.addRenderableWidget(title);

		toggle(left, top, "Base ESP", () -> c.baseEsp, v -> c.baseEsp = v);
		toggle(left, top, "Container boxes", () -> c.baseBlockBoxes, v -> c.baseBlockBoxes = v);
		toggle(left, top, "Player ESP", () -> c.playerEsp, v -> c.playerEsp = v);
		toggle(left, top, "Tracers", () -> c.tracers, v -> c.tracers = v);
		toggle(left, top, "HUD info", () -> c.hudInfo, v -> c.hudInfo = v);
		toggle(left, top, "Alert sound", () -> c.alertSound, v -> c.alertSound = v);
		toggle(left, top, "Alert flash", () -> c.alertFlash, v -> c.alertFlash = v);
		toggle(left, top, "Stop on find", () -> c.stopOnFind, v -> c.stopOnFind = v);
		toggle(left, top, "Auto update", () -> c.autoUpdate, v -> c.autoUpdate = v);

		cycle(left, top, "Min containers", () -> c.minChests, v -> c.minChests = v, 2, 12, 1);
		cycle(left, top, "Min score", () -> c.minScore, v -> c.minScore = v, 8, 60, 4);
		cycle(left, top, "Cluster radius", () -> c.clusterRadius, v -> c.clusterRadius = v, 4, 24, 2);
		cycle(left, top, "Scan seconds", () -> c.scanSeconds, v -> c.scanSeconds = v, 4, 60, 4);

		int by = top + 7 * (H + GAP) + 8;
		Button auto = Button.builder(autoLabel(), b -> {
			AutoFinder.INSTANCE.toggle();
			b.setMessage(autoLabel());
		}).bounds(left, by, W, H).build();
		this.addRenderableWidget(auto);

		this.addRenderableWidget(Button.builder(Component.literal("Test alert"), b -> Alerts.test())
			.bounds(left + W + GAP, by, W, H).build());
		this.addRenderableWidget(Button.builder(Component.literal("Clear found bases"), b -> BaseDetector.INSTANCE.clearBases())
			.bounds(left, by + H + GAP, W, H).build());
		this.addRenderableWidget(Button.builder(Updater.statusLabel(), b -> {
			Updater.onButton();
			b.setMessage(Updater.statusLabel());
		}).bounds(left, by + 2 * (H + GAP), W, H).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(left + W + GAP, by + H + GAP, W, H).build());
	}

	private Component autoLabel() {
		return Component.literal("Auto search: " + (AutoFinder.INSTANCE.active() ? "ON" : "OFF"));
	}

	private void place(Button b) {
		this.addRenderableWidget(b);
		if (++col == 2) {
			col = 0;
			row++;
		}
	}

	private int x(int left) {
		return left + col * (W + GAP);
	}

	private int y(int top) {
		return top + row * (H + GAP);
	}

	private void toggle(int left, int top, String name, BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
		Button b = Button.builder(label(name, get.getAsBoolean() ? "ON" : "OFF"), btn -> {
			boolean v = !get.getAsBoolean();
			set.accept(v);
			btn.setMessage(label(name, v ? "ON" : "OFF"));
			Config.save();
		}).bounds(x(left), y(top), W, H).build();
		place(b);
	}

	private void cycle(int left, int top, String name, IntSupplier get, java.util.function.IntConsumer set, int min, int max, int step) {
		Button b = Button.builder(label(name, String.valueOf(get.getAsInt())), btn -> {
			int v = get.getAsInt() + step;
			if (v > max) v = min;
			set.accept(v);
			btn.setMessage(label(name, String.valueOf(v)));
			Config.save();
		}).bounds(x(left), y(top), W, H).build();
		place(b);
	}

	private static Component label(String name, String value) {
		return Component.literal(name + ": " + value);
	}

	@Override
	public void onClose() {
		Config.save();
		Minecraft.getInstance().setScreen(null);
	}
}
