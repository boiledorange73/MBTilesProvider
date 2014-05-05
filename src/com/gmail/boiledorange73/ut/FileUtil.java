package com.gmail.boiledorange73.ut;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Stack;

public final class FileUtil {
    public static String calculatePath(String relPath, File rootDir) {
        String rootPath = rootDir.getAbsolutePath();
        String rootPathWithSeparator = rootPath + File.separator;
        File f = (new File(rootPathWithSeparator + relPath)).getAbsoluteFile();
        String fpath = f.getAbsolutePath();
        // left matches
        int rootPathLen = rootPathWithSeparator.length();
        if( fpath.substring(0, rootPathLen).equals(rootPathWithSeparator) ) {
            return fpath;
        }
        // hmm... fullpath is not under rootDir.
        Stack<String> stack = new Stack<String>();
        String p = relPath;
        int separatorLen = File.separator.length();
        while( p.length() > 0 ) {
            int ix = p.indexOf(File.separator);
            if( ix == 0 ) {
                // separator exists on left edge.
                // ignores.
                p = p.substring(ix+separatorLen);
            }
            else if( ix > 0 ){
                // subdirname + separator.
                String subdirname = p.substring(0, ix);
                if( "..".equals(subdirname) ) {
                    // moves to upper
                    stack.pop();
                }
                else if( ".".equals(subdirname) ) {
                    // ignores.
                }
                else {
                    stack.push(subdirname);
                }
                p = p.substring(ix+separatorLen);
            }
            else {
                // no more separator. This is filename.
                stack.push(p);
                p = "";
            }
        }
        String ret = rootPath;
        for( String s: stack ) {
            ret = ret + File.separator + s;
        }
        return ret;
    }

    public static String calculateFileNameByUrl(String urlText) {
        URL url;
        try {
            url = new URL(urlText);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        String path = url.getPath();
        if( path == null ) {
            return null;
        }
        String[] pathSegments = path.split("/");
        if( pathSegments == null || !(pathSegments.length > 0) ) {
            return null;
        }
        String ret = pathSegments[pathSegments.length-1];
        if( ret == null || !(ret.length() > 0 ) ) {
            return null;
        }
        return ret;
    }
    
    public static String calculateBytesText(long bytes) {
        String[] prefixs = { "", "Ki", "Mi", "Gi", "Ti", "Pi", "Ei" };
        int prefixIndex = 0;
        long prefix_bytes = 1L;
        while ((bytes/prefix_bytes) > 1024L && prefixIndex < prefixs.length) {
            prefixIndex++;
            prefix_bytes = prefix_bytes * 1024L;
        }
        //
        double mantissa = ((double)bytes / (double)prefix_bytes);
        double factor = Math.pow(10, 2.0 - Math.floor(Math.log10(mantissa)));
        double v = Math.round(mantissa * factor) / factor;
        return String.valueOf(v) + prefixs[prefixIndex] + "B";
    }
}
