package com.gmail.boiledorange73.and4.ut.dl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;

public class DownloaderService extends IntentService {

    public static final int ST_NONE = -1;
    public static final int ST_DOWNLOADING = 1;
    public static final int ST_FINISHED = 2;
    public static final int ST_CANCEL = 3;
    public static final int ST_ERROR = -1;

    public static final String XKEY_REMOTE = "remote";
    public static final String XKEY_LOCAL = "local";
    public static final String XKEY_TITLE = "title";
    public static final String XKEY_CALLBACK_ACTIVITY_CLASS = "callback_activity_class";
    public static final String XKEY_HTTP_TIMEOUT_MS = "http_timeout_ms";
    public static final String XKEY_SOCKET_TIMEOUT_MS = "socket_timeout_ms";

    public static final String XKEY_STATUS = "status";
    public static final String XKEY_FILESIZE = "filesize";
    public static final String XKEY_PROGRESS = "progress";
    public static final String XKEY_MESSAGE = "message";

    public static final String BROADCAST_ACTION = "com.gmail.boiledorange73.and4.ut.dl.DownloaderService.CHANGE_STATUS";

    private static final String DEFAULT_USERAGENT_NAME = "Downloader";
    private static final String DEFAULT_USERAGENT_VERSION = "0.9";

    private static final int DEFAULT_ID_NOTIFICATION = 1;
    private static final int BUFFERSIZE = 1024 * 1024 * 4;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 10000;
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 10000;

    private long mContentLength = -1;
    private long mProgress = -1;
    private boolean mWorking = false;

    private ResourceBundle mResourceBundle;

    // ----------------
    // Constructors.
    // ----------------
    /**
     * Constructor. Simply passes to the one of the super class.
     * 
     * @param name
     *            Used to name the worker thread, important only for debugging.
     */
    public DownloaderService(String name) {
        super(name);
        // Java resource
        this.mResourceBundle = ResourceBundle
                .getBundle("com.gmail.boiledorange73.and4.ut.dl.messages");
    }

    /**
     * Default constructor. Thread name must be "DownloaderService".
     */
    public DownloaderService() {
        this("DownloaderService");
    }

    // ----------------
    // Public methods.
    // ----------------
    /**
     * Gets notification id which is unique in the application (not system).
     * 
     * @return Notification id.
     */
    public int getNotificationId() {
        return DownloaderService.DEFAULT_ID_NOTIFICATION;
    }

    /**
     * Gets the user-agent text. User-agent is set to
     * "(Application Name)/(Application Version)". If you want to use another
     * user-agent text, override this.
     * 
     * @return User-agent text.
     */
    public String getUserAgent() {
        ApplicationInfo appinfo = this.getApplicationInfo();
        if (appinfo != null && appinfo.name != null) {
            String userAgentName = appinfo.name;
            try {
                String userAgentVersion = this.getPackageManager()
                        .getPackageInfo(this.getPackageName(), 0).versionName;
                return userAgentName + "/" + userAgentVersion;
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                return userAgentName;
            }
        } else {
            return DownloaderService.DEFAULT_USERAGENT_NAME + "/"
                    + DownloaderService.DEFAULT_USERAGENT_VERSION;
        }
    }

    // ----------------
    // Overriding methods.
    // ----------------
    /**
     * Called when activity is destroyed.
     */
    @Override
    public void onDestroy() {
        // The flag indicating now downloading is set to false.
        this.mWorking = false;
        // calls super class.
        super.onDestroy();
    }

