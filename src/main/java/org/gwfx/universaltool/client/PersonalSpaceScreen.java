package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.PersonalSpaceActionPayload;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Prototype;
import org.gwfx.universaltool.space.PrivateSpaceRules;

/**
 * Create / visit / change-password terminal for a personal space gate.
 *
 * <p>Layout is built from the constants below rather than from measured coordinates. Two constraints
 * shape them:
 *
 * <ul>
 *   <li>The panel is 320x236. Minecraft never reports a logical resolution below 320x240
 *       ({@code Window.BASE_WIDTH/BASE_HEIGHT}), so this fits every GUI scale, including the
 *       awkward 854x480 case. The previous 344x248 panel was clipped there.
 *   <li>{@link Screen#render} is deliberately not called: it would run {@code renderBackground} a
 *       second time and blur the panel. The consequence is that {@code renderWithTooltip} never
 *       runs, so anything a widget registers through {@code setTooltipForNextRenderPass} would be
 *       silently dropped — chip tooltips are therefore drawn by hand at the end of {@link #render}.
 * </ul>
 */
public class PersonalSpaceScreen extends Screen {
    public static final int CREATE = 0;
    public static final int VISIT = 1;
    public static final int CHANGE_PASSWORD = 2;

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 236;
    /** Distance from the panel edge to the form controls. */
    private static final int MARGIN = 24;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - MARGIN * 2;
    private static final int FIELD_HEIGHT = 20;
    /**
     * An {@link EditBox} places its text at its own origin when borderless, so it is inset by these
     * amounts to sit inside the frame {@code drawInputFrame} paints around the same rectangle.
     */
    private static final int FIELD_TEXT_INSET_Y = 6;
    private static final int FIELD_TEXT_INSET_X = 6;

    /**
     * The panel texture owns the band above row 51 and the separators at rows 50..51 and 200..201,
     * so the form well is rows 56..200 and the two buttons sit below it at 210..230.
     */
    private static final int BUTTON_Y = 210;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 132;

    /** Gap between a label's row and the top edge of its input frame. */
    private static final int LABEL_TO_FIELD = 10;

    private static final int CHIP_SIZE = 24;
    private static final int CHIP_GAP = 6;
    private static final int EMBLEM_SIZE = 16;
    private static final int CHIP_INSET = (CHIP_SIZE - EMBLEM_SIZE) / 2;

    private static final int SILHOUETTE_HEIGHT = 12;

    /**
     * The two password fields share one row, label above each. Stacking them instead would not fit:
     * CREATE needs 9+24+9+12+9 (heading, chips, name, silhouette, description) before the fields,
     * and two stacked labelled rows plus a status line overflow the 144px well.
     */
    private static final int FIELD_GAP = 8;
    private static final int FIELD_WIDTH = (CONTENT_WIDTH - FIELD_GAP) / 2;

    // CREATE: heading, chip row, name, silhouette, description, the field pair, status line.
    private static final int CREATE_HEADING_Y = 58;
    private static final int CREATE_CHIP_Y = 70;
    private static final int CREATE_NAME_Y = 98;
    private static final int CREATE_SILHOUETTE_Y = 110;
    private static final int CREATE_DESCRIPTION_Y = 128;
    private static final int CREATE_PASSWORD_LABEL_Y = 143;
    private static final int CREATE_STATUS_Y = 182;

    // VISIT: one full-width field, a hint, and a status line.
    private static final int VISIT_PASSWORD_LABEL_Y = 100;
    private static final int VISIT_HINT_Y = 145;
    private static final int VISIT_STATUS_Y = 162;

    // CHANGE_PASSWORD: the same field pair as CREATE, and a status line. Both of these modes have
    // far less content than CREATE, so their blocks are dropped to the well's optical centre.
    private static final int CHANGE_PASSWORD_LABEL_Y = 100;
    private static final int CHANGE_STATUS_Y = 145;

