package com.x3wiser.anki.tests;

import android.test.AndroidTestCase;

import com.x3wiser.anki.CollectionHelper;

import java.io.IOException;

/**
 * This test case verifies that the directory initialization works even if the app is not yet fully initialized.
 */
public class CollectionTest extends AndroidTestCase{
    public void testOpenCollection() throws IOException {
        assertNotNull("Collection could not be opened", CollectionHelper.getInstance().getCol(getContext()));
    }
}