package com.gmail.boiledorange73.and4.ut.mbtiles;

import java.io.FileNotFoundException;
import java.util.regex.Pattern;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.gmail.boiledorange73.and4.prv.MBTilesProvider.ImageFormat;

public class MBTilesOperator {
    private static final Pattern PTN_INTEGER = Pattern.compile("^-?[0-9]+$");

    private String mDbPath;
    private ImageFormat mImageFormat = ImageFormat.none;
    private SQLiteDatabase mDb;

    public String getMeta(String name) throws FileNotFoundException {
        if (this.mDb == null) {
            throw new FileNotFoundException();
        }
        Cursor cur = this.mDb.query("metadata", new String[] { "value" },
                "name=?", new String[] { name }, null, null, null);
        if (!(cur.getCount() > 0)) {
            throw new FileNotFoundException();
        }
        cur.moveToFirst();
        return cur.getString(0);
    }

    public byte[] getTile(String sz, String sx, String sy)
            throws FileNotFoundException {
        if (PTN_INTEGER.matcher(sz).find() == false
                || PTN_INTEGER.matcher(sx).find() == false
                || PTN_INTEGER.matcher(sy).find() == false) {
            throw new FileNotFoundException();
        }
        Cursor cur = this.mDb.query("tiles", new String[] { "tile_data" },
                "zoom_level=? AND tile_column=? AND tile_row=?", new String[] {
                        sz, sx, sy }, null, null, null);
        if (!(cur.getCount() > 0)) {
            throw new FileNotFoundException();
        }
        cur.moveToFirst();
        return cur.getBlob(0);
    }

    public MBTilesOperator(String dbPath) throws SQLiteException {
        this.mDbPath = dbPath;
        this.open();
    }

    public void close() {
        if (this.mDb != null && this.mDb.isOpen()) {
            this.mDb.close();
        }
    }

    public void open() throws SQLiteException {
        if (this.mDb != null && this.mDb.isOpen()) {
            return;
        }
        if (this.mDbPath == null) {
            // has no path information.
            throw new SQLiteException();
        }

        this.mDb = SQLiteDatabase.openDatabase(this.mDbPath, null,
                SQLiteDatabase.OPEN_READONLY);
        if (this.mImageFormat == ImageFormat.none) {
            Cursor cur = this.mDb.query("metadata", new String[] { "value" },
                    "name=?", new String[] { "format" }, null, null, null);
            if (cur.getCount() > 0) {
                cur.moveToFirst();
                String format = cur.getString(0);
                if ("jpg".equals(format)) {
                    this.mImageFormat = ImageFormat.jpg;
                } else if ("png".equals(format)) {
                    this.mImageFormat = ImageFormat.png;
                } else {
                    this.mImageFormat = ImageFormat.unknown;
                }
            }
        }
    }
}
