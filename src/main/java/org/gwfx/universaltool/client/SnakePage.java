package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.november.mcphone.api.client.ui.IPhonePage;
import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.api.client.ui.PhoneStyle;
import com.november.mcphone.api.client.ui.PhoneViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.SnakeScoreRewardPayload;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Playable retro Snake mini-game page inside MCphone, adapting cleanly to both
 * portrait Phone (120x176) and landscape Tablet (226x158) screen layouts.
 */
public final class SnakePage implements IPhonePage {

    private static final int GRID_COLS = 14;
    private static final int GRID_ROWS = 14;
    private static final int NORMAL_FOOD_POINTS = 10;
    private static final int BONUS_FOOD_POINTS = 30;
    private static final int BONUS_DURATION_TICKS = 140;
    private static final int BONUS_SPAWN_INTERVAL = 5;
    private static final int REWARD_TARGET_SCORE = SnakeScoreRewardPayload.REQUIRED_SCORE;

    private static final int GRID_BG_DARK = 0xFF0E1B14;
    private static final int GRID_BG_LIGHT = 0xFF13241B;
    private static final int GRID_BORDER = 0xFF2B6E46;
    private static final int HUD_BG = 0x44000000;
    private static final int SNAKE_HEAD = 0xFF42F57E;
    private static final int SNAKE_EYE = 0xFF091A10;
    private static final int APPLE_RED = 0xFFE63946;
    private static final int APPLE_SHINE = 0xFFFF8FA3;
    private static final int APPLE_LEAF = 0xFF52D14B;
    private static final int BONUS_GOLD = 0xFFFFC82C;
    private static final int BONUS_GLOW = 0x88FFE680;
    private static final int BTN_ACCENT = 0xFF1E8C4E;
    private static final int BTN_ACCENT_HOVER = 0xFF28B064;

    private static int bestScore;

    private enum State {
        READY,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    private enum Dir {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        final int dx;
        final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        boolean isOpposite(Dir other) {
            return this.dx + other.dx == 0 && this.dy + other.dy == 0;
        }
    }

    private record Cell(int x, int y) {
    }

    private final Random random = new Random();
    private final List<Cell> snake = new ArrayList<>();
    private final Deque<Dir> dirQueue = new ArrayDeque<>(2);

    private State state = State.READY;
    private Dir currentDir = Dir.RIGHT;
    private Cell food = new Cell(10, 7);
    private Cell bonusFood;
    private int bonusTicksRemaining;
    private int applesEaten;
    private int score;
    private int runStartBest;
    private boolean beatHighScoreThisRun;
    private boolean rewardClaimSentThisRun;
    private int stepTickCounter;
    private int animTick;

    // Cached layout geometry for both rendering and mouse hit-testing
    private int cachedX = -1;
    private int cachedY = -1;
    private int cachedW = -1;
    private int cachedH = -1;
    private boolean landscape;
    private int gridX;
    private int gridY;
    private int cellSize = 8;
    private int gridW;
    private int gridH;

    private final Rect hudPauseBtn = new Rect();
    private final Rect hudRestartBtn = new Rect();
    private final Rect overlayActionBtn = new Rect();
    private final Rect dpadUp = new Rect();
    private final Rect dpadDown = new Rect();
    private final Rect dpadLeft = new Rect();
    private final Rect dpadRight = new Rect();

    public SnakePage() {
        resetBoard(State.READY);
    }

    @Override
    public boolean capturesKeyboard() {
        return true;
    }

    @Override
    public void onOpen() {
        if (state == State.GAME_OVER) {
            resetBoard(State.READY);
        }
    }

    @Override
    public void onClose() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    @Override
    public void onInputCancelled() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    @Override
    public void onResize(PhoneViewport viewport) {
        updateLayout(viewport.x(), viewport.y(), viewport.width(), viewport.height());
    }

    @Override
    public void tick() {
        animTick++;
        if (state != State.PLAYING) {
            return;
        }
        if (bonusFood != null) {
            bonusTicksRemaining--;
            if (bonusTicksRemaining <= 0) {
                bonusFood = null;
                bonusTicksRemaining = 0;
            }
        }

        stepTickCounter++;
        int interval = stepIntervalTicks();
        if (stepTickCounter >= interval) {
            stepTickCounter = 0;
            stepSnake();
        }
    }

