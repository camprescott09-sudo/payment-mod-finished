package com.example.paybuttons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** The settings menu. Open it with the /paybuttons command. Settings save when you close it. */
public class ConfigScreen extends Screen {

	private record Label(String text, int x, int y) {
	}

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private EditBox patternBox;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Cams321 payment mod"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		Config c = Config.get();

		int left = width / 2 - 155;
		int right = width / 2 + 5;
		int y = 40;
		int pitch = 22;

		toggle(left, y, v -> "Buttons: " + (v ? "ON" : "OFF"), () -> c.enabled, v -> c.enabled = v);
		toggle(right, y, v -> "Bold buttons: " + (v ? "ON" : "OFF"), () -> c.bold, v -> c.bold = v);
		y += pitch;

		toggle(left, y, v -> "Show [2X]: " + (v ? "ON" : "OFF"), () -> c.showDouble, v -> c.showDouble = v);
		cycle(right, y, "[2X] colour: ", () -> c.doubleColor, v -> c.doubleColor = v, Config.COLORS);
		y += pitch;

		toggle(left, y, v -> "Show [R]: " + (v ? "ON" : "OFF"), () -> c.showR, v -> c.showR = v);
		cycle(right, y, "[R] colour: ", () -> c.rColor, v -> c.rColor = v, Config.COLORS);
		y += pitch;

		toggle(left, y, v -> "Show [X]: " + (v ? "ON" : "OFF"), () -> c.showX, v -> c.showX = v);
		cycle(right, y, "[X] colour: ", () -> c.xColor, v -> c.xColor = v, Config.COLORS);
		y += pitch;

		toggle(left, y, v -> "Hover tooltip: " + (v ? "ON" : "OFF"), () -> c.tooltip, v -> c.tooltip = v);
		toggle(right, y, v -> "Corner label: " + (v ? "ON" : "OFF"), () -> c.watermarkEnabled, v -> c.watermarkEnabled = v);
		y += pitch;

		cycle(left, y, "Corner: ", () -> c.watermarkCorner, v -> c.watermarkCorner = v, Config.CORNERS);
		y += pitch;

		y += 16;

		field(left, y, 74, "2X text", c.doubleLabel, v -> c.doubleLabel = v, 12);
		field(left + 79, y, 74, "R text", c.rLabel, v -> c.rLabel = v, 12);
		field(left + 158, y, 74, "X text", c.xLabel, v -> c.xLabel = v, 12);
		field(left + 237, y, 74, "Multiplier", c.multiplier, v -> c.multiplier = v, 8);
		y += pitch + 14;

		field(left, y, 310, "Corner label text", c.watermarkText, v -> c.watermarkText = v, 40);
		y += pitch + 14;

		patternBox = field(left, y, 310, "Payment message pattern (advanced)", c.paymentPattern, v -> c.paymentPattern = v, 300);
		y += pitch + 6;

		addRenderableWidget(Button.builder(Component.literal("Reset pattern"), b -> {
			c.paymentPattern = Config.DEFAULT_PATTERN;
			patternBox.setValue(Config.DEFAULT_PATTERN);
		}).bounds(left, y, 150, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(right, y, 150, 20).build());
	}

	private void toggle(int x, int y, Function<Boolean, String> text, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(Component.literal(text.apply(get.get())), b -> {
			boolean value = !get.get();
			set.accept(value);
			b.setMessage(Component.literal(text.apply(value)));
		}).bounds(x, y, 150, 20).build());
	}

	private void cycle(int x, int y, String prefix, Supplier<String> get, Consumer<String> set, String[] options) {
		addRenderableWidget(Button.builder(Component.literal(prefix + get.get()), b -> {
			int index = 0;
			for (int i = 0; i < options.length; i++) {
				if (options[i].equals(get.get())) {
					index = i;
					break;
				}
			}
			String next = options[(index + 1) % options.length];
			set.accept(next);
			b.setMessage(Component.literal(prefix + next));
		}).bounds(x, y, 150, 20).build());
	}

	private EditBox field(int x, int y, int w, String label, String value, Consumer<String> onChange, int maxLength) {
		labels.add(new Label(label, x, y - 11));
		EditBox box = new EditBox(font, x, y, w, 20, Component.literal(label));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setResponder(onChange);
		addRenderableWidget(box);
		return box;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
		graphics.drawCenteredString(font, PayButtonsMod.preview(), width / 2, 24, 0xFFFFFFFF);
		for (Label label : labels) {
			graphics.drawString(font, label.text(), label.x(), label.y(), 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		Config.save();
		minecraft.setScreen(parent);
	}
}