    private static final String EMBLEM_PATH = "textures/gui/environment_%s.png";
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "universal_tool", "textures/gui/personal_space_panel.png");

    private static final int COLOR_TITLE = 0xFFF0E8D6;
    private static final int COLOR_BODY = 0xFFB4C2D1;
    private static final int COLOR_LABEL = 0xFF80D5D3;
    private static final int COLOR_HINT = 0xFF9EB4C2;
    private static final int COLOR_ERROR = 0xFFE0645F;

    /** Ticks to wait for a server verdict before letting the player try again. */
    private static final int RESULT_TIMEOUT_TICKS = 100;

    private final BlockPos gatePos;
    private final int mode;
    private PrivateSpaceEnvironment selectedEnvironment = PrivateSpaceEnvironment.SUPERFLAT;
    private EditBox password;
    private EditBox confirmation;
    private PanelButton confirmButton;
    private final java.util.List<EnvironmentChip> chips = new java.util.ArrayList<>();

    private String errorKey;
    private boolean passwordInvalid;
    private boolean confirmationInvalid;
    private boolean submitting;
    private int submitTimeout;

    public PersonalSpaceScreen(BlockPos gatePos, int mode) {
        super(titleFor(mode));
        this.gatePos = gatePos;
        this.mode = mode;
    }

    private static Component titleFor(int mode) {
        return Component.translatable(switch (mode) {
            case VISIT -> "screen.universal_tool.personal_space.visit";
            case CHANGE_PASSWORD -> "screen.universal_tool.personal_space.change_password";
            default -> "screen.universal_tool.personal_space.create";
        });
    }

    private Component subtitle() {
        return Component.translatable(switch (mode) {
            case VISIT -> "screen.universal_tool.personal_space.visit_subtitle";
            case CHANGE_PASSWORD -> "screen.universal_tool.personal_space.change_subtitle";
            default -> "screen.universal_tool.personal_space.create_subtitle";
        });
    }

    private int left() { return width / 2 - PANEL_WIDTH / 2; }
    private int top() { return height / 2 - PANEL_HEIGHT / 2; }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int x = left();
        int y = top();
        chips.clear();

        if (mode == CREATE) {
            PrivateSpaceEnvironment[] options = PrivateSpaceEnvironment.values();
            int chipsWidth = options.length * CHIP_SIZE + (options.length - 1) * CHIP_GAP;
            int chipX = x + (PANEL_WIDTH - chipsWidth) / 2;
            for (PrivateSpaceEnvironment environment : options) {
                EnvironmentChip chip = new EnvironmentChip(chipX, y + CREATE_CHIP_Y, environment);
                chips.add(addRenderableWidget(chip));
                chipX += CHIP_SIZE + CHIP_GAP;
            }
        }

        int passwordLabelY = switch (mode) {
            case VISIT -> VISIT_PASSWORD_LABEL_Y;
            case CHANGE_PASSWORD -> CHANGE_PASSWORD_LABEL_Y;
            default -> CREATE_PASSWORD_LABEL_Y;
        };
        int fieldWidth = mode == VISIT ? CONTENT_WIDTH : FIELD_WIDTH;
        password = createPasswordBox(x + MARGIN, y + passwordLabelY + LABEL_TO_FIELD, fieldWidth,
                Component.translatable("screen.universal_tool.personal_space.password_hint"));
        addRenderableWidget(password);

        if (mode != VISIT) {
            confirmation = createPasswordBox(x + MARGIN + FIELD_WIDTH + FIELD_GAP,
                    y + passwordLabelY + LABEL_TO_FIELD, FIELD_WIDTH,
                    Component.translatable("screen.universal_tool.personal_space.confirm_hint"));
            addRenderableWidget(confirmation);
        }

        confirmButton = addRenderableWidget(new PanelButton(x + MARGIN, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.universal_tool.personal_space.confirm_action"), button -> submit(), true));
        addRenderableWidget(new PanelButton(x + PANEL_WIDTH - MARGIN - BUTTON_WIDTH, y + BUTTON_Y,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.universal_tool.personal_space.cancel"), button -> onClose(), false));
        setInitialFocus(password);
    }

    private EditBox createPasswordBox(int x, int y, int width, Component hint) {
        EditBox box = new EditBox(font, x + FIELD_TEXT_INSET_X, y + FIELD_TEXT_INSET_Y,
                width - FIELD_TEXT_INSET_X * 2, FIELD_HEIGHT - FIELD_TEXT_INSET_Y * 2,
                Component.translatable("screen.universal_tool.personal_space.password"));
        box.setMaxLength(PrivateSpaceRules.MAX_PASSWORD_LENGTH);
        box.setBordered(false);
        box.setTextColor(0xFFE5ECF7);
        box.setTextColorUneditable(0xFF8290A4);
        box.setHint(hint);
        box.setFormatter((value, index) -> FormattedCharSequence.forward("•".repeat(value.length()), Style.EMPTY));
        box.setResponder(value -> clearError());
        return box;
    }

    private void selectEnvironment(PrivateSpaceEnvironment environment) {
        selectedEnvironment = environment;
        clearError();
    }

    private void clearError() {
        errorKey = null;
        passwordInvalid = false;
        confirmationInvalid = false;
    }

    // ------------------------------------------------------------- submission

    private void submit() {
        if (submitting) return;
        clearError();
        String confirm = confirmation == null ? "" : confirmation.getValue();
        String secret = password.getValue();

        if (mode != VISIT) {
            if (secret.length() < PrivateSpaceRules.MIN_PASSWORD_LENGTH) {
                fail("message.universal_tool.personal_space.password_too_short", true, false);
                return;
            }
            if (secret.length() > PrivateSpaceRules.MAX_PASSWORD_LENGTH) {
                fail("message.universal_tool.personal_space.password_length", true, false);
                return;
            }
            if (!secret.equals(confirm)) {
                fail("message.universal_tool.personal_space.password_mismatch", false, true);
                return;
            }
        } else if (secret.isEmpty()) {
            fail("message.universal_tool.personal_space.password_required", true, false);
            return;
        }

        PacketDistributor.sendToServer(new PersonalSpaceActionPayload(gatePos, mode,
                selectedEnvironment.id(), secret, confirm));
        submitting = true;
        submitTimeout = RESULT_TIMEOUT_TICKS;
        confirmButton.active = false;
        confirmButton.setMessage(Component.translatable("screen.universal_tool.personal_space.submitting"));
    }

    private void fail(String key, boolean badPassword, boolean badConfirmation) {
        errorKey = key;
        passwordInvalid = badPassword;
        confirmationInvalid = badConfirmation;
    }

    /** Restores the form after a rejection, or on timeout when the server never answered. */
    private void stopSubmitting() {
        submitting = false;
        submitTimeout = 0;
        confirmButton.active = true;
        confirmButton.setMessage(Component.translatable("screen.universal_tool.personal_space.confirm_action"));
    }

    /** Called by {@code ClientScreens} when the server answers this screen's action. */
    public void onServerResult(PersonalSpaceResultPayload payload) {
        if (!gatePos.equals(payload.gatePos()) || payload.mode() != mode) return;
        if (payload.accepted()) {
            onClose();
            return;
        }
        stopSubmitting();
        boolean passwordProblem = payload.messageKey().endsWith("password_length")
                || payload.messageKey().endsWith("password_mismatch")
                || payload.messageKey().endsWith("wrong_password");
        fail(payload.messageKey(), passwordProblem, payload.messageKey().endsWith("password_mismatch"));
    }

    @Override
    public void tick() {
        if (!submitting) return;
        if (--submitTimeout > 0) return;
        stopSubmitting();
        fail("message.universal_tool.personal_space.create_failed", false, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys walk the chip row while no text field owns the keyboard.
        if (mode == CREATE && !chips.isEmpty() && !(getFocused() instanceof EditBox)
                && (keyCode == 263 || keyCode == 262)) {
            PrivateSpaceEnvironment[] options = PrivateSpaceEnvironment.values();
            int step = keyCode == 263 ? -1 : 1;
            selectEnvironment(options[Math.floorMod(selectedEnvironment.ordinal() + step, options.length)]);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        graphics.blit(PANEL_TEXTURE, x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.drawString(font, Component.translatable("screen.universal_tool.personal_space.kicker"),
                x + MARGIN + 2, y + 12, 0xFF83DAD7, false);
        graphics.drawCenteredString(font, title, x + PANEL_WIDTH / 2, y + 24, COLOR_TITLE);
        graphics.drawCenteredString(font, subtitle(), x + PANEL_WIDTH / 2, y + 36, COLOR_BODY);

        if (mode == CREATE) renderCreate(graphics, x, y);
        else if (mode == VISIT) renderVisit(graphics, x, y);
        else renderChangePassword(graphics, x, y);

        // Screen.render() invokes renderBackground again, blurring the panel if called here.
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);

        renderError(graphics, x, y);
        renderChipTooltip(graphics, mouseX, mouseY);
    }

    private void renderCreate(GuiGraphics graphics, int x, int y) {
        graphics.drawCenteredString(font, Component.translatable("screen.universal_tool.personal_space.choose_environment"),
                x + PANEL_WIDTH / 2, y + CREATE_HEADING_Y, 0xFFD6C7A5);
        // The chip row itself is drawn by EnvironmentChip, which is registered as a renderable widget.
        graphics.drawCenteredString(font, selectedEnvironment.displayName(),
                x + PANEL_WIDTH / 2, y + CREATE_NAME_Y, COLOR_TITLE);
        drawSilhouette(graphics, x + MARGIN, y + CREATE_SILHOUETTE_Y, CONTENT_WIDTH, SILHOUETTE_HEIGHT, selectedEnvironment);
        graphics.drawCenteredString(font,
                Component.translatable("screen.universal_tool.personal_space.environment_" + selectedEnvironment.id()),
                x + PANEL_WIDTH / 2, y + CREATE_DESCRIPTION_Y, COLOR_HINT);
        drawFieldPair(graphics, x, y, CREATE_PASSWORD_LABEL_Y);
    }

    private void renderVisit(GuiGraphics graphics, int x, int y) {
        label(graphics, "password_label", x + MARGIN, y + VISIT_PASSWORD_LABEL_Y);
        drawInputFrame(graphics, x + MARGIN, y + VISIT_PASSWORD_LABEL_Y + LABEL_TO_FIELD,
                CONTENT_WIDTH, FIELD_HEIGHT, password.isFocused(), passwordInvalid);
        graphics.drawCenteredString(font, Component.translatable("screen.universal_tool.personal_space.visit_hint"),
                x + PANEL_WIDTH / 2, y + VISIT_HINT_Y, COLOR_HINT);
    }

    private void renderChangePassword(GuiGraphics graphics, int x, int y) {
        drawFieldPair(graphics, x, y, CHANGE_PASSWORD_LABEL_Y);
    }

    /** The password/confirmation pair, which shares one row so both fit above the status line. */
    private void drawFieldPair(GuiGraphics graphics, int x, int y, int labelY) {
        label(graphics, "password_label", x + MARGIN, y + labelY);
        drawInputFrame(graphics, x + MARGIN, y + labelY + LABEL_TO_FIELD,
                FIELD_WIDTH, FIELD_HEIGHT, password.isFocused(), passwordInvalid);
        label(graphics, "confirm_label", x + MARGIN + FIELD_WIDTH + FIELD_GAP, y + labelY);
        drawInputFrame(graphics, x + MARGIN + FIELD_WIDTH + FIELD_GAP, y + labelY + LABEL_TO_FIELD,
                FIELD_WIDTH, FIELD_HEIGHT, confirmation.isFocused(), confirmationInvalid);
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable("screen.universal_tool.personal_space." + key),
                x, y, COLOR_LABEL, false);
    }

    private void renderError(GuiGraphics graphics, int x, int y) {
        if (errorKey == null) return;
        int statusY = switch (mode) {
            case VISIT -> VISIT_STATUS_Y;
            case CHANGE_PASSWORD -> CHANGE_STATUS_Y;
            default -> CREATE_STATUS_Y;
        };
        Component message = Component.translatable(errorKey, PrivateSpaceRules.MIN_PASSWORD_LENGTH,
                PrivateSpaceRules.MAX_PASSWORD_LENGTH);
        graphics.drawCenteredString(font, message, x + PANEL_WIDTH / 2, y + statusY, COLOR_ERROR);
    }

    /**
     * Hand-drawn because {@code Screen.renderWithTooltip} never runs on this screen; a widget-registered
     * tooltip would be queued and then dropped.
     */
    private void renderChipTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (EnvironmentChip chip : chips) {
            if (chip.hovered) {
                graphics.renderTooltip(font, chip.environment.displayName(), mouseX, mouseY);
                return;
            }
        }
    }

    private static void drawInputFrame(GuiGraphics graphics, int x, int y, int width, int height,
                                       boolean focused, boolean invalid) {
        int accent = invalid ? COLOR_ERROR : focused ? 0xFF73D7D2 : 0xFF59677B;
        graphics.fill(x, y, x + width, y + height, 0xFF0C1420);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF182433);
        graphics.fill(x, y, x + width, y + 1, accent);
        graphics.fill(x, y + height - 1, x + width, y + height, invalid ? COLOR_ERROR : 0xFF344457);
        graphics.fill(x, y, x + 1, y + height, invalid ? COLOR_ERROR : 0xFF344457);
        graphics.fill(x + width - 1, y, x + width, y + height, invalid ? COLOR_ERROR : 0xFF344457);
    }

    // ------------------------------------------------------------- silhouette

    /** A one-pixel-wide slice of the terrain profile, or {@code null} where there is no ground. */
    private record Slice(float top, float bottom) {}

    /**
     * A side view of the landscape, drawn from the same {@link Prototype} the generator uses so the
     * preview cannot drift away from what the player actually gets. Purely decorative: it reads no
     * world state and no seed.
     */
    private void drawSilhouette(GuiGraphics graphics, int x, int y, int width, int height,
                                PrivateSpaceEnvironment environment) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF22303F);
        graphics.fill(x, y, x + width, y + height, 0xFF0E1822);
        int baseline = y + height - 1;
        for (int column = 0; column < width; column++) {
            Slice slice = silhouetteSlice((float) column / (width - 1), environment.profile().prototype());
            if (slice == null) continue;
            int sliceTop = y + Math.round(slice.top() * (height - 1));
            int sliceBottom = y + Math.round(slice.bottom() * (height - 1));
            sliceTop = Math.max(y, Math.min(sliceTop, baseline));
            sliceBottom = Math.max(sliceTop + 1, Math.min(sliceBottom, baseline));
            graphics.fill(x + column, sliceTop, x + column + 1, sliceBottom, 0xFF1F4A52);
            graphics.fill(x + column, sliceTop, x + column + 1, Math.min(sliceTop + 2, sliceBottom), 0xFF43949A);
        }
    }

    private static Slice silhouetteSlice(float t, Prototype prototype) {
        if (prototype == Prototype.SKY) {
            // The only prototype that is not one solid mass: a broad centre with two satellites.
            float centre = 1 - Math.min(1, Math.abs(t - 0.5F) / 0.22F);
            if (centre > 0) return new Slice(0.30F, 0.30F + 0.50F * centre);
            float leftSatellite = 1 - Math.min(1, Math.abs(t - 0.15F) / 0.075F);
            if (leftSatellite > 0) return new Slice(0.14F, 0.14F + 0.18F * leftSatellite);
            float rightSatellite = 1 - Math.min(1, Math.abs(t - 0.85F) / 0.065F);
            if (rightSatellite > 0) return new Slice(0.60F, 0.60F + 0.16F * rightSatellite);
            return null;
        }
        return new Slice(switch (prototype) {
            case FLAT -> 0.32F;
            case ROLLING -> 0.36F + 0.14F * wave(t, 1.5F, 0.0F) + 0.06F * wave(t, 3.7F, 1.0F);
            case HIGHLAND -> 0.34F + 0.40F * peak(t, 0.30F, 0.10F) + 0.32F * peak(t, 0.72F, 0.07F);
            case DUNES -> 0.30F + 0.20F * wave(t, 1.0F, 0.35F) + 0.07F * wave(t, 2.3F, 0.0F);
            case TUNDRA -> 0.36F + 0.05F * wave(t, 2.5F, 0.2F);
            case PLATEAU -> 0.30F + 0.22F * (float) Math.floor((wave(t, 1.2F, 0.0F) + 1.0F) * 2.0F) / 2.0F;
            default -> 0.32F;
        }, 1.0F);
    }

    private static float wave(float t, float frequency, float phase) {
        return (float) Math.sin(t * Math.PI * 2 * frequency + phase);
    }

    private static float peak(float t, float centre, float spread) {
        float distance = (t - centre) / spread;
        return (float) Math.exp(-distance * distance);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------- widgets

    /** One landscape in the selector row: an emblem tile with selection and hover states. */
    private final class EnvironmentChip extends AbstractWidget {
        private final PrivateSpaceEnvironment environment;
        private final ResourceLocation emblem;
        private boolean hovered;

        private EnvironmentChip(int x, int y, PrivateSpaceEnvironment environment) {
            super(x, y, CHIP_SIZE, CHIP_SIZE, environment.displayName());
            this.environment = environment;
            this.emblem = ResourceLocation.fromNamespaceAndPath("universal_tool",
                    EMBLEM_PATH.formatted(environment.id()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.hovered = isHovered();
            boolean selected = environment == selectedEnvironment;
            int fill = selected ? 0xFF1B4C55 : hovered ? 0xFF2A3550 : 0xFF202B3B;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF0A111A);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
            int edge = selected ? 0xFF70CEC8 : hovered ? 0xFF9A7BD0 : 0xFF3A4859;
            outline(graphics, edge);
            if (selected) {
                // A one-pixel halo, drawn outside the tile so the row rhythm stays even.
                graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0x5570CEC8);
                graphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0x5570CEC8);
                graphics.fill(getX() - 1, getY(), getX(), getY() + height, 0x5570CEC8);
                graphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0x5570CEC8);
            }
            graphics.blit(emblem, getX() + CHIP_INSET, getY() + CHIP_INSET, 0, 0,
                    EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE);
        }

        private void outline(GuiGraphics graphics, int color) {
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, color);
            graphics.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height - 1, color);
            graphics.fill(getX() + 1, getY() + 1, getX() + 2, getY() + height - 1, color);
            graphics.fill(getX() + width - 2, getY() + 1, getX() + width - 1, getY() + height - 1, color);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            selectEnvironment(environment);
        }
    }

    private static final class PanelButton extends Button {
        private final boolean primary;

        private PanelButton(int x, int y, int width, int height, Component text, OnPress action) {
            this(x, y, width, height, text, action, false);
        }

        private PanelButton(int x, int y, int width, int height, Component text, OnPress action, boolean primary) {
            super(x, y, width, height, text, action, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int fill = !active ? 0xFF252D3B : isHovered()
                    ? (primary ? 0xFF286568 : 0xFF46546A)
                    : (primary ? 0xFF1C4A53 : 0xFF303C50);
            int edge = primary ? 0xFF70CEC8 : 0xFF7D8DA1;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF0A111A);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, active ? edge : 0xFF3B4553);
            graphics.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height - 1, 0xFF101925);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? 0xFFF1E9D8 : 0xFF8B95A3);
        }
    }
}