    @Override
    public void render(PhoneCanvas canvas) {
        updateLayout(canvas.x(), canvas.y(), canvas.width(), canvas.height());
        GuiGraphics g = canvas.graphics();
        Font font = canvas.font();
        PhoneStyle style = canvas.style();

        g.fill(canvas.x(), canvas.y(), canvas.x() + canvas.width(), canvas.y() + canvas.height(),
                style.screenBackground());

        canvas.clipped(canvas.x(), canvas.y(), canvas.width(), canvas.height(), () -> {
            renderHud(canvas, g, font, style);
            renderGrid(g);
            renderDpad(canvas, g, font, style);
            if (state != State.PLAYING) {
                renderStateOverlay(canvas, g, font);
            }
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> {
                handleDirectionInput(Dir.UP);
                return true;
            }
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> {
                handleDirectionInput(Dir.DOWN);
                return true;
            }
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                handleDirectionInput(Dir.LEFT);
                return true;
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                handleDirectionInput(Dir.RIGHT);
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                if (state == State.READY || state == State.GAME_OVER) {
                    resetBoard(State.PLAYING);
                } else if (state == State.PLAYING) {
                    state = State.PAUSED;
                } else if (state == State.PAUSED) {
                    state = State.PLAYING;
                }
                playUiSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                resetBoard(State.PLAYING);
                playUiSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (hudRestartBtn.contains(mouseX, mouseY)) {
            resetBoard(State.PLAYING);
            playUiSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
            return true;
        }
        if (hudPauseBtn.contains(mouseX, mouseY)) {
            if (state == State.PLAYING) {
                state = State.PAUSED;
            } else if (state == State.PAUSED) {
                state = State.PLAYING;
            } else {
                resetBoard(State.PLAYING);
            }
            playUiSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
            return true;
        }

        if (state != State.PLAYING && overlayActionBtn.contains(mouseX, mouseY)) {
            if (state == State.PAUSED) {
                state = State.PLAYING;
            } else {
                resetBoard(State.PLAYING);
            }
            playUiSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
            return true;
        }

        if (dpadUp.contains(mouseX, mouseY)) {
            handleDirectionInput(Dir.UP);
            return true;
        }
        if (dpadDown.contains(mouseX, mouseY)) {
            handleDirectionInput(Dir.DOWN);
            return true;
        }
        if (dpadLeft.contains(mouseX, mouseY)) {
            handleDirectionInput(Dir.LEFT);
            return true;
        }
        if (dpadRight.contains(mouseX, mouseY)) {
            handleDirectionInput(Dir.RIGHT);
            return true;
        }

        // Clicking inside the grid while playing steers relative to the snake head
        if (state == State.PLAYING
                && mouseX >= gridX && mouseX < gridX + gridW
                && mouseY >= gridY && mouseY < gridY + gridH
                && !snake.isEmpty()) {
            Cell head = snake.get(0);
            double headCenterX = gridX + (head.x + 0.5D) * cellSize;
            double headCenterY = gridY + (head.y + 0.5D) * cellSize;
            double dx = mouseX - headCenterX;
            double dy = mouseY - headCenterY;
            if (Math.abs(dx) > Math.abs(dy)) {
                handleDirectionInput(dx >= 0 ? Dir.RIGHT : Dir.LEFT);
            } else {
                handleDirectionInput(dy >= 0 ? Dir.DOWN : Dir.UP);
            }
            return true;
        }
        return false;
    }

    private void handleDirectionInput(Dir dir) {
        if (state == State.READY) {
            state = State.PLAYING;
        } else if (state != State.PLAYING) {
            return;
        }
        Dir reference = dirQueue.isEmpty() ? currentDir : dirQueue.peekLast();
        if (dir == reference || dir.isOpposite(reference)) {
            return;
        }
        if (dirQueue.size() < 2) {
            dirQueue.addLast(dir);
        }
    }

    private void stepSnake() {
        if (!dirQueue.isEmpty()) {
            Dir next = dirQueue.pollFirst();
            if (!next.isOpposite(currentDir)) {
                currentDir = next;
            }
        }
        Cell head = snake.get(0);
        Cell nextHead = new Cell(head.x + currentDir.dx, head.y + currentDir.dy);

        if (nextHead.x < 0 || nextHead.x >= GRID_COLS || nextHead.y < 0 || nextHead.y >= GRID_ROWS) {
            triggerGameOver();
            return;
        }

        boolean eatingNormal = nextHead.equals(food);
        boolean eatingBonus = bonusFood != null && nextHead.equals(bonusFood);
        boolean growing = eatingNormal || eatingBonus;

        // Tail vacates its cell on a non-growing step, so exclude the last segment from self-hit.
        int checkLimit = growing ? snake.size() : snake.size() - 1;
        for (int i = 0; i < checkLimit; i++) {
            if (snake.get(i).equals(nextHead)) {
                triggerGameOver();
                return;
            }
        }

        snake.add(0, nextHead);
        if (!growing) {
            snake.remove(snake.size() - 1);
            return;
        }

        if (eatingNormal) {
            addScore(NORMAL_FOOD_POINTS);
            applesEaten++;
            spawnNormalFood();
            if (bonusFood == null && applesEaten % BONUS_SPAWN_INTERVAL == 0) {
                spawnBonusFood();
            }
            playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.05F);
        }
        if (eatingBonus) {
            addScore(BONUS_FOOD_POINTS);
            bonusFood = null;
            bonusTicksRemaining = 0;
            playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.38F);
        }
    }

