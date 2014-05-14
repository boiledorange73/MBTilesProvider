package com.gmail.boiledorange73.and4.prv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.gmail.boiledorange73.and4.ut.TemporaryFileManager;
import com.gmail.boiledorange73.and4.ut.mbtiles.MBTilesOperator;
import com.gmail.boiledorange73.ut.FileUtil;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

/**
 * MBTiles provider. Tiles can be got by
 * "content://(authority)/(encoded mbtiles path)/(x)/(y)/(z).(ext)"
 * 
 * Root directory of mbtiles is "
 * {@link android.os.Environment#getExternalStorageDirectory()}/
 * {@link #getRelativeDirectoryPath()}".
 * 
 * To read metadata, "cotent://(authority)/(encoded mbtiles path)/metadata" for
 * all, or "cotent://(authority)/(encoded mbtiles path)/(metadata name)" for
 * each metadata.
 * 
 * If you want to got metadata with JSON or JSONP, open
 * "cotent://(authority)/(encoded mbtiles path)/metadata?json" or
 * "cotent://(authority)/(encoded mbtiles path)/metadata?callback=(function name)"
 * .
 * 
 * Function name must match /^[\\$_a-zA-Z][\\$_0-9a-zA-Z]*$/
 * 
 * @author yellow
 * 
 */
public class MBTilesProvider extends ContentProvider {
    /** Function name pattern */
    private static final Pattern PTN_FUNCTIONNAME = Pattern
            .compile("^[\\$_a-zA-Z][\\$_0-9a-zA-Z]*$");

    /** White list for metadata name. */
    private static final String[] PERMISSIVE_METADATA_NAMES = { "name", "type",
            "version", "description", "format", "bounds", "attribution" };

    private HashMap<String, MBTilesOperator> mMBTiles = new HashMap<String, MBTilesOperator>();
    private TemporaryFileManager mTFM = null;

