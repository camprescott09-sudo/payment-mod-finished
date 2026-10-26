package com.example.paybuttons;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import com.example.paybuttons.mixin.ChatComponentAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Cams321 payment mod.
 *
 * Adds clickable [2X] and [X] buttons to incoming payment messages. Clicking a button runs
 * "/pay <name> <amount>". Type /paybuttons in chat to open the settings menu.
 */
public class PayButtonsMod implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("paybuttons");

	private static boolean openMenuNextTick = false;

	/** Each payment message gets an id so a click on one of its buttons can find that message again. */
	private static int nextId = 1;
	private static final int MAX_TRACKED = 500;
	/** id -> the original message without buttons. Swapped back in once a button is clicked. */
	private static final Map<String, Component> ORIGINALS = new LinkedHashMap<>();

	@Override
	public void onInitializeClient() {
		Config.get();

		// /paybuttons opens the settings menu. The screen opens on the next tick so the
		// chat window closing doesn't immediately close the menu again.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("paybuttons")
						.executes(context -> {
							openMenuNextTick = true;
							return 1;
						})
						// The buttons run this. It sends /pay itself and says what it sent.
						.then(ClientCommandManager.literal("pay")
								.then(ClientCommandManager.argument("name", StringArgumentType.word())
										.then(ClientCommandManager.argument("amount", StringArgumentType.word())
												.executes(context -> sendPayment(context.getSource(),
														StringArgumentType.getString(context, "name"),
														StringArgumentType.getString(context, "amount"), null))
												.then(ClientCommandManager.argument("id", StringArgumentType.word())
														.executes(context -> sendPayment(context.getSource(),
																StringArgumentType.getString(context, "name"),
																StringArgumentType.getString(context, "amount"),
																StringArgumentType.getString(context, "id"))))))))
						// [X] runs this: does nothing except remove the buttons.
						.then(ClientCommandManager.literal("dismiss")
								.then(ClientCommandManager.argument("id", StringArgumentType.word())
										.executes(context -> {
											removeButtons(Minecraft.getInstance(), StringArgumentType.getString(context, "id"));
											return 1;
										})))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openMenuNextTick) {
				openMenuNextTick = false;
				client.setScreen(new ConfigScreen(client.screen));
			}
		});

		// Small label in a screen corner.
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
			Config config = Config.get();
			Minecraft client = Minecraft.getInstance();
			if (!config.watermarkEnabled || client.options.hideGui
					|| config.watermarkText == null || config.watermarkText.isEmpty()) {
				return;
			}
			int textWidth = client.font.width(config.watermarkText);
			int margin = 4;
			boolean rightSide = config.watermarkCorner.endsWith("RIGHT");
			boolean topSide = config.watermarkCorner.startsWith("TOP");
			int x = rightSide ? graphics.guiWidth() - textWidth - margin : margin;
			int y = topSide ? margin : graphics.guiHeight() - client.font.lineHeight - margin;
			graphics.drawString(client.font, config.watermarkText, x, y, 0xFFFFFFFF, true);
		});

		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			Config config = Config.get();
			if (overlay || !config.enabled) {
				return message;
			}

			String text = message.getString();
			Matcher matcher = config.pattern().matcher(text);
			if (!matcher.find()) {
				if (text.contains("paid") && !text.startsWith("You paid")) {
					// Helps you fix the payment pattern: look for this line in logs/latest.log
					LOGGER.info("Saw a payment-like message that did not match: [{}]", text);
				}
				return message;
			}

			String name = group(matcher, "name", "name2");
			String rawNumber = group(matcher, "amount", "amount2");
			String rawSuffix = group(matcher, "suffix", "suffix2");
			if (name == null || rawNumber == null) {
				LOGGER.warn("Payment pattern needs groups named 'name' and 'amount'. Matched text: [{}]", text);
				return message;
			}
			String number = rawNumber.replace(",", "");
			String suffix = rawSuffix == null ? "" : rawSuffix.toLowerCase();

			String id = "p" + (nextId++);
			List<Component> buttons = makeButtons(name, number, suffix, id);
			if (buttons.isEmpty()) {
				return message;
			}

			ORIGINALS.put(id, message);
			while (ORIGINALS.size() > MAX_TRACKED) {
				ORIGINALS.remove(ORIGINALS.keySet().iterator().next());
			}
			MutableComponent result = message.copy();
			for (Component button : buttons) {
				result.append(Component.literal(" "));
				result.append(button);
			}
			return result;
		});
	}

	private static int sendPayment(FabricClientCommandSource source, String name, String amount, String id) {
		Minecraft client = Minecraft.getInstance();
		if (!name.matches("\\w{1,16}") || !amount.matches("[0-9.]+[kmb]?")) {
			source.sendFeedback(Component.literal("[Cams321 payment mod] Refused odd payment: " + name + " " + amount)
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		String command = "pay " + name + " " + amount;
		LOGGER.info("Sending command: /{}", command);
		source.sendFeedback(Component.literal("[Cams321 payment mod] Sending /" + command)
				.withStyle(ChatFormatting.GRAY));
		if (client.player == null) {
			return 0;
		}
		client.player.connection.sendCommand(command);
		if (id != null) {
			removeButtons(client, id);
		}
		return 1;
	}

	/** Swaps the clicked message back to its original text, so both buttons vanish and you can see what you've done. */
	private static void removeButtons(Minecraft client, String id) {
		Component original = ORIGINALS.remove(id);
		if (original == null) {
			return;
		}
		ChatComponent chat = client.gui.getChat();
		ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
		List<GuiMessage> all = accessor.paybuttons$getAllMessages();
		boolean changed = false;
		for (int i = 0; i < all.size(); i++) {
			GuiMessage m = all.get(i);
			if (hasButtonId(m.content(), id)) {
				all.set(i, new GuiMessage(m.addedTime(), original, m.signature(), m.tag()));
				changed = true;
			}
		}
		if (changed) {
			accessor.paybuttons$refreshTrimmedMessages();
		}
	}

	private static boolean hasButtonId(Component component, String id) {
		if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run
				&& run.command().endsWith(" " + id)) {
			return true;
		}
		for (Component sibling : component.getSiblings()) {
			if (hasButtonId(sibling, id)) {
				return true;
			}
		}
		return false;
	}

	/** Returns the first of the named groups that matched, or null if none did. */
	private static String group(Matcher matcher, String... names) {
		for (String groupName : names) {
			try {
				String value = matcher.group(groupName);
				if (value != null) {
					return value;
				}
			} catch (IllegalArgumentException ignored) {
				// This pattern has no group with that name
			}
		}
		return null;
	}

	/** A sample payment line with the current settings, used for the preview in the menu. */
	static Component preview() {
		MutableComponent line = Component.literal("You have been paid 5M from Steve.");
		for (Component button : makeButtons("Steve", "5", "m", null)) {
			line.append(Component.literal(" "));
			line.append(button);
		}
		return line;
	}

	private static List<Component> makeButtons(String name, String number, String suffix, String id) {
		Config config = Config.get();
		List<Component> buttons = new ArrayList<>();

		Component single = config.showX
				? button(config.xLabel, Config.colorOf(config.xColor), name, null, id)
				: null;

		// [R] pays back exactly what they sent
		Component refund = config.showR
				? button(config.rLabel, Config.colorOf(config.rColor), name, number + suffix, id)
				: null;

		Component multiplied = null;
		if (config.showDouble) {
			String amount = multiply(number, config.multiplierValue());
			if (amount != null) {
				multiplied = button(config.doubleLabel, Config.colorOf(config.doubleColor), name, amount + suffix, id);
			}
		}

		// Fixed order: [2X] [R] [X]
		if (multiplied != null) buttons.add(multiplied);
		if (refund != null) buttons.add(refund);
		if (single != null) buttons.add(single);
		return buttons;
	}

	private static Component button(String label, ChatFormatting color, String name, String amount, String id) {
		Config config = Config.get();
		boolean dismiss = amount == null;
		String command = dismiss ? "" : "/pay " + name + " " + amount;
		// Always goes through the mod command so it can remove the buttons after the click.
		String clickCommand = dismiss
				? "/paybuttons dismiss " + (id == null ? "none" : id)
				: "/paybuttons pay " + name + " " + amount + (id == null ? "" : " " + id);
		Style style = Style.EMPTY
				.withColor(color)
				.withBold(config.bold)
				.withClickEvent(new ClickEvent.RunCommand(clickCommand));
		if (config.tooltip) {
			style = style.withHoverEvent(new HoverEvent.ShowText(Component.literal(dismiss ? "Dismiss (does nothing)" : "Run " + command)));
		}
		return Component.literal(label).setStyle(style);
	}

	private static String multiply(String number, BigDecimal factor) {
		try {
			return new BigDecimal(number).multiply(factor).stripTrailingZeros().toPlainString();
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
