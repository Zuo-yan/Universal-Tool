package org.gwfx.universaltool.client;

/** Shared render and hit-test coordinates, measured from the wheel's center. */
record SkillWheelLayout(int centerX, int centerY, float scale, int count) {
    static final int CARD_WIDTH = 86;
    static final int CARD_HEIGHT = 28;
    static final int CONTENT_WIDTH = 368;
    private static final int CONTENT_HEIGHT = 228;
    private static final int HOTBAR_CLEARANCE = 24;
    private static final int RADIUS_X = 132;
    private static final int RADIUS_Y = 67;
    private static final int DEAD_ZONE = 18;
    private static final double FULL_TURN = 2 * Math.PI;
    private static final double SECTOR_MARGIN = Math.toRadians(4);

    static SkillWheelLayout fit(int width, int height, int count) {
        float widthScale = width / (float) CONTENT_WIDTH;
        float heightScale = Math.max(1, height - 2 * HOTBAR_CLEARANCE) / (float) CONTENT_HEIGHT;
        float scale = Math.min(1.0F, Math.min(widthScale, heightScale));
        return new SkillWheelLayout(width / 2, height / 2, scale, count);
    }

    int slotX(int index) {
        return (int) Math.round(RADIUS_X * Math.sin(index * FULL_TURN / count));
    }

    int slotY(int index) {
        return (int) Math.round(-RADIUS_Y * Math.cos(index * FULL_TURN / count));
    }

    int cardAt(double mouseX, double mouseY) {
        double x = (mouseX - centerX) / scale;
        double y = (mouseY - centerY) / scale;
        for (int i = 0; i < count; i++) {
            if (Math.abs(x - slotX(i)) <= CARD_WIDTH / 2.0 && Math.abs(y - slotY(i)) <= CARD_HEIGHT / 2.0) {
                return i;
            }
        }
        return -1;
    }

    int pointedSlot(double mouseX, double mouseY, int currentSlot) {
        int card = cardAt(mouseX, mouseY);
        if (card >= 0) return card;

        double x = (mouseX - centerX) / scale;
        double y = (mouseY - centerY) / scale;
        if (x * x + y * y <= DEAD_ZONE * DEAD_ZONE) return currentSlot;

        double angle = Math.atan2(x / RADIUS_X, -y / RADIUS_Y);
        double sector = FULL_TURN / count;
        double fromCurrent = Math.IEEEremainder(angle - currentSlot * sector, FULL_TURN);
        if (Math.abs(fromCurrent) <= sector / 2 + SECTOR_MARGIN) return currentSlot;
        return Math.floorMod((int) Math.round(angle / sector), count);
    }
}
