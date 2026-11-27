package org.gwfx.universaltool.snake;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.gwfx.universaltool.snake.SnakeBoard.*;

/** Server-side simulation. Only the game clock advances the board or earns points. */
final class SnakeGame {
    private final RandomGenerator random;
    private final Deque<Cell> snake = new ArrayDeque<>();
    private final Deque<Dir> turns = new ArrayDeque<>(2);
    private State state;
    private Dir direction;
    private Cell food;
    private Cell bonusFood;
    private int score;
    private int bestScore;
    private int bonusTicks;
    private int applesEaten;
    private int stepTicks;

    SnakeGame(RandomGenerator random, int bestScore) {
        this.random = random;
        this.bestScore = bestScore;
        reset(State.READY);
    }

    int score() {
        return score;
    }

    int bestScore() {
        return bestScore;
    }

    SnakeBoard snapshot() {
        return new SnakeBoard(state, direction, List.copyOf(snake), food, bonusFood, bonusTicks, score, bestScore);
    }

    void open() {
        if (state == State.GAME_OVER) reset(State.READY);
    }

    boolean pause() {
        if (state != State.PLAYING) return false;
        state = State.PAUSED;
        return true;
    }

    void toggle() {
        switch (state) {
            case READY, GAME_OVER -> reset(State.PLAYING);
            case PLAYING -> pause();
            case PAUSED -> state = State.PLAYING;
        }
    }

    void restart() {
        reset(State.PLAYING);
    }

    boolean turn(Dir next) {
        boolean started = state == State.READY;
        if (started) state = State.PLAYING;
        if (state == State.PLAYING && turns.size() < 2) {
            Dir last = turns.isEmpty() ? direction : turns.getLast();
            if (next != last && !next.isOpposite(last)) turns.addLast(next);
        }
        return started;
    }

    boolean tick() {
        if (state != State.PLAYING) return false;
        boolean bonusExpired = bonusFood != null && --bonusTicks == 0;
        if (bonusExpired) {
            bonusFood = null;
            if (food == null) food = randomFreeCell();
        }
        if (++stepTicks < Math.max(2, 5 - applesEaten / 4)) return bonusExpired;
        stepTicks = 0;
        move();
        return true;
    }

    private void move() {
        if (!turns.isEmpty()) direction = turns.removeFirst();
        Cell next = snake.getFirst().step(direction);
        boolean ateFood = next.equals(food);
        boolean ateBonus = next.equals(bonusFood);
        boolean grows = ateFood || ateBonus;
        if (!next.onBoard() || (snake.contains(next) && (grows || !next.equals(snake.getLast())))) {
            state = State.GAME_OVER;
            return;
        }
        if (!grows) snake.removeLast();
        snake.addFirst(next);

        if (ateFood) {
            ++applesEaten;
            score += 10;
            food = randomFreeCell();
            if (bonusFood == null && applesEaten % 5 == 0) {
                bonusFood = randomFreeCell();
                bonusTicks = bonusFood != null ? BONUS_DURATION_TICKS : 0;
            }
        } else if (ateBonus) {
            score += 30;
            bonusFood = null;
            bonusTicks = 0;
            // A full board except for the last bonus cell can temporarily have no normal food.
            if (food == null) food = randomFreeCell();
        }
        bestScore = Math.max(bestScore, score);
        if (snake.size() == CELLS) {
            state = State.GAME_OVER;
        }
    }

    private void reset(State next) {
        int x = GRID_COLS / 2 - 1;
        int y = GRID_ROWS / 2;
        snake.clear();
        snake.addLast(new Cell(x, y));
        snake.addLast(new Cell(x - 1, y));
        snake.addLast(new Cell(x - 2, y));
        turns.clear();
        direction = Dir.RIGHT;
        state = next;
        food = null;
        bonusFood = null;
        food = randomFreeCell();
        score = 0;
        applesEaten = 0;
        bonusTicks = 0;
        stepTicks = 0;
    }

    private Cell randomFreeCell() {
        boolean[] occupied = new boolean[CELLS];
        for (Cell segment : snake) occupied[segment.index()] = true;
        if (food != null) occupied[food.index()] = true;
        if (bonusFood != null) occupied[bonusFood.index()] = true;
        int free = 0;
        for (boolean used : occupied) {
            if (!used) free++;
        }
        if (free == 0) return null;
        int rank = random.nextInt(free);
        for (int index = 0; index < CELLS; index++) {
            if (!occupied[index] && rank-- == 0) return Cell.fromIndex(index);
        }
        throw new IllegalStateException("Could not select a free Snake cell");
    }
}
