package dev.basehunter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseHunterClient implements ClientModInitializer {
	public static final String MOD_ID = "basehunter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping openGui;
	private static KeyMapping toggleAuto;

	@Override
	public void onInitializeClient() {
		Config.load();
		BlockInfo.init();
		Updater.checkAsync(false);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openGui = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.basehunter.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		toggleAuto = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.basehunter.auto", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, category));

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> BaseDetector.INSTANCE.onChunkLoad(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> BaseDetector.INSTANCE.onChunkUnload(chunk));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BaseDetector.INSTANCE.reset());

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "esp"), Esp::render);
	}

	private void tick(Minecraft mc) {
		while (openGui.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new ConfigScreen());
		}
		while (toggleAuto.consumeClick()) {
			if (mc.player != null) AutoFinder.INSTANCE.toggle();
		}
		Updater.tick(mc);
		BaseDetector.INSTANCE.tick(mc);
		AutoFinder.INSTANCE.tick(mc);
		Alerts.tick(mc);
	}
}
