package org.gwfx.universaltool.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.PersonalSpaceActionPayload;
import org.gwfx.universaltool.network.PersonalSpaceResultPayload;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment;

/**
 * Single-screen Star Gate terminal:
 * <ul>
 *   <li>Freely choose and switch among any of the 8 shared exploration realms.</li>
 *   <li>Click the direct cosmic bar below to immediately enter the player's own 25x25x25
 *       UUID-bound space (no secondary menu, no passwords; Shift+Right-Click any starry block to return).</li>
 * </ul>
 */
public class PersonalSpaceScreen extends Screen {
    public static final int PAGE_REALMS = 0;

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 236;
    private static final int MARGIN = 24;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - MARGIN * 2;

    private static final int BUTTON_Y = 210;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 132;

    private static final int CHIP_SIZE = 24;
    private static final int CHIP_GAP = 5;
    private static final int EMBLEM_SIZE = 20;
    private static final int CHIP_INSET = (CHIP_SIZE - EMBLEM_SIZE) / 2;

    private static final int PREVIEW_TEX_WIDTH = 544;
    private static final int PREVIEW_TEX_HEIGHT = 136;
    private static final int SNAPSHOT_TEX_WIDTH = 272;
    private static final int SNAPSHOT_TEX_HEIGHT = 68;

    private static final int REALM_HEADING_Y = 55;
    private static final int REALM_CHIP_Y = 66;
    private static final int REALM_NAME_Y = 93;
    private static final int REALM_PREVIEW_Y = 104;
    private static final int REALM_PREVIEW_HEIGHT = 48;
    private static final int REALM_TRAITS_Y = 156;
    private static final int PRIVATE_ENTRY_BAR_Y = 169;
    private static final int PRIVATE_ENTRY_BAR_HEIGHT = 24;
    private static final int REALM_STATUS_Y = 195;

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

    private static final int RESULT_TIMEOUT_TICKS = 100;

    private final BlockPos gatePos;
    private PrivateSpaceEnvironment selectedEnvironment;
    private PanelButton enterRealmButton;
    private PanelButton enterPrivateButton;
    private final java.util.List<EnvironmentChip> chips = new java.util.ArrayList<>();

    private String errorKey;
    private boolean submitting;
    private int submitTimeout;
    private int pendingActionMode = -1;

    public PersonalSpaceScreen(BlockPos gatePos, int mode) {
        this(gatePos, mode, "", "", "");
    }

    public PersonalSpaceScreen(BlockPos gatePos, int mode, String ownerName, String environmentId) {
        this(gatePos, mode, ownerName, environmentId, "");
    }

    public PersonalSpaceScreen(BlockPos gatePos, int mode, String ownerName,
                               String environmentId, String spaceDimension) {
        super(Component.translatable("screen.universal_tool.personal_space.realms_title"));
        this.gatePos = gatePos;
        PrivateSpaceEnvironment parsed = PrivateSpaceEnvironment.byId(environmentId);
        this.selectedEnvironment = parsed != null ? parsed : PrivateSpaceEnvironment.SUPERFLAT;
    }

    private int left() { return width / 2 - PANEL_WIDTH / 2; }
    private int top() { return height / 2 - PANEL_HEIGHT / 2; }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        int x = left();
        int y = top();
        chips.clear();

        PrivateSpaceEnvironment[] options = PrivateSpaceEnvironment.values();
        int chipsWidth = options.length * CHIP_SIZE + (options.length - 1) * CHIP_GAP;
        int chipX = x + (PANEL_WIDTH - chipsWidth) / 2;
        for (PrivateSpaceEnvironment environment : options) {
            EnvironmentChip chip = new EnvironmentChip(chipX, y + REALM_CHIP_Y, environment);
            chips.add(addRenderableWidget(chip));
            chipX += CHIP_SIZE + CHIP_GAP;
        }

        enterPrivateButton = addRenderableWidget(new PanelButton(
                x + MARGIN, y + PRIVATE_ENTRY_BAR_Y, CONTENT_WIDTH, PRIVATE_ENTRY_BAR_HEIGHT,
                Component.translatable("screen.universal_tool.personal_space.open_private_menu"),
                Component.translatable("screen.universal_tool.personal_space.open_private_desc"),
                button -> submitAction(PersonalSpaceActionPayload.ACTION_ENTER_PRIVATE_SPACE),
                ButtonStyle.COSMIC
        ));