    // ----------------
    // Private methods
    // ----------------
    /**
     * Creates temporary file and open it and create ParcelFileDescriptor.
     * 
     * @param content
     *            Cotent binary.
     * @return ParcelFileDescriptor instane.
     * @throws FileNotFoundException
     *             Thrown if temporary file cannot be opened.
     */
    private ParcelFileDescriptor createParcelFileDescriptor(byte[] content)
            throws FileNotFoundException {
        String path = this.mTFM.add(content);
        return ParcelFileDescriptor.open(new File(path),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Closes all MBTilesOperator instances.
     */
    private void closeAllMBTilesOperators() {
        synchronized (this) {
            for (String db : this.mMBTiles.keySet()) {
                MBTilesOperator op = this.mMBTiles.get(db);
                if (op != null) {
                    op.close();
                }
            }
            this.mMBTiles.clear();
        }
    }

    /**
     * Closes one MBTilesOperator which handles specified database.
     * 
     * @param db
     *            Database name (encoded filename)
     * @throws FileNotFoundException
     *             Thrown when failed to close.
     */
    private void closeMBTilesOperator(String db) throws FileNotFoundException {
        synchronized (this) {
            if (!this.mMBTiles.containsKey(db)) {
                throw new FileNotFoundException("MBTiles file not found.");
            }
            MBTilesOperator op = this.mMBTiles.get(db);
            if (op != null) {
                op.close();
            }
            this.mMBTiles.remove(db);
        }
    }

    /**
     * Gets MBTilesOperator instane. If not created, will create it.
     * 
     * @param db
     *            Database name (encoded filename)
     * @param readonly
     *            Opens the db with READONLY mode if db is not opened.
     * @return MBTilesOperator.
     */
    private MBTilesOperator getMBTilesOperator(String db, boolean readonly) {
        synchronized (this) {
            if (this.mMBTiles.containsKey(db)) {
                return this.mMBTiles.get(db);
            } else {
                String dbpath;
                try {
                    dbpath = URLDecoder.decode(db, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                }
                String relPath = this.getRelativeDirectoryPath();
                if (relPath != null) {
                    dbpath = relPath + File.separator + dbpath;
                }
                String path = FileUtil.calculatePath(dbpath,
                        Environment.getExternalStorageDirectory());
                MBTilesOperator op = null;
                if (path != null) {
                    try {
                        op = new MBTilesOperator(path, readonly);
                    } catch (SQLiteException e) {
                        e.printStackTrace();
                    }
                }
                this.mMBTiles.put(db, op);
                return op;
            }
        }
    }

    /**
     * Returns relative directory path on
     * {@link android.os.Enironment#getExternalStorageDirectory()}.
     * 
     * @return Relative directory path. Returning null means that medium
     *         relative path does not exist.
     */
    protected String getRelativeDirectoryPath() {
        return null;
    }

    /**
     * Opens the file.
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        synchronized (this) {
            if (this.mTFM == null) {
                this.mTFM = new TemporaryFileManager(this.getContext(),
                        "MBTilesProvider");
            }
            // removes empty segments
            List<String> rowPathSegments = uri.getPathSegments();
            ArrayList<String> pathSegments = new ArrayList<String>();
            if (rowPathSegments != null) {
                for (String segment : rowPathSegments) {
                    if (segment != null && segment.length() > 0) {
                        pathSegments.add(segment);
                    }
                }
            }
            MBTilesOperator op;
            switch (pathSegments.size()) {
            case 4:
                // /db/x/y/z.ext
                String sz = pathSegments.get(1);
                String sx = pathSegments.get(2);
                String sye = pathSegments.get(3);
                String sy;
                if (sye.indexOf('.') >= 0) {
                    sy = sye.substring(0, sye.indexOf('.'));
                } else {
                    sy = sye;
                }
                op = this.getMBTilesOperator(pathSegments.get(0), true);
                if (op == null) {
                    throw new FileNotFoundException("MBTiles file not found.");
                }
                return this.createParcelFileDescriptor(op.getTile(sz, sx, sy));
            case 2:
                // /(db)/metadata | (metaname)
                if ("metadata".equals(pathSegments.get(1))) {
                    op = this.getMBTilesOperator(pathSegments.get(0), true);
                    if (op == null) {
                        throw new FileNotFoundException();
                    }
                    HashMap<String, String> meta = new HashMap<String, String>();
                    for (String pmn : MBTilesProvider.PERMISSIVE_METADATA_NAMES) {
                        try {
                            String metaValue = op.getMeta(pmn);
                            if (metaValue != null) {
                                meta.put(pmn, metaValue);
                            }
                        } catch (FileNotFoundException e) {
                            // DOES NOTHING
                        }
                    }
                    if (meta.isEmpty()) {
                        throw new FileNotFoundException("No metadata found.");
                    }
                    String content = null;
                    if (uri.getQueryParameter("json") != null) {
                        content = this.createMetadataJson(meta);
                    } else if (uri.getQueryParameter("callback") != null) {
                        content = this.createMetadataJson(meta);
                        String callback = uri.getQueryParameter("callback");
                        if (MBTilesProvider.PTN_FUNCTIONNAME.matcher(callback)
                                .find()) {
                            content = callback + "(" + content + ");";
                        }
                    } else {
                        // simple value
                        content = this.createMetadataText(meta);
                    }
                    try {
                        return this.createParcelFileDescriptor(content
                                .getBytes("UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new FileNotFoundException(e.getMessage());
                    }
                } else {
                    // (db)/(metaname)
                    op = this.getMBTilesOperator(pathSegments.get(0), true);
                    if (op == null) {
                        throw new FileNotFoundException();
                    }
                    String metaValue = null;
                    for (String pnm : MBTilesProvider.PERMISSIVE_METADATA_NAMES) {
                        if (pnm.equals(pathSegments.get(1))) {
                            metaValue = op.getMeta(pathSegments.get(1));
                            break;
                        }
                    }
                    if (metaValue == null) {
                        throw new FileNotFoundException(
                                "Speified metadata not found.");
                    }
                    String content = null;
                    if (uri.getQueryParameter("json") != null) {
                        content = this.createSingleMetadataJson(
                                pathSegments.get(1), metaValue);
                    } else if (uri.getQueryParameter("callback") != null) {
                        content = this.createSingleMetadataJson(
                                pathSegments.get(1), metaValue);
                        String callback = uri.getQueryParameter("callback");
                        if (MBTilesProvider.PTN_FUNCTIONNAME.matcher(callback)
                                .find()) {
                            content = callback + "(" + content + ");";
                        }
                    } else {
                        // simple value
                        content = metaValue;
                    }
                    try {
                        return this.createParcelFileDescriptor(content
                                .getBytes("UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new FileNotFoundException(e.getMessage());
                    }
                }
            case 1:
                // (db)
                String c1 = uri.getQueryParameter("c");
                if ("close".equals(c1)) {
                    this.closeMBTilesOperator(pathSegments.get(0));
                }
                return this.createParcelFileDescriptor(new byte[] {});
            case 0:
                String c0 = uri.getQueryParameter("c");
                if ("close".equals(c0)) {
                    this.closeAllMBTilesOperators();
                }
                return this.createParcelFileDescriptor(new byte[] {});
            }
            throw new FileNotFoundException("Invalid path");
        }
    }

    // --------
    // subs
    // --------
    /**
     * Creates metadata JSON from HashMap.
     * 
     * @param meta
     *            HashMap containing complex of name and value.
     * @return JSON text.
     */
    private String createMetadataJson(HashMap<String, String> meta) {
        JSONObject ret = new JSONObject();
        for (String k : meta.keySet()) {
            String v = meta.get(k);
            try {
                ret.put(k, v);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ret.toString();
    }

    /**
     * Creates one metadata JSON.
     * 
     * @param metaName
     *            Name of a metadata.
     * @param metaValue
     *            Value of metadata.
     * @return JSON text.
     */
    private String createSingleMetadataJson(String metaName, String metaValue) {
        JSONObject ret = new JSONObject();
        try {
            ret.put(metaName, metaValue);
            return ret.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "{\"+metaName+\":null}";
    }

    /**
     * Creates metadata text from HashMap. For each line of metadata is
     * represented with "(name)\t(value)"; where "\t" is tab character. Name
     * must not contain any space. Tab character and Line-Feed character are
     * changed as "\t" and "\n".
     * 
     * @param meta
     *            HashMap containing complex of name and value.
     * @return JSON text.
     */

    private String createMetadataText(HashMap<String, String> meta) {
        String ret = "";
        for (String k : meta.keySet()) {
            String v = meta.get(k);
            ret = ret + k + "\t";
            if (v == null) {
                ret = ret + "\\N";
            }
            for (int n = 0; n < v.length(); n++) {
                char c = v.charAt(n);
                switch (c) {
                case '\t':
                    ret = ret + "\\t";
                    break;
                case '\n':
                    ret = ret + "\\n";
                    break;
                default:
                    ret = ret + c;
                }
            }
            ret = ret + "\n";
        }
        return ret;
    }

    @Override
    public void onLowMemory() {
        this.closeAllMBTilesOperators();
    }

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        // DOES NOTHING
        return 0;
    }

    @Override
    public String getType(Uri arg0) {
        // DOES NOTHING
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // DOES NOTHING
        return null;
    }

    @Override
    public boolean onCreate() {
        // DOES NOTHING
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        // DOES NOTHING
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        // DOES NOTHING
        return 0;
    }

}