    /**
     * Called when this servie is called.
     * 
     * @param intent
     *            The intent.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        String remote = intent.getStringExtra(DownloaderService.XKEY_REMOTE);
        String local = intent.getStringExtra(DownloaderService.XKEY_LOCAL);
        String title = intent.getStringExtra(DownloaderService.XKEY_TITLE);
        int http_timeout_ms = intent.getIntExtra(
                DownloaderService.XKEY_SOCKET_TIMEOUT_MS,
                DownloaderService.DEFAULT_SOCKET_TIMEOUT_MS);
        int socket_timeout_ms = intent.getIntExtra(
                DownloaderService.XKEY_SOCKET_TIMEOUT_MS,
                DownloaderService.DEFAULT_HTTP_TIMEOUT_MS);
        Class<? extends Activity> callbackActivityClass = this
                .calculateCallbackActivityClass(intent);
        this.doDownload(this.getUserAgent(), title, remote, local,
                callbackActivityClass, http_timeout_ms, socket_timeout_ms);
    }

    // ----------------
    // Private methods, which handle notification.
    // ----------------
    /**
     * Shows notification and broadcasts the message.
     * 
     * @param notification_id
     *            Notification ID, which is unique in the appliation (NOT in the
     *            system).
     * @param icon_id
     *            ID of icon for notification.
     * @param title
     *            Title of data file.
     * @param text
     *            The message.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showNotifycation(int notification_id, int icon_id,
            String title, String text,
            Class<? extends Activity> callbackActivityClass) {
        Notification notification = new Notification(icon_id, text,
                System.currentTimeMillis());
        PendingIntent contentIntent = null;
        if (callbackActivityClass != null) {
            // The PendingIntent to launch our activity if the user selects this
            // notification
            contentIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                    callbackActivityClass), 0);
        }
        notification.setLatestEventInfo(this, title, text, contentIntent);
        ((NotificationManager) this
                .getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                notification_id, notification);
    }

    /**
     * Shows notification and broadcasts that download is canceled.
     * 
     * @param title
     *            Title of data file.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showCancel(String title,
            Class<? extends Activity> callbackActivityClass) {
        String text = title
                + " "
                + this.mResourceBundle
                        .getString("DownloaderServiceBase.S_CANCELED");
        this.showNotifycation(this.getNotificationId(),
                android.R.drawable.stat_notify_error, this.mResourceBundle
                        .getString("DownloaderServiceBase.W_CANCEL"), text,
                callbackActivityClass);
        Intent intent = new Intent();
        intent.setAction(DownloaderService.BROADCAST_ACTION);
        intent.putExtra(DownloaderService.XKEY_STATUS,
                DownloaderService.ST_CANCEL);
        intent.putExtra(DownloaderService.XKEY_FILESIZE, this.mContentLength);
        intent.putExtra(DownloaderService.XKEY_PROGRESS, this.mProgress);
        this.sendBroadcast(intent);
    }

    /**
     * Shows notification and broadcasts that an error occurred.
     * 
     * @param text
     *            Error message.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showError(String text,
            Class<? extends Activity> callbackActivityClass) {
        this.showNotifycation(
                this.getNotificationId(),
                android.R.drawable.stat_notify_error,
                this.mResourceBundle.getString("DownloaderServiceBase.W_ERROR"),
                text, callbackActivityClass);
        Intent intent = new Intent();
        intent.setAction(DownloaderService.BROADCAST_ACTION);
        intent.putExtra(DownloaderService.XKEY_STATUS,
                DownloaderService.ST_ERROR);
        intent.putExtra(DownloaderService.XKEY_MESSAGE, text);
        this.sendBroadcast(intent);
    }

    /**
     * Shows notification and broadcasts that a download error occurred. If both
     * of reason1 and reason2 are filled, reason is both of reason and separated
     * with white-space. If both of reason1 and reason2 are null, not reason is
     * printed. This method finally calls {@link #showError(String)}.
     * 
     * @param title
     *            Title of data file.
     * @param reason1
     *            A part of reason.
     * @param reason2
     *            A part of reason.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showDownloadError(String title, String reason1,
            String reason2, Class<? extends Activity> callbackActivityClass) {
        String mess = title
                + " "
                + this.mResourceBundle
                        .getString("DownloaderServiceBase.S_FAILED_DOWNLOAD");
        String reason = null;
        if (reason1 != null) {
            reason = reason1;
        }
        if (reason2 != null) {
            if (reason == null) {
                reason = reason2;
            } else {
                reason = reason + " " + reason2;
            }
        }
        if (reason != null) {
            mess = mess + " (" + reason + ")";
        }
        this.showError(mess, callbackActivityClass);
    }

    /**
     * Shows notification and broadcasts that download is working.
     * 
     * @param title
     *            Title of data file.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showDownloading(String title,
            Class<? extends Activity> callbackActivityClass) {
        String text = title;
        if (this.mContentLength > 0) {
            int percent = (int) ((double) this.mProgress * 100.0 / (double) this.mContentLength);
            text = text + " " + String.valueOf(percent) + "%";
        }
        this.showNotifycation(this.getNotificationId(),
                android.R.drawable.stat_sys_download, this.mResourceBundle
                        .getString("DownloaderServiceBase.W_DOWNLOADING"),
                text, callbackActivityClass);
        Intent intent = new Intent();
        intent.setAction(DownloaderService.BROADCAST_ACTION);
        intent.putExtra(DownloaderService.XKEY_STATUS,
                DownloaderService.ST_DOWNLOADING);
        intent.putExtra(DownloaderService.XKEY_FILESIZE, this.mContentLength);
        intent.putExtra(DownloaderService.XKEY_PROGRESS, this.mProgress);
        this.sendBroadcast(intent);
    }

    /**
     * Shows notification and broadcasts that download is finished.
     * 
     * @param fileName
     *            file name text, not full-path.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     */
    private void showFinished(String fileName,
            Class<? extends Activity> callbackActivityClass) {
        String text = fileName
                + " "
                + this.mResourceBundle
                        .getString("DownloaderServiceBase.S_DOWNLOAD_FINISHED");
        this.showNotifycation(
                this.getNotificationId(),
                android.R.drawable.stat_sys_download_done,
                this.mResourceBundle
                        .getString("DownloaderServiceBase.W_DOWNLOAD_FINISHED"),
                text, callbackActivityClass);
        Intent intent = new Intent();
        intent.setAction(DownloaderService.BROADCAST_ACTION);
        intent.putExtra(DownloaderService.XKEY_STATUS,
                DownloaderService.ST_FINISHED);
        intent.putExtra(DownloaderService.XKEY_FILESIZE, this.mProgress);
        intent.putExtra(DownloaderService.XKEY_PROGRESS, this.mProgress);
        this.sendBroadcast(intent);
    }