    private void addScore(int points) {
        score += points;
        if (score > bestScore) {
            bestScore = score;
            if (runStartBest > 0 && !beatHighScoreThisRun) {
                beatHighScoreThisRun = true;
                playUiSound(SoundEvents.PLAYER_LEVELUP, 1.15F);
            }
        }
        if (score >= REWARD_TARGET_SCORE && !rewardClaimSentThisRun) {
            rewardClaimSentThisRun = true;
            PacketDistributor.sendToServer(new SnakeScoreRewardPayload(score));
            playUiSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
        }
    }

    private void triggerGameOver() {
        state = State.GAME_OVER;
        dirQueue.clear();
        playUiSound(SoundEvents.VILLAGER_NO, 0.9F);
    }

    private int stepIntervalTicks() {
        int speedLevel = applesEaten / 4;
        return Math.max(2, 5 - speedLevel);
    }

    private void resetBoard(State targetState) {
        snake.clear();
        int cy = GRID_ROWS / 2;
        int cx = GRID_COLS / 2 - 1;
        snake.add(new Cell(cx, cy));
        snake.add(new Cell(cx - 1, cy));
        snake.add(new Cell(cx - 2, cy));

        dirQueue.clear();
        currentDir = Dir.RIGHT;
        score = 0;
        applesEaten = 0;
        bonusFood = null;
        bonusTicksRemaining = 0;
        stepTickCounter = 0;
        runStartBest = bestScore;
        beatHighScoreThisRun = false;
        rewardClaimSentThisRun = false;
        state = targetState;
        spawnNormalFood();
    }

    private void spawnNormalFood() {
        Cell cell = pickFreeCell();
        if (cell != null) {
            food = cell;
        }
    }

    private void spawnBonusFood() {
        Cell cell = pickFreeCell();
        if (cell != null) {
            bonusFood = cell;
            bonusTicksRemaining = BONUS_DURATION_TICKS;
        }
    }

