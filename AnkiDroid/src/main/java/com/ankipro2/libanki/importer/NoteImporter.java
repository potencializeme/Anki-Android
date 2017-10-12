package com.ankipro2.libanki.importer;

import com.ankipro2.libanki.Collection;

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
