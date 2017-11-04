/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ankipro.anki;

import android.content.SharedPreferences;

import com.ankipro.libanki.Utils;
import com.x3wiser.anki.AnkiProApp;
import com.x3wiser.anki.BackupManager;
import com.x3wiser.anki.CollectionHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UnknownFormatConversionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

public class BackupAnkiProManager extends BackupManager{

    private static boolean mUseBackups = true;

    /* Prevent class from being instantiated */
    private BackupAnkiProManager() {
        super();
    }


    public static boolean isActivated() {
        return mUseBackups;
    }

    public static boolean performBackupInBackground(final String colPath, int interval, boolean force) {
        SharedPreferences prefs = AnkiProApp.getSharedPrefs(AnkiProApp.getInstance().getBaseContext());
        if (prefs.getInt("backupMax", 8) == 0 && !force) {
            Timber.w("backups are disabled");
            return false;
        }
        final File colFile = new File(colPath);
        File[] deckBackups = getBackups(colFile);
        int len = deckBackups.length;
        if (len > 0 && deckBackups[len - 1].lastModified() == colFile.lastModified()) {
            Timber.d("performBackup: No backup necessary due to no collection changes");
            return false;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US);
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());

        // Abort backup if one was already made less than 5 hours ago
        Date lastBackupDate = null;
        while (lastBackupDate == null && len > 0) {
            try {
                len--;
                lastBackupDate = df.parse(deckBackups[len].getName().replaceAll(
                        "^.*-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}).ankipro$", "$1"));
            } catch (ParseException e) {
                lastBackupDate = null;
            }
        }
        if (lastBackupDate != null && lastBackupDate.getTime() + interval * 3600000L > Utils.intNow(1000) && !force) {
            Timber.d("performBackup: No backup created. Last backup younger than 5 hours");
            return false;
        }

        String backupFilename;
        try {
            backupFilename = String.format(Utils.ENGLISH_LOCALE, colFile.getName().replace(".anki2", "")
                    + "-%s.ankipro", df.format(cal.getTime()));
        } catch (UnknownFormatConversionException e) {
            Timber.e(e, "performBackup: error on creating backup filename");
            return false;
        }

        // Abort backup if destination already exists (extremely unlikely)
        final File backupFile = new File(getBackupDirectory(colFile.getParentFile()), backupFilename);
        if (backupFile.exists()) {
            Timber.d("performBackup: No new backup created. File already exists");
            return false;
        }

        // Abort backup if not enough free space
        if (getFreeDiscSpace(colFile) < colFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Timber.e("performBackup: Not enough space on sd card to backup.");
            prefs.edit().putBoolean("noSpaceLeft", true).commit();
            return false;
        }

        // Don't bother trying to do backup if the collection is too small to be valid
        if (colFile.length() < MIN_BACKUP_COL_SIZE) {
            Timber.d("performBackup: No backup created as the collection is too small to be valid");
            return false;
        }


        // TODO: Probably not a good idea to do the backup while the collection is open
        if (CollectionHelper.getInstance().colIsOpen()) {
            Timber.w("Collection is already open during backup... we probably shouldn't be doing this");
        }
        Timber.i("Launching new thread to backup %s to %s", colPath, backupFile.getPath());

        // Backup collection as apkg in new thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                // Save collection file as zip archive
                int BUFFER_SIZE = 1024;
                byte[] buf = new byte[BUFFER_SIZE];
                try {
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(colPath), BUFFER_SIZE);
                    ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(backupFile)));
                    ZipEntry ze = new ZipEntry("collection.ankicfc");
                    zos.putNextEntry(ze);
                    int len;
                    while ((len = bis.read(buf, 0, BUFFER_SIZE)) != -1) {
                        zos.write(buf, 0, len);
                    }
                    zos.close();
                    bis.close();
                    // Delete old backup files if needed
                    SharedPreferences prefs = AnkiProApp.getSharedPrefs(AnkiProApp.getInstance().getBaseContext());
                    deleteDeckBackups(colPath, prefs.getInt("backupMax", 8));
                    // set timestamp of file in order to avoid creating a new backup unless its changed
                    backupFile.setLastModified(colFile.lastModified());
                    Timber.i("Backup created succesfully");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        return true;
    }


    public static File[] getBackups(File colFile) {
        File[] files = getBackupDirectory(colFile.getParentFile()).listFiles();
        if (files == null) {
            files = new File[0];
        }
        ArrayList<File> deckBackups = new ArrayList<>();
        for (File aktFile : files) {
            if (aktFile.getName().replaceAll("^(.*)-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}.ankipro$", "$1.ankipro")
                    .equals(colFile.getName().replace(".anki2",".ankipro"))) {
                deckBackups.add(aktFile);
            }
        }
        Collections.sort(deckBackups);
        File[] fileList = new File[deckBackups.size()];
        deckBackups.toArray(fileList);
        return fileList;
    }
}
