package com.example.foliodimmer;

final class ReaderToolbarLayout {
    private static final float TRACK_START = 84f;
    private static final float PREVIOUS_BUTTON_RIGHT = 62f;
    private static final float NEXT_BUTTON_INSET = 124f;
    private static final float TRACK_TO_BUTTON_GAP = 22f;

    private ReaderToolbarLayout() { }

    static float previousButtonRight() {
        return PREVIOUS_BUTTON_RIGHT;
    }

    static float trackStart() {
        return TRACK_START;
    }

    static float trackEnd(float width) {
        return nextButtonLeft(width) - TRACK_TO_BUTTON_GAP;
    }

    static float nextButtonLeft(float width) {
        return width - NEXT_BUTTON_INSET;
    }

    static float progressAt(float x, float width) {
        float span = Math.max(1f, trackEnd(width) - TRACK_START);
        return Math.max(0f, Math.min(1f, (x - TRACK_START) / span));
    }
}
