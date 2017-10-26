/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ankipro.libanki;

import android.content.Context;

import com.ichi2.anki.CollectionHelper;



import java.io.File;
import java.io.FileFilter;

import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;


public class Utils extends com.ichi2.libanki.Utils{

    /* Prevent class from being instantiated */
    protected Utils() {
        super();
    }

    /** Returns a list of apkg-files. */
    public static List<File> getImportableDecks(Context context) {
        String deckPath = CollectionHelper.getCurrentAnkiDroidDirectory(context);
        File dir = new File(deckPath);
        int deckCount = 0;
        File[] deckList = null;
        if (dir.exists() && dir.isDirectory()) {
            deckList = dir.listFiles(new FileFilter(){
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile() && (pathname.getName().endsWith(".ankipro")|| pathname.getName().endsWith(".apkg"));
                }
            });
            deckCount = deckList.length;
        }
        List<File> decks = new ArrayList<>();
        decks.addAll(Arrays.asList(deckList).subList(0, deckCount));
        return decks;
    }
}
