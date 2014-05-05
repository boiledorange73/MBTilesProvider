package com.gmail.boiledorange73.and4.ut.app;

import java.io.File;

import android.os.Environment;
import android.os.StatFs;

import com.gmail.boiledorange73.ut.FileUtil;

public final class AppInfo {
    public static String calculateStorageStatusText() {
        return AppInfo.calculateStorageStatusText("%1$s / %2$s");
    }

    public static String calculateStorageStatusText(String format) {
        File f = Environment.getExternalStorageDirectory();
        String text = "";
        if (f != null) {
            StatFs statFs = new StatFs(f.getAbsolutePath());
            long blockCount = statFs.getBlockCount();
            long availableBlockCount = statFs.getAvailableBlocks();
            long blockSize = statFs.getBlockSize();
            long totalSize = blockCount * blockSize;
            long availableSize = availableBlockCount * blockSize;
            String totalSizeText = FileUtil.calculateBytesText(totalSize);
            String availableSizeText = FileUtil
                    .calculateBytesText(availableSize);
            text = String.format(format, availableSizeText, totalSizeText);
        }
        return text;
    }
}
