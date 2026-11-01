package com.example.foliodimmer;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CollectionStorageTest {
    @Test
    public void trimsNamesAndTreatsBlankAsMissing() {
        assertEquals("Reading", CollectionStorage.normalizeName("  Reading  "));
        assertNull(CollectionStorage.normalizeName("  \t"));
    }

    @Test
    public void rejectsCaseInsensitiveDuplicatesButAllowsCurrentNameDuringRename() {
        HashSet<String> names = new HashSet<>(Arrays.asList("Fiction", "Study"));

        assertTrue(CollectionStorage.isDuplicateName(names, " FICTION ", null));
        assertFalse(CollectionStorage.isDuplicateName(names, " fiction ", "Fiction"));
        assertFalse(CollectionStorage.isDuplicateName(names, "New shelf", "Fiction"));
    }

    @Test
    public void encodedPreferenceKeysDoNotCollideForJavaHashCollisions() {
        assertEquals("FB".hashCode(), "Ea".hashCode());
        assertFalse(CollectionStorage.keyPart("FB").equals(CollectionStorage.keyPart("Ea")));
    }

    @Test
    public void encodedPreferenceKeysAreStableForUnicode() {
        assertEquals(CollectionStorage.keyPart("雪と月"), CollectionStorage.keyPart("雪と月"));
        assertFalse(CollectionStorage.keyPart("é").equals(CollectionStorage.keyPart("e")));
    }
}
