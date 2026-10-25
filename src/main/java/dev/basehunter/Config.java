package dev.basehunter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Plain Gson POJO stored in config/basehunter.json. Edit the strings there if your server's /rtp menu differs. */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basehunter.json");

	public static Config INSTANCE = new Config();

	// ESP
	public boolean baseEsp = true;
	public boolean baseBlockBoxes = true;
	public boolean playerEsp = true;
	public boolean tracers = true;
	public boolean hudInfo = true;

	// Detection
	public int minChests = 5;          // containers within clusterRadius to count as a base
	public int minScore = 25;          // OR total "base block" score within clusterRadius
	public int clusterRadius = 10;     // blocks

	// Alerts
	public boolean alertSound = true;
	public boolean alertFlash = true;
	public boolean stopOnFind = true;

	// Updates
	public boolean autoUpdate = true;   // check GitHub on launch and download new builds (applied on next restart)

	// Auto search (/rtp loop)
	public int scanSeconds = 12;       // how long to scan after each teleport
	public String rtpCommand = "rtp";  // command without the slash
	public String firstClick = "Overworld";
	public String secondClick = "NA East";

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			BaseHunterClient.LOGGER.warn("Could not read config, using defaults", e);
		}
		save();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(INSTANCE));
		} catch (Exception e) {
			BaseHunterClient.LOGGER.warn("Could not save config", e);
		}
	}
}