        enterRealmButton = addRenderableWidget(new PanelButton(
                x + MARGIN, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.universal_tool.personal_space.enter_realm"),
                null,
                button -> submitAction(PersonalSpaceActionPayload.ACTION_ENTER_REALM),
                ButtonStyle.PRIMARY
        ));
        addRenderableWidget(new PanelButton(
                x + PANEL_WIDTH - MARGIN - BUTTON_WIDTH, y + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.universal_tool.personal_space.close"),
                null,
                button -> onClose(),
                ButtonStyle.SECONDARY
        ));
    }

    private void selectEnvironment(PrivateSpaceEnvironment environment) {
        selectedEnvironment = environment;
        errorKey = null;
    }

    // ------------------------------------------------------------- submission

    private void submitAction(int actionMode) {
        if (submitting) return;
        errorKey = null;
        pendingActionMode = actionMode;
        PacketDistributor.sendToServer(new PersonalSpaceActionPayload(
                gatePos, actionMode, selectedEnvironment.id()));
        submitting = true;
        submitTimeout = RESULT_TIMEOUT_TICKS;
        enterRealmButton.active = false;
        enterPrivateButton.active = false;
        enterRealmButton.setMessage(Component.translatable("screen.universal_tool.personal_space.submitting"));
    }

    private void stopSubmitting() {
        submitting = false;
        submitTimeout = 0;
        pendingActionMode = -1;
        enterRealmButton.active = true;
        enterPrivateButton.active = true;
        enterRealmButton.setMessage(Component.translatable("screen.universal_tool.personal_space.enter_realm"));
    }

    /** Called by {@code ClientScreens} when the server answers this screen's action. */
    public void onServerResult(PersonalSpaceResultPayload payload) {
        if (!gatePos.equals(payload.gatePos())) return;
        if (pendingActionMode != -1 && payload.mode() != pendingActionMode) return;
        if (payload.accepted()) {
            onClose();
            return;
        }
        stopSubmitting();
        errorKey = payload.messageKey();
    }

    @Override
    public void tick() {
        if (!submitting) return;
        if (--submitTimeout > 0) return;
        stopSubmitting();
        errorKey = "message.universal_tool.personal_space.load_failed";
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!chips.isEmpty() && (keyCode == 263 || keyCode == 262)) {
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
        drawCentered(graphics, Component.translatable("screen.universal_tool.personal_space.realms_title"),
                x + PANEL_WIDTH / 2, y + 24, COLOR_TITLE);
        drawCentered(graphics, Component.translatable("screen.universal_tool.personal_space.realms_subtitle"),
                x + PANEL_WIDTH / 2, y + 36, COLOR_BODY);

        drawCentered(graphics, Component.translatable("screen.universal_tool.personal_space.choose_environment"),
                x + PANEL_WIDTH / 2, y + REALM_HEADING_Y, COLOR_LABEL);
        drawCentered(graphics, selectedEnvironment.displayName(),
                x + PANEL_WIDTH / 2, y + REALM_NAME_Y, COLOR_TITLE);

        ResourceLocation liveRealmSnapshot = PhotoManager.getOrLoadSpaceSnapshot("realm/" + selectedEnvironment.id());
        boolean hasLive = liveRealmSnapshot != null;
        ResourceLocation texture = hasLive ? liveRealmSnapshot : realmPreviewTexture(selectedEnvironment);
        drawPreviewViewport(graphics, x + MARGIN, y + REALM_PREVIEW_Y, CONTENT_WIDTH, REALM_PREVIEW_HEIGHT,
                texture, hasLive ? SNAPSHOT_TEX_WIDTH : PREVIEW_TEX_WIDTH,
                hasLive ? SNAPSHOT_TEX_HEIGHT : PREVIEW_TEX_HEIGHT, hasLive, "512×512");

        drawFittedCentered(graphics,
                Component.translatable("screen.universal_tool.personal_space.environment_" + selectedEnvironment.id()),
                x + PANEL_WIDTH / 2, y + REALM_TRAITS_Y, CONTENT_WIDTH, COLOR_HINT);

        // Screen.render() invokes renderBackground again, blurring the panel if called here.
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        if (errorKey != null) {
            drawCentered(graphics, Component.translatable(errorKey),
                    x + PANEL_WIDTH / 2, y + REALM_STATUS_Y, COLOR_ERROR);
        }
        renderChipTooltip(graphics, mouseX, mouseY);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static void drawFittedCentered(GuiGraphics graphics, Component text, int centerX, int y, int maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        if (textWidth <= maxWidth || textWidth <= 0) {
            graphics.drawString(font, text, centerX - textWidth / 2, y, color, false);
            return;
        }
        float scale = (float) maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + 4.0F * (1.0F - scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth / 2, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderChipTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (EnvironmentChip chip : chips) {
            if (chip.hovered) {
                renderTraitTooltip(graphics, chip.environment, mouseX, mouseY);
                return;
            }
        }
        int x = left();
        int y = top();
        if (mouseX >= x + MARGIN && mouseX < x + MARGIN + CONTENT_WIDTH
                && mouseY >= y + REALM_PREVIEW_Y && mouseY < y + REALM_PREVIEW_Y + REALM_PREVIEW_HEIGHT) {
            renderTraitTooltip(graphics, selectedEnvironment, mouseX, mouseY);
        }
    }

    private void renderTraitTooltip(GuiGraphics graphics, PrivateSpaceEnvironment env, int mouseX, int mouseY) {
        String id = env.id();
        java.util.List<Component> lines = java.util.List.of(
                env.displayName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("screen.universal_tool.personal_space.trait_time_" + id).withStyle(ChatFormatting.AQUA),
                Component.translatable("screen.universal_tool.personal_space.trait_spawn_" + id).withStyle(ChatFormatting.GREEN),
                Component.translatable("screen.universal_tool.personal_space.trait_ores_" + id).withStyle(ChatFormatting.YELLOW),
                Component.translatable("screen.universal_tool.personal_space.trait_perk_" + id).withStyle(ChatFormatting.LIGHT_PURPLE)
        );
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // --------------------------------------------------------- real-world viewport

    private static ResourceLocation realmPreviewTexture(PrivateSpaceEnvironment environment) {
        return ResourceLocation.fromNamespaceAndPath("universal_tool", PREVIEW_PATH.formatted(environment.id()));
    }

    private void drawPreviewViewport(GuiGraphics graphics, int x, int y, int width, int height,
                                     ResourceLocation texture, int texWidth, int texHeight,
                                     boolean liveSnapshot, String sizeTag) {
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

        graphics.fill(x, y, x + width, y + 2, 0x40000000);
        graphics.fill(x, y + height - 2, x + width, y + height, 0x48000000);

        int tagW = font.width(sizeTag);
        int badgeX = x + width - tagW - 6;
        int badgeY = y + height - 11;
        graphics.fill(badgeX - 2, badgeY - 1, x + width - 2, y + height - 2, 0x991A110A);
        graphics.drawString(font, sizeTag, badgeX, badgeY, 0xFFF7ECDA, false);

        if (liveSnapshot) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, 0xAA1A110A);
            graphics.fill(x + 4, y + 4, x + 8, y + 8, 0xFF6FE06B);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------- widgets

    private enum ButtonStyle {
        PRIMARY, SECONDARY, COSMIC
    }

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
                graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF1E140C);
                graphics.fill(x, y, x + width, y + height, 0xFFD6A84E);
                graphics.fill(x, y, x + width - 1, y + 1, 0xFFF2D27A);
                graphics.fill(x, y, x + 1, y + height - 1, 0xFFF2D27A);
                graphics.fill(x + 1, y + height - 1, x + width, y + height, 0xFF7A531C);
                graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFF7A531C);
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF241B14);
                graphics.fill(x, y, x + 1, y + 1, 0xFFFFF2BC);
                graphics.fill(x + width - 1, y, x + width, y + 1, 0xFFFFF2BC);
                graphics.fill(x, y + height - 1, x + 1, y + height, 0xFFB5863B);
                graphics.fill(x + width - 1, y + height - 1, x + width, y + height, 0xFF593E18);
            } else {
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
        private final Component subline;
        private final ButtonStyle style;

        private PanelButton(int x, int y, int width, int height, Component text, Component subline,
                            OnPress action, ButtonStyle style) {
            super(x, y, width, height, text, action, DEFAULT_NARRATION);
            this.subline = subline;
            this.style = style;
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
            } else if (style == ButtonStyle.PRIMARY) {
                border = hover ? 0xFFF2D27A : 0xFF2A1A08;
                fill = hover ? 0xFFA87328 : 0xFF8C5D1E;
                hi = hover ? 0xFFF7DC88 : 0xFFD6A84E;
                lo = hover ? 0xFF634112 : 0xFF4F330E;
            } else if (style == ButtonStyle.COSMIC) {
                border = hover ? 0xFF7BE8DF : 0xFF19132E;
                fill = hover ? 0xFF2D2558 : 0xFF1E193E;
                hi = hover ? 0xFF9AF5EE : 0xFF53A8A3;
                lo = hover ? 0xFF130F2B : 0xFF0E0B20;
            } else {
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

            if (subline != null) {
                int mainColor = !active ? 0xFF9C8B7A : (hover ? 0xFFE6FFFC : 0xFFB8FAF2);
                int subColor = !active ? 0xFF7F7163 : (hover ? 0xFFCDE9F0 : 0xFF95C2CE);
                int maxInner = width - 12;
                drawFittedCentered(graphics, getMessage(), x + width / 2, y + 3, maxInner, mainColor);
                drawFittedCentered(graphics, subline, x + width / 2, y + 13, maxInner, subColor);
                return;
            }

            int textColor = !active ? 0xFF9C8B7A
                    : (hover ? 0xFFFFF8D6 : (style == ButtonStyle.PRIMARY ? 0xFFFFF5D6 : 0xFFF2E5CF));
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2,
                    y + (height - 8) / 2, textColor);
        }
    }
}
