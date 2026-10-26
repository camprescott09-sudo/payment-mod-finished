package com.example.paybuttons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Mod settings, saved to config/paybuttons.json. Edited through the /paybuttons menu. */
public class Config {

	/**
	 * Matches both DonutSMP payment messages:
	 *   "APEX1969 paid you $ 1M"
	 *   "You have been paid 1 from ETJAMBLES."
	 * A custom pattern must use the group names: name, amount and (optionally) suffix.
	 * The second form may use name2, amount2 and suffix2.
	 */
	public static final String DEFAULT_PATTERN =
			"^\\s*(?:\\.?(?<name>\\w{3,16}) (?:has )?paid you \\$?\\s*(?<amount>[\\d.,]+)\\s*(?<suffix>[kKmMbB]?)"
					+ "|You have been paid \\$?\\s*(?<amount2>[\\d.,]+)\\s*(?<suffix2>[kKmMbB]?)\\s+from\\s+\\.?(?<name2>\\w{3,16}))";

	/** Earlier defaults that missed real messages. Replaced automatically if found in a saved config. */
	private static final String[] OLD_DEFAULT_PATTERNS = {
			"^\\s*\\.?(\\w{3,16}) (?:has )?paid you \\$?([\\d.,]+)\\s*([kKmMbB]?)",
			"^\\s*You have been paid \\$?(?<amount>[\\d.,]+)\\s*(?<suffix>[kKmMbB]?)\\s+from\\s+\\.?(?<name>\\w{3,16})"
	};

	public static final String[] COLORS = {
			"RED", "GREEN", "GOLD", "YELLOW", "AQUA", "BLUE", "LIGHT_PURPLE", "WHITE", "GRAY"
	};

	public static final String[] CORNERS = {"BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_LEFT", "TOP_RIGHT"};

	public boolean enabled = true;
	public boolean showDouble = true;
	public boolean showX = true;
	public boolean showR = true;
	public String doubleLabel = "[2X]";
	public String xLabel = "[X]";
	public String rLabel = "[R]";
	public String multiplier = "2";
	public String doubleColor = "GREEN";
	public String xColor = "RED";
	public String rColor = "GOLD";
	public boolean bold = true;
	public boolean tooltip = true;
	public boolean watermarkEnabled = true;
	public String watermarkText = "Cams321 payment mod";
	public String watermarkCorner = "BOTTOM_LEFT";
	public String paymentPattern = DEFAULT_PATTERN;

	private transient String compiledFrom;
	private transient Pattern compiled;

	private static final Logger LOGGER = LoggerFactory.getLogger("paybuttons");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance;

	public static Config get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("paybuttons.json");
	}

	private static Config load() {
		Path path = file();
		if (Files.exists(path)) {
			try {
				Config loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Config.class);
				if (loaded != null) {
					for (String old : OLD_DEFAULT_PATTERNS) {
						if (old.equals(loaded.paymentPattern)) {
							loaded.paymentPattern = DEFAULT_PATTERN;
						}
					}
					// [R] used to default to AQUA; GOLD is Minecraft's orange
					if ("AQUA".equals(loaded.rColor)) {
						loaded.rColor = "GOLD";
					}
					return loaded;
				}
			} catch (Exception e) {
				LOGGER.warn("Could not read paybuttons.json, using defaults", e);
			}
		}
		return new Config();
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(get()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Could not save paybuttons.json", e);
		}
	}

	/** The payment pattern, falling back to the default if the saved one is empty or invalid. */
	public Pattern pattern() {
		String source = paymentPattern == null || paymentPattern.isBlank() ? DEFAULT_PATTERN : paymentPattern;
		if (compiled == null || !source.equals(compiledFrom)) {
			try {
				compiled = Pattern.compile(source);
			} catch (PatternSyntaxException e) {
				compiled = Pattern.compile(DEFAULT_PATTERN);
			}
			compiledFrom = source;
		}
		return compiled;
	}

	public BigDecimal multiplierValue() {
		try {
			BigDecimal value = new BigDecimal(multiplier.trim());
			if (value.signum() > 0) {
				return value;
			}
		} catch (Exception ignored) {
		}
		return BigDecimal.valueOf(2);
	}

	public static ChatFormatting colorOf(String name) {
		try {
			return ChatFormatting.valueOf(name);
		} catch (Exception e) {
			return ChatFormatting.WHITE;
		}
	}
}