    private Cell pickFreeCell() {
        List<Cell> free = new ArrayList<>(GRID_COLS * GRID_ROWS);
        for (int gx = 0; gx < GRID_COLS; gx++) {
            for (int gy = 0; gy < GRID_ROWS; gy++) {
                Cell candidate = new Cell(gx, gy);
                if (!snake.contains(candidate)
                        && (food == null || !candidate.equals(food))
                        && (bonusFood == null || !candidate.equals(bonusFood))) {
                    free.add(candidate);
                }
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        return free.get(random.nextInt(free.size()));
    }

    private void renderHud(PhoneCanvas canvas, GuiGraphics g, Font font, PhoneStyle style) {
        int x = canvas.x() + 4;
        int w = Math.max(20, canvas.width() - 8);
        int y = canvas.y() + 2;

        g.fill(x, y, x + w, y + 16, HUD_BG);
        g.fill(x, y + 15, x + w, y + 16, GRID_BORDER);

        String scoreText = Component.translatable("gui.universal_tool.snake.score", score).getString();
        String bestText = Component.translatable("gui.universal_tool.snake.best", bestScore).getString();

        int btnSpace = 30;
        int textAreaW = Math.max(20, w - btnSpace - 6);
        int halfTextW = textAreaW / 2;

        drawScaledText(g, font, scoreText, x + 3, y + 4, halfTextW - 2, SNAKE_HEAD, false);
        drawScaledText(g, font, bestText, x + 3 + halfTextW, y + 4, halfTextW - 2, BONUS_GOLD, false);

        renderSmallIconBtn(canvas, hudPauseBtn, state == State.PLAYING ? "||" : "▶", style);
        renderSmallIconBtn(canvas, hudRestartBtn, "↻", style);
    }

    private void renderGrid(GuiGraphics g) {
        // Frame around playfield
        g.fill(gridX - 1, gridY - 1, gridX + gridW + 1, gridY + gridH + 1, GRID_BORDER);

        for (int gx = 0; gx < GRID_COLS; gx++) {
            for (int gy = 0; gy < GRID_ROWS; gy++) {
                int px = gridX + gx * cellSize;
                int py = gridY + gy * cellSize;
                int bg = ((gx + gy) & 1) == 0 ? GRID_BG_DARK : GRID_BG_LIGHT;
                g.fill(px, py, px + cellSize, py + cellSize, bg);
            }
        }

        // Normal apple
        if (food != null) {
            int fx = gridX + food.x * cellSize;
            int fy = gridY + food.y * cellSize;
            int inset = Math.max(1, cellSize / 7);
            g.fill(fx + inset, fy + inset + 1, fx + cellSize - inset, fy + cellSize - inset, APPLE_RED);
            if (cellSize >= 6) {
                g.fill(fx + inset + 1, fy + inset + 1, fx + inset + 2, fy + inset + 2, APPLE_SHINE);
                g.fill(fx + cellSize / 2, fy + Math.max(0, inset - 1),
                        fx + cellSize / 2 + 2, fy + inset + 1, APPLE_LEAF);
            }
        }

        // Bonus golden apple with pulse + countdown bar
        if (bonusFood != null) {
            int bx = gridX + bonusFood.x * cellSize;
            int by = gridY + bonusFood.y * cellSize;
            if ((animTick / 4) % 2 == 0) {
                g.fill(bx, by, bx + cellSize, by + cellSize, BONUS_GLOW);
            }
            int inset = Math.max(1, cellSize / 8);
            g.fill(bx + inset, by + inset, bx + cellSize - inset, by + cellSize - inset, BONUS_GOLD);
            if (cellSize >= 6) {
                g.fill(bx + inset + 1, by + inset + 1, bx + inset + 2, by + inset + 2, 0xFFFFFFFF);
            }

            float ratio = Mth.clamp((float) bonusTicksRemaining / (float) BONUS_DURATION_TICKS, 0.0F, 1.0F);
            int barW = Math.round(gridW * ratio);
            g.fill(gridX, gridY + gridH + 1, gridX + gridW, gridY + gridH + 3, 0x66000000);
            g.fill(gridX, gridY + gridH + 1, gridX + barW, gridY + gridH + 3, BONUS_GOLD);
        }

        // Snake body from tail to head
        int len = snake.size();
        for (int i = len - 1; i >= 0; i--) {
            Cell seg = snake.get(i);
            int sx = gridX + seg.x * cellSize;
            int sy = gridY + seg.y * cellSize;

            if (i == 0) {
                g.fill(sx, sy, sx + cellSize, sy + cellSize, SNAKE_HEAD);
                drawSnakeEyes(g, sx, sy);
            } else {
                float t = len > 1 ? (float) i / (float) (len - 1) : 0.0F;
                int r = (int) Mth.lerp(t, 45, 16);
                int gr = (int) Mth.lerp(t, 212, 108);
                int b = (int) Mth.lerp(t, 105, 52);
                int color = 0xFF000000 | (r << 16) | (gr << 8) | b;
                int pad = (i == len - 1 && cellSize >= 6) ? 1 : 0;
                g.fill(sx + pad, sy + pad, sx + cellSize - pad, sy + cellSize - pad, color);
                if (cellSize >= 7 && i % 2 == 1) {
                    g.fill(sx + 2, sy + 2, sx + cellSize - 2, sy + cellSize - 2, 0x22FFFFFF);
                }
            }
        }
    }

    private void drawSnakeEyes(GuiGraphics g, int sx, int sy) {
        int eyeSize = cellSize >= 9 ? 2 : 1;
        int near = 1;
        int far = Math.max(1, cellSize - eyeSize - 1);
        switch (currentDir) {
            case UP -> {
                g.fill(sx + near, sy + near, sx + near + eyeSize, sy + near + eyeSize, SNAKE_EYE);
                g.fill(sx + far, sy + near, sx + far + eyeSize, sy + near + eyeSize, SNAKE_EYE);
            }
            case DOWN -> {
                g.fill(sx + near, sy + far, sx + near + eyeSize, sy + far + eyeSize, SNAKE_EYE);
                g.fill(sx + far, sy + far, sx + far + eyeSize, sy + far + eyeSize, SNAKE_EYE);
            }
            case LEFT -> {
                g.fill(sx + near, sy + near, sx + near + eyeSize, sy + near + eyeSize, SNAKE_EYE);
                g.fill(sx + near, sy + far, sx + near + eyeSize, sy + far + eyeSize, SNAKE_EYE);
            }
            case RIGHT -> {
                g.fill(sx + far, sy + near, sx + far + eyeSize, sy + near + eyeSize, SNAKE_EYE);
                g.fill(sx + far, sy + far, sx + far + eyeSize, sy + far + eyeSize, SNAKE_EYE);
            }
        }
    }

    private void renderDpad(PhoneCanvas canvas, GuiGraphics g, Font font, PhoneStyle style) {
        renderDpadButton(canvas, g, font, dpadUp, "▲", currentDir == Dir.UP, style);
        renderDpadButton(canvas, g, font, dpadLeft, "◄", currentDir == Dir.LEFT, style);
        renderDpadButton(canvas, g, font, dpadDown, "▼", currentDir == Dir.DOWN, style);
        renderDpadButton(canvas, g, font, dpadRight, "►", currentDir == Dir.RIGHT, style);

        if (landscape) {
            int infoX = dpadLeft.x;
            int infoW = Math.max(40, (dpadRight.x + dpadRight.w) - infoX);
            int infoY = gridY + 4;
            String lenText = Component.translatable("gui.universal_tool.snake.length", snake.size()).getString();
            drawScaledText(g, font, lenText, infoX, infoY, infoW, style.bodyColor(), false);
            String hintText = Component.translatable("gui.universal_tool.snake.hint").getString();
            drawScaledText(g, font, hintText, infoX, infoY + 12, infoW, style.subtleColor(), false);
            boolean reachedReward = score >= REWARD_TARGET_SCORE || bestScore >= REWARD_TARGET_SCORE;
            String rewardText = Component.translatable(
                    reachedReward
                            ? "gui.universal_tool.snake.reward_unlocked"
                            : "gui.universal_tool.snake.reward_goal_short",
                    REWARD_TARGET_SCORE
            ).getString();
            drawScaledText(g, font, rewardText, infoX, infoY + 24, infoW,
                    reachedReward ? SNAKE_HEAD : BONUS_GOLD, false);
        }
    }

    private static void renderDpadButton(PhoneCanvas canvas,
                                         GuiGraphics g,
                                         Font font,
                                         Rect rect,
                                         String glyph,
                                         boolean activeDir,
                                         PhoneStyle style) {
        boolean hovered = canvas.hovered(rect.x, rect.y, rect.w, rect.h);
        int bg = hovered ? style.buttonHoverColor() : style.buttonColor();
        g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
        if (hovered) {
            g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, style.pressedOverlay());
        }
        int border = activeDir ? SNAKE_HEAD : 0x44FFFFFF;
        g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, border);
        g.fill(rect.x, rect.y + rect.h - 1, rect.x + rect.w, rect.y + rect.h, border);

        int gx = rect.x + (rect.w - font.width(glyph)) / 2;
        int gy = rect.y + (rect.h - font.lineHeight) / 2;
        g.drawString(font, glyph, gx, gy, activeDir ? SNAKE_HEAD : style.bodyColor(), false);
    }

