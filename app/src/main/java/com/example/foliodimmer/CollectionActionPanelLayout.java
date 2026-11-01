package com.example.foliodimmer;

final class CollectionActionPanelLayout {
    static final class Position {
        final float left;
        final float top;
        final float width;
        final float height;

        Position(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }

    private CollectionActionPanelLayout() { }

    static Position calculate(float anchorX, float anchorY, float screenWidth,
                              float screenHeight, boolean expanded) {
        float width = Math.max(0, Math.min(260, screenWidth - 32));
        float height = Math.min(expanded ? 266 : 220, Math.max(0, screenHeight - 32));
        float maxLeft = Math.max(16, screenWidth - 16 - width);
        float maxTop = Math.max(16, screenHeight - 16 - height);
        float left = clamp(anchorX - width + 28, 16, maxLeft);
        float top = clamp(anchorY + 12, 16, maxTop);
        return new Position(left, top, width, height);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