    // ----------------
    // Main routine
    // ----------------
    /**
     * Downloads the file.
     * 
     * @param userAgent
     *            The user-agent text which is passed to the server.
     * @param title
     *            Title of data file.
     * @param remoteUriText
     *            Remote URI text.
     * @param local
     *            Local file path.
     * @param callbackActivityClass
     *            Activity which is activated when notification cell is tapped.
     * @param connection_timeout_ms
     *            Connection timeout milliseconds. If this is negative,
     *            connection timeout is not set.
     * @param socket_timeout_ms
     *            Timeout milliseconds between data arrivals.
     */
    private void doDownload(String userAgent, String title,
            String remoteUriText, String local,
            Class<? extends Activity> callbackActivityClass,
            int connection_timeout_ms, int socket_timeout_ms) {
        // init
        this.mWorking = true;
        this.mContentLength = -1;
        this.mProgress = -1;
        //
        if (title == null) {
            Uri remoteUri = Uri.parse(remoteUriText);
            List<String> pathSegments = remoteUri.getPathSegments();
            if (pathSegments != null && pathSegments.size() > 0) {
                title = pathSegments.get(pathSegments.size() - 1);
            }
            if (title == null) {
                title = this.mResourceBundle
                        .getString("DownloaderServiceBase.S_NONAME");
            }
        }
        // creates directory
        if (this.makeDir(local, callbackActivityClass) == false) {
            return;
        }
        // gets current file size.
        File localFile = new File(local);
        // Gets the progeress.
        if (localFile.exists()) {
            this.mProgress = localFile.length();
        } else {
            this.mProgress = 0;
        }
        // notification
        this.showDownloading(title, callbackActivityClass);
        // checkpoint
        if (this.mWorking == false) {
            this.showCancel(title, callbackActivityClass);
            return;
        }
        // -------- HTTP
        // create client
        HttpClient client = new DefaultHttpClient();
        // client / set params
        HttpParams sentParams = client.getParams();
        if (sentParams != null && userAgent != null && userAgent.length() > 0) {
            sentParams.setParameter("http.useragent", userAgent);
        }
        // client / set timeout (ms)
        if (connection_timeout_ms > 0) {
            client.getParams().setParameter("http.connection.timeout",
                    Integer.valueOf(connection_timeout_ms));
        }
        if (socket_timeout_ms > 0) {
            client.getParams().setParameter("http.socket.timeout",
                    Integer.valueOf(socket_timeout_ms));
        }

        // Gets only head
        HttpUriRequest headReq = new HttpHead(remoteUriText);
        HttpResponse headRes;
        try {
            headRes = client.execute(headReq);
            if (headRes != null
                    && headRes.getStatusLine().getStatusCode() == 200) {
                // gets content-length
                Header[] contentLengthHeaders = headRes
                        .getHeaders("Content-Length");
                if (contentLengthHeaders != null) {
                    for (Header h : contentLengthHeaders) {
                        if (h != null) {
                            String hvs = h.getValue();
                            if (hvs != null) {
                                long hv = Long.parseLong(hvs);
                                if (this.mContentLength < 0
                                        || hv > this.mContentLength) {
                                    this.mContentLength = hv;
                                }
                            }
                        }
                    }
                }
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        // finished?
        if (this.mContentLength > 0 && this.mProgress >= this.mContentLength) {
            this.showFinished(title, callbackActivityClass);
            return;
        }

        // checkpoint
        if (this.mWorking == false) {
            this.showCancel(title, callbackActivityClass);
            return;
        }

        // gets content.
        // notification
        this.showDownloading(title, callbackActivityClass);
        // open connection
        HttpUriRequest contentReq = new HttpGet(remoteUriText);
        if (this.mProgress > 0) {
            contentReq.addHeader("Range",
                    String.format("bytes=%d-", this.mProgress));
        }
        HttpResponse contentRes = null;
        try {
            contentRes = client.execute(contentReq);
        } catch (ClientProtocolException e) {
            this.showError(e.getMessage(), callbackActivityClass);
            e.printStackTrace();
            return;
        } catch (IOException e) {
            this.showError(e.getMessage(), callbackActivityClass);
            e.printStackTrace();
            return;
        }
        if (contentRes == null) {
            this.showDownloadError(title, null, null, callbackActivityClass);
        }
        // check status. 200 - success, 206 - partial
        int statusCode = contentRes.getStatusLine().getStatusCode();
        if (statusCode != 200 && statusCode != 206) {
            this.showDownloadError(title, statusCode + " "
                    + contentRes.getStatusLine().getReasonPhrase(), null,
                    callbackActivityClass);
            return;
        }
        // gets input stream.
        InputStream is = null;
        try {
            is = contentRes.getEntity().getContent();
        } catch (IllegalStateException e) {
            this.showDownloadError(title, e.getMessage(), null,
                    callbackActivityClass);
            e.printStackTrace();
            return;
        } catch (IOException e) {
            this.showDownloadError(title, e.getMessage(), null,
                    callbackActivityClass);
            e.printStackTrace();
            return;
        }
        // sets up buffer
        byte[] buff = new byte[DownloaderService.BUFFERSIZE];
        // gets output stream.
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(localFile, statusCode == 206);
            int read;
            // checkpoint
            if (this.mWorking == false) {
                this.showCancel(title, callbackActivityClass);
                return;
            }
            // reading loop
            while ((read = is.read(buff, 0, DownloaderService.BUFFERSIZE)) >= 0) {
                // checkpoint
                if (this.mWorking == false) {
                    this.showCancel(title, callbackActivityClass);
                    return;
                }
                if (read > 0) {
                    os.write(buff, 0, read);
                    this.mProgress += read;
                    // notification
                    this.showDownloading(title, callbackActivityClass);
                }
            }
            this.showFinished(title, callbackActivityClass);
        } catch (FileNotFoundException e) {
            this.showDownloadError(title, e.getMessage(), null,
                    callbackActivityClass);
            e.printStackTrace();
            return;
        } catch (SocketTimeoutException e) {
            this.showDownloadError(title, this.mResourceBundle
                    .getString("DownloaderServieBase.S_CONNECTION_DOWN"), e
                    .getMessage(), callbackActivityClass);
            e.printStackTrace();
            return;
        } catch (IOException e) {
            this.showDownloadError(title, e.getMessage(), null,
                    callbackActivityClass);
            e.printStackTrace();
            return;
        } finally {
            // closes output stream and input stream.
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ----------------
    /**
     * Calculates callback activity class, with checks whether received instance
     * if class of Activity.
     * 
     * @param intent
     *            Intent.
     * @return Class of Activity if received instance is suitable. Otherwise,
     *         returns null.
     */
    private Class<? extends Activity> calculateCallbackActivityClass(
            Intent intent) {
        if (intent == null) {
            return null;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }
        if (!extras.containsKey(DownloaderService.XKEY_CALLBACK_ACTIVITY_CLASS)) {
            return null;
        }
        Object obj = intent
                .getSerializableExtra(DownloaderService.XKEY_CALLBACK_ACTIVITY_CLASS);
        if (obj == null) {
            return null;
        }
        if (((Class<?>) obj).asSubclass(Activity.class) != null) {
            return (Class<? extends Activity>) obj;
        }
        return null;
    }

    private boolean makeDir(String filePath,
            Class<? extends Activity> callbackActivityClass) {
        if (filePath == null || !(filePath.length() > 0)) {
            return true;
        }
        String curPath = "";
        int ix;
        while (filePath.length() > 0
                && (ix = filePath.indexOf(File.separator)) >= 0) {
            curPath = curPath + filePath.substring(0, ix);
            filePath = filePath.substring(ix + File.separator.length());
            if (curPath.length() > 0) {
                File curFile = new File(curPath);
                if (!curFile.isDirectory()) {
                    // not directory / no such file or directory
                    if (curFile.exists()) {
                        // not directory
                        String fmt = this.mResourceBundle
                                .getString("DownloaderServieBase.F_1_IS_NOT_DIRECTORY");
                        String mess = String.format(fmt, curPath);
                        this.showError(mess, callbackActivityClass);
                        return false;
                    } else {
                        // no such file or directory
                        if (curFile.mkdir() == false) {
                            // fails to create
                            String fmt = this.mResourceBundle
                                    .getString("DownloaderServieBase.F_FAIL_MKDIR_1");
                            String mess = String.format(fmt, curPath);
                            this.showError(mess, callbackActivityClass);
                            return false;
                        }
                    }
                }
            }
            curPath = curPath + File.separator;
        }
        return true;
    }

}
