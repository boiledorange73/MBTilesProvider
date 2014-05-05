package com.gmail.boiledorange73.and4.ut;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;

public class TemporaryFileManager {
    static public final String SUBDIRECTORY_NAME = "tmp";
    static public final int DEFAULT_MAXFILES = 64;

    private int mFiles = -1;
    private int mMaxFiles = TemporaryFileManager.DEFAULT_MAXFILES;
    private String mDirPath = null;

    public TemporaryFileManager(Context context, String dname) {
        File rootdir = context.getFilesDir();
        File subdir = new File(rootdir.getAbsolutePath() + File.separator
                + TemporaryFileManager.SUBDIRECTORY_NAME);
        if (!subdir.exists()) {
            subdir.mkdir();
        }
        File subsubdir = new File(subdir.getAbsoluteFile() + File.separator + dname);
        if( !subsubdir.exists() ) {
            subsubdir.mkdir();
        }
        this.mDirPath = subsubdir.getAbsolutePath() + File.separator;
        this.clearFiles();
    }

    private void clearFiles() {
        File subdir = new File(this.mDirPath);
        String[] fnames = subdir.list();
        if (fnames != null) {
            for (String fname : fnames) {
                if (!".".equals(fnames) && !"..".equals(fname)) {
                    File f = new File(this.mDirPath + fname);
                    f.delete();
                }
            }
        }
        this.mFiles = 0;
    }

    public void clear() {
        synchronized (this) {
            this.clearFiles();
        }
    }

    public String add(byte[] content) {
        synchronized (this) {
            int newnumber = (this.mFiles % this.mMaxFiles) + 1;
            File f = new File(this.mDirPath + String.valueOf(newnumber));
            if (f.exists()) {
                f.delete();
            }
            try {
                FileOutputStream os = new FileOutputStream(f);
                os.write(content);
                os.close();
                this.mFiles = newnumber;
                return f.getAbsolutePath();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
