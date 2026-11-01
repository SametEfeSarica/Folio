package com.example.foliodimmer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReaderToolbarLayoutTest {
    @Test
    public void sliderHasEqualGapsFromPreviousAndNextButtons() {
        float width = 1360;

        float leftGap = ReaderToolbarLayout.trackStart()
                - ReaderToolbarLayout.previousButtonRight();
        float rightGap = ReaderToolbarLayout.nextButtonLeft(width)
                - ReaderToolbarLayout.trackEnd(width);

        assertEquals(leftGap, rightGap, 0.01f);
    }

    @Test
    public void sliderProgressUsesTheVisibleTrackBounds() {
        float width = 1360;

        assertEquals(0f, ReaderToolbarLayout.progressAt(84, width), 0.001f);
        assertEquals(1f, ReaderToolbarLayout.progressAt(width - 146, width), 0.001f);
    }
}
