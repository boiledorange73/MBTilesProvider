package com.gmail.boiledorange73.and4.ut.mbtiles;

import java.io.FileNotFoundException;
import java.util.regex.Pattern;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

public class MBTilesOperator {
    /** Image format type */
    private enum ImageFormat {
        /** Means not set. */
        none,
        /* Means PNG. */
        png,
        /* Means JPEG. */
        jpg,
        /* Means unknown format. */
        unknown
    }

    private static final Pattern PTN_INTEGER = Pattern.compile("^-?[0-9]+$");

    private String mDbPath;
    private ImageFormat mImageFormat = ImageFormat.none;
    private SQLiteDatabase mDb;

    /**
     * Constructor.
     * 
     * @param dbPath
     *            Path to MBTiles file.
     * @param readonly
     *            Whether opens with read-only mode.
     * @throws SQLiteException
     *             Thrown when fails to open the file.
     */
    public MBTilesOperator(String dbPath, boolean readonly)
            throws SQLiteException {
        this.mDbPath = dbPath;
        this.open(readonly);
    }

    /**
     * Gets the metadata.
     * 
     * @param name
     *            Name of one of metadata.
     * @return Specified metadata value.
     * @throws FileNotFoundException
     */
    public String getMeta(String name) throws FileNotFoundException {
        if (this.mDb == null) {
            throw new FileNotFoundException();
        }
        // SELECT value FROM metadata WHERE name=?
        Cursor cur = this.mDb.query("metadata", new String[] { "value" },
                "name=?", new String[] { name }, null, null, null);
        if (!(cur.getCount() > 0)) {
            throw new FileNotFoundException();
        }
        cur.moveToFirst();
        return cur.getString(0);
    }

    /**
     * Gets the tile.
     * 
     * @param sz
     *            String z. Must match /^-?[0-9]+$/
     * @param sx
     *            String x. Must match /^-?[0-9]+$/
     * @param sy
     *            String y. Must match /^-?[0-9]+$/
     * @return The content.
     * @throws FileNotFoundException
     *             Thrown if specified record is not found.
     */
    public byte[] getTile(String sz, String sx, String sy)
            throws FileNotFoundException {
        if (PTN_INTEGER.matcher(sz).find() == false
                || PTN_INTEGER.matcher(sx).find() == false
                || PTN_INTEGER.matcher(sy).find() == false) {
            throw new FileNotFoundException();
        }
        // SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND
        // tile_wor=?
        Cursor cur = this.mDb.query("tiles", new String[] { "tile_data" },
                "zoom_level=? AND tile_column=? AND tile_row=?", new String[] {
                        sz, sx, sy }, null, null, null);
        if (!(cur.getCount() > 0)) {
            throw new FileNotFoundException();
        }
        cur.moveToFirst();
        return cur.getBlob(0);
    }

    /**
     * Closes MBTiles file.
     */
    public void close() {
        if (this.mDb != null && this.mDb.isOpen()) {
            this.mDb.close();
        }
    }

    /**
     * Opens MBTiles file.
     * 
     * @param readonly
     *            Whether opens it read-only mode.
     * @throws SQLiteException
     */
    public void open(boolean readonly) throws SQLiteException {
        if (this.mDb != null && this.mDb.isOpen()) {
            return;
        }
        if (this.mDbPath == null) {
            // has no path information.
            throw new SQLiteException();
        }

        int openFlag = readonly ? SQLiteDatabase.OPEN_READONLY
                : SQLiteDatabase.OPEN_READWRITE;
        this.mDb = SQLiteDatabase.openDatabase(this.mDbPath, null, openFlag);
        // Gets image format if not yet gotten.
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
