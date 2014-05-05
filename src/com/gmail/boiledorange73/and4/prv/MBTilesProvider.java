package com.gmail.boiledorange73.and4.prv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
 * "content://(authority)/(encoded mbtiles path)/(x)/(y)/(z).(ext)" Root
 * directory of mbtiles is {@link Environment#getExternalStorageDirectory()}.
 * 
 * @author yellow
 * 
 */
public class MBTilesProvider extends ContentProvider {
    private HashMap<String, MBTilesOperator> mMBTiles = new HashMap<String, MBTilesOperator>();
    private TemporaryFileManager mTFM = null;

    public enum ImageFormat {
        none, unknown, png, jpg,
    }

    private ParcelFileDescriptor createParcelFileDescriptor(byte[] content)
            throws FileNotFoundException {
        String path = this.mTFM.add(content);
        return ParcelFileDescriptor.open(new File(path),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void closeAllMBTilesOperators() {
        synchronized (this) {
            for( String db : this.mMBTiles.keySet() ) {
                MBTilesOperator op = this.mMBTiles.get(db);
                if( op != null ) {
                    op.close();
                }
            }
            this.mMBTiles.clear();
        }
    }


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

    private MBTilesOperator getMBTilesOperator(String db, boolean readonly) {
        synchronized (this) {
            if (this.mMBTiles.containsKey(db)) {
                return this.mMBTiles.get(db);
            } else if (readonly) {
                return null;
            } else {
                String dbpath;
                try {
                    dbpath = URLDecoder.decode(db, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                }
                String path = FileUtil.calculatePath(dbpath,
                        Environment.getExternalStorageDirectory());
                MBTilesOperator op = null;
                if (path != null) {
                    try {
                        op = new MBTilesOperator(path);
                    }
                    catch(SQLiteException e) {
                        e.printStackTrace();
                    }
                }
                this.mMBTiles.put(db, op);
                return op;
            }
        }
    }

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
                op = this.getMBTilesOperator(pathSegments.get(0), false);
                if (op == null) {
                    throw new FileNotFoundException("MBTiles file not found.");
                }
                return this.createParcelFileDescriptor(op.getTile(sz, sx, sy));
            case 2:
                // /(db)/(metaname)
                String metaName = pathSegments.get(1);
                op = this.getMBTilesOperator(pathSegments.get(0), false);
                if (op == null) {
                    throw new FileNotFoundException("MBTiles file not found.");
                }
                if ("version".equals(metaName)) {
                    String metaValue = op.getMeta(metaName);
                    if (metaValue == null) {
                        throw new FileNotFoundException("Invalid path");
                    } else {
                        return this.createParcelFileDescriptor(metaValue
                                .getBytes());
                    }
                }
                break;
            case 1:
                // (db)
                String c1 = uri.getQueryParameter("c");
                if ("close".equals(c1)) {
                    this.closeMBTilesOperator(pathSegments.get(0));
                }
                return this.createParcelFileDescriptor(new byte[]{});
            case 0:
                String c0 = uri.getQueryParameter("c");
                if ("close".equals(c0)) {
                    this.closeAllMBTilesOperators();
                }
                return this.createParcelFileDescriptor(new byte[]{});
            }
            throw new FileNotFoundException("Invalid path");
        }
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
