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
    private static final int FIELD_HEIGHT = 19;
    /**
     * An {@link EditBox} places its text at its own origin when borderless, so it is inset by these
     * amounts to sit inside the frame {@code drawInputFrame} paints around the same rectangle.
     */
    private static final int FIELD_TEXT_INSET_Y = 5;
    private static final int FIELD_TEXT_INSET_X = 6;

    /**
     * The panel texture owns the band above row 51 and the separators at rows 49..51 and 199..201,
     * so the form well is rows 52..198 and the two buttons sit below it at 210..230.
     */
    private static final int BUTTON_Y = 210;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 132;

    /** Gap between a label's row and the top edge of its input frame. */
    private static final int LABEL_TO_FIELD = 10;

    private static final int CHIP_SIZE = 24;
    private static final int CHIP_GAP = 5;
    private static final int EMBLEM_SIZE = 20;
    private static final int CHIP_INSET = (CHIP_SIZE - EMBLEM_SIZE) / 2;

    private static final int FIELD_GAP = 8;
    private static final int FIELD_WIDTH = (CONTENT_WIDTH - FIELD_GAP) / 2;

    private static final int PREVIEW_TEX_WIDTH = 544;
    private static final int PREVIEW_TEX_HEIGHT = 136;
    private static final int SNAPSHOT_TEX_WIDTH = 272;
    private static final int SNAPSHOT_TEX_HEIGHT = 68;

    // CREATE: heading, chip row, realm name, 272x42 real-world viewport, description, field pair, status.
    private static final int CREATE_HEADING_Y = 54;
    private static final int CREATE_CHIP_Y = 64;
    private static final int CREATE_NAME_Y = 90;
    private static final int CREATE_PREVIEW_Y = 102;
    private static final int CREATE_PREVIEW_HEIGHT = 42;
    private static final int CREATE_DESCRIPTION_Y = 148;
    private static final int CREATE_PASSWORD_LABEL_Y = 159;
    private static final int CREATE_STATUS_Y = 190;

    // VISIT & CHANGE_PASSWORD: 272x64 real-world viewport, realm info caption, password field(s), status.
    private static final int REALM_PREVIEW_Y = 58;
    private static final int REALM_PREVIEW_HEIGHT = 64;
    private static final int REALM_INFO_Y = 127;
    private static final int VISIT_PASSWORD_LABEL_Y = 140;
    private static final int VISIT_HINT_Y = 173;
    private static final int VISIT_STATUS_Y = 186;
    private static final int CHANGE_PASSWORD_LABEL_Y = 143;
    private static final int CHANGE_STATUS_Y = 180;

    private static final String EMBLEM_PATH = "textures/gui/environment_%s.png";
    private static final String PREVIEW_PATH = "textures/gui/previews/env_%s.png";
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "universal_tool", "textures/gui/personal_space_panel.png");

    private static final int COLOR_KICKER = 0xFF7A5E43;
    private static final int COLOR_TITLE = 0xFF3A281A;
    private static final int COLOR_BODY = 0xFF5C4735;
    private static final int COLOR_LABEL = 0xFF3A281A;
    private static final int COLOR_HINT = 0xFF5C4735;
    private static final int COLOR_ERROR = 0xFFA63429;

    /** Ticks to wait for a server verdict before letting the player try again. */
    private static final int RESULT_TIMEOUT_TICKS = 100;

    private final BlockPos gatePos;
    private final int mode;
    private final String ownerName;
    private final PrivateSpaceEnvironment realmEnvironment;
    private final String spaceDimension;
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
        this(gatePos, mode, "", "", "");
    }

    public PersonalSpaceScreen(BlockPos gatePos, int mode, String ownerName, String environmentId) {
        this(gatePos, mode, ownerName, environmentId, "");
    }

    public PersonalSpaceScreen(BlockPos gatePos, int mode, String ownerName,
                               String environmentId, String spaceDimension) {
        super(titleFor(mode));
        this.gatePos = gatePos;
        this.mode = mode;
        this.ownerName = ownerName == null ? "" : ownerName;
        PrivateSpaceEnvironment parsed = PrivateSpaceEnvironment.byId(environmentId);
        this.realmEnvironment = parsed != null ? parsed : PrivateSpaceEnvironment.SUPERFLAT;
        this.spaceDimension = spaceDimension == null ? "" : spaceDimension;
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
        box.setTextColor(0xFFF7ECDA);
        box.setTextColorUneditable(0xFF9C8972);
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
                x + MARGIN + 2, y + 12, COLOR_KICKER, false);
        drawCentered(graphics, title, x + PANEL_WIDTH / 2, y + 24, COLOR_TITLE);
        drawCentered(graphics, subtitle(), x + PANEL_WIDTH / 2, y + 36, COLOR_BODY);

        if (mode == CREATE) renderCreate(graphics, x, y);
        else if (mode == VISIT) renderVisit(graphics, x, y);
        else renderChangePassword(graphics, x, y);

        // Screen.render() invokes renderBackground again, blurring the panel if called here.
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);

        renderError(graphics, x, y);
        renderChipTooltip(graphics, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void renderCreate(GuiGraphics graphics, int x, int y) {
        drawCentered(graphics, Component.translatable("screen.universal_tool.personal_space.choose_environment"),
                x + PANEL_WIDTH / 2, y + CREATE_HEADING_Y, COLOR_LABEL);
        // The chip row itself is drawn by EnvironmentChip, which is registered as a renderable widget.
        drawCentered(graphics, selectedEnvironment.displayName(),
                x + PANEL_WIDTH / 2, y + CREATE_NAME_Y, COLOR_TITLE);
        drawPreviewViewport(graphics, x + MARGIN, y + CREATE_PREVIEW_Y, CONTENT_WIDTH, CREATE_PREVIEW_HEIGHT,
                realmPreviewTexture(selectedEnvironment), PREVIEW_TEX_WIDTH, PREVIEW_TEX_HEIGHT, false);
        drawCentered(graphics,
                Component.translatable("screen.universal_tool.personal_space.environment_" + selectedEnvironment.id()),
                x + PANEL_WIDTH / 2, y + CREATE_DESCRIPTION_Y, COLOR_HINT);
        drawFieldPair(graphics, x, y, CREATE_PASSWORD_LABEL_Y);
    }

    private void renderVisit(GuiGraphics graphics, int x, int y) {
        renderRealmHeaderViewport(graphics, x, y);
        label(graphics, "password_label", x + MARGIN, y + VISIT_PASSWORD_LABEL_Y);
        drawInputFrame(graphics, x + MARGIN, y + VISIT_PASSWORD_LABEL_Y + LABEL_TO_FIELD,
                CONTENT_WIDTH, FIELD_HEIGHT, password.isFocused(), passwordInvalid);
        drawCentered(graphics, Component.translatable("screen.universal_tool.personal_space.visit_hint"),
                x + PANEL_WIDTH / 2, y + VISIT_HINT_Y, COLOR_HINT);
    }

    private void renderChangePassword(GuiGraphics graphics, int x, int y) {
        renderRealmHeaderViewport(graphics, x, y);
        drawFieldPair(graphics, x, y, CHANGE_PASSWORD_LABEL_Y);
    }

    private void renderRealmHeaderViewport(GuiGraphics graphics, int x, int y) {
        ResourceLocation snapshot = spaceDimension.isEmpty() ? null : PhotoManager.getOrLoadSpaceSnapshot(spaceDimension);
        boolean live = snapshot != null;
        ResourceLocation texture = live ? snapshot : realmPreviewTexture(realmEnvironment);
        int texW = live ? SNAPSHOT_TEX_WIDTH : PREVIEW_TEX_WIDTH;
        int texH = live ? SNAPSHOT_TEX_HEIGHT : PREVIEW_TEX_HEIGHT;
        drawPreviewViewport(graphics, x + MARGIN, y + REALM_PREVIEW_Y, CONTENT_WIDTH, REALM_PREVIEW_HEIGHT,
                texture, texW, texH, live);

        Component envLine = Component.translatable("screen.universal_tool.personal_space.environment",
                realmEnvironment.displayName());
        Component caption = ownerName.isEmpty()
                ? envLine
                : Component.literal(ownerName + " · ").append(envLine);
        drawCentered(graphics, caption, x + PANEL_WIDTH / 2, y + REALM_INFO_Y, COLOR_TITLE);
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
        drawCentered(graphics, message, x + PANEL_WIDTH / 2, y + statusY, COLOR_ERROR);
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
        int border = invalid ? 0xFFC94438 : focused ? 0xFFD6A84E : 0xFF8C6A3E;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF1A110A);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF241B14);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF17100B);
    }

    // --------------------------------------------------------- real-world viewport

    private static ResourceLocation realmPreviewTexture(PrivateSpaceEnvironment environment) {
        return ResourceLocation.fromNamespaceAndPath("universal_tool", PREVIEW_PATH.formatted(environment.id()));
    }

    /**
     * Renders a burnished-brass framed panoramic world viewport showing either a live local player
     * snapshot or the authentic pre-captured realm preview.
     */
    private void drawPreviewViewport(GuiGraphics graphics, int x, int y, int width, int height,
                                     ResourceLocation texture, int texWidth, int texHeight, boolean liveSnapshot) {
        // Burnished brass & walnut miniature landscape frame.
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF2A1C12);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF9E7330);
        graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFFD6A84E);
        graphics.fill(x - 1, y - 1, x, y + height + 1, 0xFFD6A84E);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, 0xFF5C4028);
        graphics.fill(x + width, y, x + width + 1, y + height + 1, 0xFF5C4028);
        graphics.fill(x, y, x + width, y + height, 0xFF140E09);

        float srcH = texWidth * ((float) height / width);
        float vOffset = Math.max(0.0F, (texHeight - srcH) * 0.5F);
        int vHeight = Math.max(1, Math.min(texHeight, Math.round(srcH)));
        graphics.blit(texture, x, y, width, height, 0.0F, vOffset, texWidth, vHeight, texWidth, texHeight);

        // Subtle inner shadow at top & bottom edges for recessed depth.
        graphics.fill(x, y, x + width, y + 2, 0x40000000);
        graphics.fill(x, y + height - 2, x + width, y + height, 0x48000000);

        // Compact dimension tag in bottom-right corner.
        String tag = "512×512";
        int tagW = font.width(tag);
        int badgeX = x + width - tagW - 6;
        int badgeY = y + height - 11;
        graphics.fill(badgeX - 2, badgeY - 1, x + width - 2, y + height - 2, 0x991A110A);
        graphics.drawString(font, tag, badgeX, badgeY, 0xFFF7ECDA, false);

        if (mode != CREATE) {
            int gemColor = liveSnapshot ? 0xFF6FE06B : 0xFFD6A84E;
            graphics.fill(x + 3, y + 3, x + 9, y + 9, 0xAA1A110A);
            graphics.fill(x + 4, y + 4, x + 8, y + 8, gemColor);
        }
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
            int x = getX();
            int y = getY();

            if (selected) {
                // Gilded burnished-brass selection frame around dark walnut socket.
                graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF1E140C);
                graphics.fill(x, y, x + width, y + height, 0xFFD6A84E);
                graphics.fill(x, y, x + width - 1, y + 1, 0xFFF2D27A);
                graphics.fill(x, y, x + 1, y + height - 1, 0xFFF2D27A);
                graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFF7A531C);
                graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFF7A531C);
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF241B14);
                // Corner brass stud glints
                graphics.fill(x, y, x + 1, y + 1, 0xFFFFF2BC);
                graphics.fill(x + width - 1, y, x + width, y + 1, 0xFFFFF2BC);
                graphics.fill(x, y + height - 1, x + 1, y + height, 0xFFB5863B);
                graphics.fill(x + width - 1, y + height - 1, x + width, y + height, 0xFF593E18);
            } else {
                // Walnut & antique-bronze recessed medallion socket.
                graphics.fill(x, y, x + width, y + height, 0xFF1E140C);
                int hiRim = hovered ? 0xFFD6A84E : 0xFF8C6A3E;
                int loRim = hovered ? 0xFF7A531C : 0xFF4A331F;
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF2B1F16);
                graphics.fill(x + 1, y + 1, x + width - 1, y + 2, hiRim);
                graphics.fill(x + 1, y + 1, x + 2, y + height - 1, hiRim);
                graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, loRim);
                graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, loRim);
            }
            graphics.blit(emblem, x + CHIP_INSET, y + CHIP_INSET, EMBLEM_SIZE, EMBLEM_SIZE,
                    0.0F, 0.0F, 32, 32, 32, 32);
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
            int x = getX();
            int y = getY();
            boolean hover = active && isHovered();
            int border;
            int fill;
            int hi;
            int lo;
            if (!active) {
                border = 0xFF2B2017;
                fill = 0xFF47382B;
                hi = 0xFF635040;
                lo = 0xFF2E231A;
            } else if (primary) {
                // Burnished brass primary action button
                border = hover ? 0xFFF2D27A : 0xFF2A1A08;
                fill = hover ? 0xFFA87328 : 0xFF8C5D1E;
                hi = hover ? 0xFFF7DC88 : 0xFFD6A84E;
                lo = hover ? 0xFF634112 : 0xFF4F330E;
            } else {
                // Warm walnut oak secondary button
                border = hover ? 0xFFD6A84E : 0xFF1E140C;
                fill = hover ? 0xFF735134 : 0xFF5C4028;
                hi = hover ? 0xFFA67B52 : 0xFF8C6642;
                lo = hover ? 0xFF3E2918 : 0xFF332113;
            }

            graphics.fill(x, y, x + width, y + height, border);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, hi);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, hi);
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, lo);
            graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, lo);

            int textColor = !active ? 0xFF9C8B7A : (hover ? 0xFFFFF8D6 : (primary ? 0xFFFFF5D6 : 0xFFF2E5CF));
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2,
                    y + (height - 8) / 2, textColor);
        }
    }
}