    private static void renderSmallIconBtn(PhoneCanvas canvas, Rect rect, String label, PhoneStyle style) {
        GuiGraphics g = canvas.graphics();
        Font font = canvas.font();
        boolean hovered = canvas.hovered(rect.x, rect.y, rect.w, rect.h);
        g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
                hovered ? style.buttonHoverColor() : style.buttonColor());
        int tx = rect.x + (rect.w - font.width(label)) / 2;
        int ty = rect.y + (rect.h - font.lineHeight) / 2;
        g.drawString(font, label, tx, ty, style.titleColor(), false);
    }

    private void renderStateOverlay(PhoneCanvas canvas, GuiGraphics g, Font font) {
        g.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0xAA08100C);

        String titleKey = switch (state) {
            case READY -> "gui.universal_tool.snake.title.ready";
            case PAUSED -> "gui.universal_tool.snake.title.paused";
            case GAME_OVER -> "gui.universal_tool.snake.title.game_over";
            case PLAYING -> "";
        };
        String btnKey = switch (state) {
            case READY -> "gui.universal_tool.snake.btn.start";
            case PAUSED -> "gui.universal_tool.snake.btn.resume";
            case GAME_OVER -> "gui.universal_tool.snake.btn.retry";
            case PLAYING -> "";
        };

