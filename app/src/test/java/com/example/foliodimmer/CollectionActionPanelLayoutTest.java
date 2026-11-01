package com.example.foliodimmer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CollectionActionPanelLayoutTest {
    @Test
    public void panelOpensNearTheSelectedCollectionMenu() {
        CollectionActionPanelLayout.Position position =
                CollectionActionPanelLayout.calculate(420, 280, 1340, 529, false);

        assertTrue(position.left < 420);
        assertTrue(position.left + position.width >= 420);
        assertTrue(position.top >= 280);
    }

    @Test
    public void panelStaysInsideSmallLandscapeScreens() {
        CollectionActionPanelLayout.Position position =
                CollectionActionPanelLayout.calculate(180, 290, 600, 400, true);

        assertTrue(position.left >= 16);
        assertTrue(position.top >= 16);
        assertTrue(position.left + position.width <= 584);
        assertTrue(position.top + position.height <= 384);
    }

    @Test
    public void panelUsesAvailableWidthOnNarrowScreens() {
        CollectionActionPanelLayout.Position position =
                CollectionActionPanelLayout.calculate(110, 180, 250, 450, false);

        assertEquals(218f, position.width, 0.01f);
        assertTrue(position.left >= 16);
        assertTrue(position.left + position.width <= 234);
    }
}
