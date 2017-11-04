package com.x3wiser.libanki.importer;

import com.x3wiser.libanki.Collection;

/**
 * This class is a stub. Nothing is implemented yet.
 */
public class NoteImporter extends Importer {
    public NoteImporter(Collection col, String file) {
        super(col, file);
    }

    @Override
    public void run() {

    }

    public int getTotal() {
        return mTotal;
    }
}