        int titleColor = state == State.GAME_OVER ? APPLE_RED : SNAKE_HEAD;
        String titleStr = Component.translatable(titleKey).getString();
        drawScaledCenteredText(g, font, titleStr, gridX + 4, gridY + gridH / 2 - 26, gridW - 8, titleColor, true);

        if (state == State.GAME_OVER) {
            String sub = Component.translatable("gui.universal_tool.snake.final_score", score).getString();
            drawScaledCenteredText(g, font, sub, gridX + 4, gridY + gridH / 2 - 14, gridW - 8, BONUS_GOLD, false);
        } else {
            boolean reachedReward = score >= REWARD_TARGET_SCORE || bestScore >= REWARD_TARGET_SCORE;
            String goal = Component.translatable(
                    reachedReward
                            ? "gui.universal_tool.snake.reward_unlocked"
                            : "gui.universal_tool.snake.reward_goal",
                    REWARD_TARGET_SCORE
            ).getString();
            drawScaledCenteredText(g, font, goal, gridX + 4, gridY + gridH / 2 - 14, gridW - 8,
                    reachedReward ? SNAKE_HEAD : BONUS_GOLD, false);
        }

        boolean hovered = canvas.hovered(overlayActionBtn.x, overlayActionBtn.y,
                overlayActionBtn.w, overlayActionBtn.h);
        g.fill(overlayActionBtn.x, overlayActionBtn.y,
                overlayActionBtn.x + overlayActionBtn.w, overlayActionBtn.y + overlayActionBtn.h,
                hovered ? BTN_ACCENT_HOVER : BTN_ACCENT);
        g.fill(overlayActionBtn.x, overlayActionBtn.y,
                overlayActionBtn.x + overlayActionBtn.w, overlayActionBtn.y + 1, SNAKE_HEAD);
        g.fill(overlayActionBtn.x, overlayActionBtn.y + overlayActionBtn.h - 1,
                overlayActionBtn.x + overlayActionBtn.w, overlayActionBtn.y + overlayActionBtn.h, SNAKE_HEAD);

        String btnLabel = Component.translatable(btnKey).getString();
        int btnTextY = overlayActionBtn.y + (overlayActionBtn.h - font.lineHeight) / 2;
        drawScaledCenteredText(g, font, btnLabel, overlayActionBtn.x + 3, btnTextY,
                overlayActionBtn.w - 6, 0xFFFFFFFF, true);

