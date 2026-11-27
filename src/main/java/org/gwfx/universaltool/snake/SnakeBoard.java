package org.gwfx.universaltool.snake;

import java.util.List;

/** A read-only board suitable for rendering and sending to the client. */
public record SnakeBoard(State state, Dir direction, List<Cell> snake, Cell food, Cell bonusFood,
                         int bonusTicks, int score, int bestScore) {
    public static final int GRID_COLS = 14;
    public static final int GRID_ROWS = 14;
    public static final int CELLS = GRID_COLS * GRID_ROWS;
    public static final int BONUS_DURATION_TICKS = 140;
    public static final int REWARD_TARGET_SCORE = 200;

    public SnakeBoard {
        snake = List.copyOf(snake);
    }

    public static SnakeBoard waiting() {
        return new SnakeBoard(State.READY, Dir.RIGHT, List.of(), null, null, 0, 0, 0);
    }

    public enum State { READY, PLAYING, PAUSED, GAME_OVER }

    public enum Dir {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        private final int dx;
        private final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public boolean isOpposite(Dir other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    public record Cell(int x, int y) {
        public Cell step(Dir dir) {
            return new Cell(x + dir.dx, y + dir.dy);
        }

        public boolean onBoard() {
            return x >= 0 && x < GRID_COLS && y >= 0 && y < GRID_ROWS;
        }

        public int index() {
            return y * GRID_COLS + x;
        }

        public static Cell fromIndex(int index) {
            return new Cell(index % GRID_COLS, index / GRID_COLS);
        }
    }
}