        if (state == State.GAME_OVER) {
            boolean reachedReward = score >= REWARD_TARGET_SCORE || bestScore >= REWARD_TARGET_SCORE;
            String goal = Component.translatable(
                    reachedReward
                            ? "gui.universal_tool.snake.reward_unlocked"
                            : "gui.universal_tool.snake.reward_goal",
                    REWARD_TARGET_SCORE
            ).getString();
            drawScaledCenteredText(g, font, goal, gridX + 4, overlayActionBtn.y + overlayActionBtn.h + 5,
                    gridW - 8, reachedReward ? SNAKE_HEAD : BONUS_GOLD, false);
        }
    }

    private void updateLayout(int x, int y, int w, int h) {
        if (x == cachedX && y == cachedY && w == cachedW && h == cachedH) {
            return;
        }
        cachedX = x;
        cachedY = y;
        cachedW = w;
        cachedH = h;

        int hudRight = x + w - 4;
        hudRestartBtn.set(hudRight - 14, y + 3, 13, 12);
        hudPauseBtn.set(hudRight - 29, y + 3, 13, 12);

        landscape = w > h + 20;
        if (landscape) {
            // Tablet layout: grid on left, info + D-pad on right
            int availH = Math.max(70, h - 24);
            int availW = Math.max(70, w - 88);
            cellSize = Math.max(5, Math.min(availW / GRID_COLS, availH / GRID_ROWS));
            gridW = cellSize * GRID_COLS;
            gridH = cellSize * GRID_ROWS;
            gridX = x + 6;
            gridY = y + 20 + Math.max(0, (availH - gridH) / 2);

            int sideX = gridX + gridW + 8;
            int sideW = Math.max(48, (x + w - 4) - sideX);
            int padBtn = Math.min(20, Math.max(14, (sideW - 6) / 3));
            int dpadCenterX = sideX + sideW / 2;
            int dpadTopY = y + h - padBtn * 2 - 8;

            dpadUp.set(dpadCenterX - padBtn / 2, dpadTopY, padBtn, padBtn);
            dpadLeft.set(dpadCenterX - padBtn / 2 - padBtn - 2, dpadTopY + padBtn + 2, padBtn, padBtn);
            dpadDown.set(dpadCenterX - padBtn / 2, dpadTopY + padBtn + 2, padBtn, padBtn);
            dpadRight.set(dpadCenterX + padBtn / 2 + 2, dpadTopY + padBtn + 2, padBtn, padBtn);
        } else {
            // Phone layout: HUD -> centered square grid -> compact 2-row D-pad
            int dpadH = 34;
            int availH = Math.max(70, h - 22 - dpadH);
            int availW = Math.max(70, w - 8);
            cellSize = Math.max(5, Math.min(availW / GRID_COLS, availH / GRID_ROWS));
            gridW = cellSize * GRID_COLS;
            gridH = cellSize * GRID_ROWS;
            gridX = x + (w - gridW) / 2;
            gridY = y + 20 + Math.max(0, (availH - gridH) / 2);

            int btnW = 24;
            int btnH = 14;
            int cx = x + w / 2;
            int dpadTopY = gridY + gridH + 5;
            dpadUp.set(cx - btnW / 2, dpadTopY, btnW, btnH);
            dpadLeft.set(cx - btnW / 2 - btnW - 3, dpadTopY + btnH + 2, btnW, btnH);
            dpadDown.set(cx - btnW / 2, dpadTopY + btnH + 2, btnW, btnH);
            dpadRight.set(cx + btnW / 2 + 3, dpadTopY + btnH + 2, btnW, btnH);
        }

        int btnW = Math.min(76, Math.max(48, gridW - 18));
        overlayActionBtn.set(gridX + (gridW - btnW) / 2, gridY + gridH / 2 + 2, btnW, 18);
    }

    private static void playUiSound(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    private static void playUiSound(Holder<SoundEvent> sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    private static void drawScaledText(GuiGraphics g,
                                       Font font,
                                       String text,
                                       int x,
                                       int y,
                                       int maxWidth,
                                       int color,
                                       boolean shadow) {
        int rawW = font.width(text);
        if (rawW <= maxWidth || rawW <= 0 || maxWidth <= 4) {
            g.drawString(font, text, x, y, color, shadow);
            return;
        }
        float scale = Math.max(0.55F, (float) maxWidth / (float) rawW);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (font.lineHeight * (1.0F - scale)) * 0.5F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private static void drawScaledCenteredText(GuiGraphics g,
                                               Font font,
                                               String text,
                                               int x,
                                               int y,
                                               int maxWidth,
                                               int color,
                                               boolean shadow) {
        int rawW = font.width(text);
        if (rawW <= maxWidth || rawW <= 0 || maxWidth <= 4) {
            int drawX = x + Math.max(0, (maxWidth - rawW) / 2);
            g.drawString(font, text, drawX, y, color, shadow);
            return;
        }
        float scale = Math.max(0.55F, (float) maxWidth / (float) rawW);
        float scaledW = rawW * scale;
        float drawX = x + Math.max(0.0F, (maxWidth - scaledW) * 0.5F);
        float drawY = y + (font.lineHeight * (1.0F - scale)) * 0.5F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(drawX, drawY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private static final class Rect {
        int x;
        int y;
        int w;
        int h;

        void set(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
