package com.gmail.boiledorange73.and4.ut.app;

import java.io.File;
import java.util.ResourceBundle;

import com.gmail.boiledorange73.and4.ut.dl.DownloaderServiceBase;
import com.gmail.boiledorange73.ut.FileUtil;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public abstract class DownloaderActivityBase extends ActivityBase {

    public static enum DataFileStatusEnum {
        not_found, must_update, may_update, broken, exists
    }

    public class DownloaderServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloaderActivityBase.this.onDownloaderStatusChanged(context,
                    intent);
        }
    }

    private DownloaderServiceReceiver mReceiver;
    private boolean mNowDownloading;
    private int mProgressPercentValue;
    private long mFileSizeValue;
    private long mProgressValue;

    private ResourceBundle mResourceBundle;
    private TextView mTxtDataTitle;
    private TextView mTxtProgress;
    private ProgressBar mProgressPercent;
    private ProgressBar mProgressCircle;
    private Button mBtnStartDL;
    private Button mBtnCancelDL;
    private Button mBtnDelete;
    private TextView mTxtMemory;
    private Button mBtnStartApp;
    private TextView mTxtMessage;

    private void onDownloaderStatusChanged(Context context, Intent intent) {
        int status = intent.getIntExtra(DownloaderServiceBase.XKEY_STATUS,
                DownloaderServiceBase.ST_NONE);
        if (status == DownloaderServiceBase.ST_ERROR) {
            this.mNowDownloading = false;
            this.mTxtMessage.setText(intent
                    .getStringExtra(DownloaderServiceBase.XKEY_MESSAGE));
            this.changeStatus();
        } else {
            if (status == DownloaderServiceBase.ST_FINISHED) {
                this.mNowDownloading = false;
                this.changeStatus();
            } else if (status == DownloaderServiceBase.ST_CANCEL) {
                this.mNowDownloading = false;
                this.changeStatus();
            } else if (status == DownloaderServiceBase.ST_DOWNLOADING) {
                this.mNowDownloading = true;
                this.mFileSizeValue = intent.getLongExtra(
                        DownloaderServiceBase.XKEY_FILESIZE, -1L);
                this.mProgressValue = intent.getLongExtra(
                        DownloaderServiceBase.XKEY_PROGRESS, -1L);
                if (this.mFileSizeValue >= 0) {
                    this.mProgressPercentValue = (int) (((double) this.mProgressValue * 100.0) / (double) this.mFileSizeValue);
                    this.changeStatus();
                } else {
                    this.mProgressPercentValue = -1;
                    this.changeStatus();
                }
            }
        }
    }

    private DataFileStatusEnum calculateDataFileStatus() {
        String local = this.getLocal();
        if (local == null) {
            return DataFileStatusEnum.not_found;
        }
        File localFile = new File(local);
        if (!localFile.exists()) {
            return DataFileStatusEnum.not_found;
        }
        DataFileStatusEnum ret = this.checkDataFileStatus(localFile);
        if (ret == null) {
            ret = DataFileStatusEnum.exists;
        }
        return ret;
    }

    // onNewIntent is required for SingleTask activity
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.mResourceBundle = ResourceBundle
                .getBundle("com.gmail.boiledorange73.and4.ut.app.messages");

        // KeepScreenOn
        if (this.getKeepScreenOn()) {
            this.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        this.initViews();

        this.mReceiver = new DownloaderActivityBase.DownloaderServiceReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DownloaderServiceBase.BROADCAST_ACTION);
        this.registerReceiver(this.mReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.changeStatus();
    }

    @Override
    protected void onDestroy() {
        if (this.mReceiver != null) {
            this.unregisterReceiver(this.mReceiver);
            this.mReceiver = null;
        }
        this.mResourceBundle = null;
        super.onDestroy();
    }

    protected boolean getKeepScreenOn() {
        return true;
    }

    private Intent mServiceIntent = null;

    protected abstract String getDataTitle();

    protected abstract String getRemoteUrl();

    protected abstract String getRelativePath();

    protected abstract DataFileStatusEnum checkDataFileStatus(File localFile);

    protected abstract Class<? extends Service> getDownloaderServiceClass();

    protected abstract boolean isAutoStart();
    
    protected abstract boolean isAppInhibitable();

    /**
     * Called before downloaded file is deleted.
     * 
     * @return Whether permits to delete the file. If returns false, the file is
     *         not deleted.
     */
    protected abstract boolean onBeforeDeleteDownload();

    /**
     * Starts the application
     */
    protected abstract boolean startApplication();

    /**
     * Calculates the directory where downloaded file is put on. If you want to
     * download the file on the directory different from
     * {@link android.os.Environment#getExternalStorageDirectory()}, override
     * this method.
     * 
     * @return The path text.
     */
    public File getLocalDirectory() {
        return Environment.getExternalStorageDirectory();
    }

    /**
     * Gets the path of downloaded file.
     * 
     * @return The path text.
     */
    public String getLocal() {
        String relativePath = this.getRelativePath();
        if (relativePath == null) {
            String remoteUrl = this.getRemoteUrl();
            relativePath = FileUtil.calculateFileNameByUrl(remoteUrl);
        }
        if (relativePath == null) {
            return null;
        }
        String local = FileUtil.calculatePath(relativePath,
                this.getLocalDirectory());
        return local;
    }

    // --------
    // Handles when one of buttons is clicked.
    //

    /**
     * Starts downloading.
     */
    private void startDownload() {
        String local = this.getLocal();
        if (local == null) {
            this.mTxtMemory.setText(this.mResourceBundle
                    .getString("DownloaderActivityBase.S_UNKNOWN_DSTFILENAME"));
            return;
        }

        String remoteUrl = this.getRemoteUrl();

        String title = this.getDataTitle();
        if (title == null) {
            title = FileUtil.calculateFileNameByUrl(remoteUrl);
        }

        this.mServiceIntent = new Intent(this, this.getDownloaderServiceClass());
        this.mServiceIntent.putExtra(DownloaderServiceBase.XKEY_TITLE, title);
        this.mServiceIntent.putExtra(DownloaderServiceBase.XKEY_REMOTE,
                remoteUrl);
        this.mServiceIntent.putExtra(DownloaderServiceBase.XKEY_LOCAL, local);
        this.startService(this.mServiceIntent);
    }

    /**
     * Cancels downloading.
     */
    private void cancelDownload() {
        if (this.mServiceIntent != null) {
            this.stopService(this.mServiceIntent);
            this.mServiceIntent = null;
        }
    }

    /**
     * Confirming deleting the donwloaded file.
     */
    private void confirmDeleteDownload() {
        File f = new File(this.getLocal());
        if (f.exists()) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this);
            bld.setTitle(this.mResourceBundle
                    .getString("DownloaderActivityBase.S_CONFIRMATION"));
            bld.setMessage(this.mResourceBundle
                    .getString("DownloaderActivityBase.Q_DELETE_FILE"));
            bld.setPositiveButton(this.getText(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DownloaderActivityBase.this.getHandler().post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            DownloaderActivityBase.this
                                                    .deleteDownload();
                                        }
                                    });
                        }
                    });
            bld.setNegativeButton(this.getText(android.R.string.cancel), null);
            bld.show();

        }
        if (this.mServiceIntent != null) {
            this.stopService(this.mServiceIntent);
            this.mServiceIntent = null;
        }
    }

    /**
     * Really deletes the downloaded file.
     */
    private void deleteDownload() {
        String local = this.getLocal();
        File f = new File(local);
        if (f.exists()) {
            if (this.onBeforeDeleteDownload() == true) {
                f.delete();
                this.changeStatus();
            }
        }
    }

    private void startApp() {
        if (this.startApplication()) {
            this.finish();
        }
    }

    private void resetTxtFileSize() {
        if (this.mProgressValue >= 0) {
            this.mTxtProgress.setText(FileUtil
                    .calculateBytesText(this.mProgressValue));
        } else {
            this.mTxtProgress.setText("");
        }
    }

    // --------
    // Operating statuses of views.
    // --------
    private void changeStatus() {
        if (this.mNowDownloading == true) {
            // now downloading.
            if (this.mProgressPercentValue < 0) {
                this.mProgressPercent.setVisibility(View.INVISIBLE);
                this.mProgressCircle.setVisibility(View.VISIBLE);
            } else {
                this.mProgressPercent.setVisibility(View.VISIBLE);
                this.mProgressCircle.setVisibility(View.INVISIBLE);
                this.mProgressPercent.setProgress(this.mProgressPercentValue);
            }
            this.resetTxtFileSize();
            this.mBtnStartDL.setEnabled(false);
            this.mBtnCancelDL.setEnabled(true);
            this.mBtnDelete.setEnabled(false);
            this.mBtnStartApp.setEnabled(false);
            this.mTxtMessage.setText("");
        } else {
            // now waiting for commands.
            // progresses are hidden.
            this.mProgressPercent.setVisibility(View.INVISIBLE);
            this.mProgressCircle.setVisibility(View.INVISIBLE);

            // Calculate file size
            File f = new File(this.getLocal());
            this.mFileSizeValue = -1;
            this.mProgressValue = f.exists() ? f.length() : -1;
            // shows file size
            this.resetTxtFileSize();
            //
            boolean appInhibitable = this.isAppInhibitable();
            switch (this.calculateDataFileStatus()) {
            case exists:
                this.mBtnStartDL.setEnabled(false);
                this.mBtnCancelDL.setEnabled(false);
                this.mBtnDelete.setEnabled(true);
                // start button is active only if NOT inhibitable.
                this.mBtnStartApp.setEnabled(!appInhibitable);
                this.mTxtMessage.setText("");
                // start ?
                // Auto Start mode and NOT inhibitable.
                if (this.isAutoStart() && appInhibitable == false) {
                    this.startApp();
                }
                break;
            case may_update:
                this.mBtnStartDL.setEnabled(false);
                this.mBtnCancelDL.setEnabled(false);
                this.mBtnDelete.setEnabled(true);
                // start button is active only if NOT inhibitable.
                this.mBtnStartApp.setEnabled(!appInhibitable);
                this.mTxtMessage.setText(this.mResourceBundle
                        .getString("DownloaderActivityBase.S_YOU_HAVE_UPDATE"));
                break;
            case must_update:
                this.mBtnStartDL.setEnabled(false);
                this.mBtnCancelDL.setEnabled(false);
                this.mBtnDelete.setEnabled(true);
                this.mBtnStartApp.setEnabled(false);
                this.mTxtMessage.setText(this.mResourceBundle
                        .getString("DownloaderActivityBase.S_YOU_MUST_UPDATE"));
                break;
            case broken: // broken
                this.mBtnStartDL.setEnabled(true);
                this.mBtnCancelDL.setEnabled(false);
                this.mBtnDelete.setEnabled(true);
                this.mBtnStartApp.setEnabled(false);
                this.mTxtMessage.setText(this.mResourceBundle
                        .getString("DownloaderActivityBase.S_CANNOT_EXECUTE"));
                break;
            // not found and so on.
            case not_found:
            default:
                this.mBtnStartDL.setEnabled(true);
                this.mBtnCancelDL.setEnabled(false);
                this.mBtnDelete.setEnabled(false);
                this.mBtnStartApp.setEnabled(false);
                this.mTxtMessage.setText("");
                break;
            }
        }
    }

    // --------
    // Operating views.
    // --------
    /**
     * Changes memotry text.
     */
    private void changeMemoryText() {
        this.mTxtMemory
                .setText(AppInfo.calculateStorageStatusText(this.mResourceBundle
                        .getString("DownloaderActivityBase.F_MEMORY_AVAILABLE_TOTAL")));
    }

    /**
     * Initializes all views. This is called by {@link #onCreate(Bundle)}.
     */
    private void initViews() {
        // sp_per_scale rate.
        Resources rc = this.getResources();
        float px_per_sp = 1.0F;
        if (rc != null) {
            DisplayMetrics metrics = rc.getDisplayMetrics();
            if (metrics != null) {
                px_per_sp = metrics.scaledDensity;
            }
        }
        // Java resource
        this.mResourceBundle = ResourceBundle
                .getBundle("com.gmail.boiledorange73.and4.ut.app.messages");
        //
        LinearLayout.LayoutParams layoutParamsSmallRight = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParamsSmallRight.setMargins((int) (10.0F * px_per_sp),
                (int) (5.0F * px_per_sp), (int) (10.0F * px_per_sp),
                (int) (5.0F * px_per_sp));

        // Activity / scrollView / root (LinearLayout)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        this.setContentView(scrollView);
        LinearLayout root = new LinearLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(root);
        // linear layout params
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins((int) (10.0F * px_per_sp),
                (int) (5.0F * px_per_sp), (int) (10.0F * px_per_sp),
                (int) (5.0F * px_per_sp));
        // DataTitle
        this.mTxtDataTitle = new TextView(this);
        this.mTxtDataTitle.setLayoutParams(layoutParams);
        this.mTxtDataTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        this.mTxtDataTitle.setText(this.getDataTitle());
        root.addView(this.mTxtDataTitle);
        // file size
        this.mTxtProgress = new TextView(this);
        this.mTxtProgress.setLayoutParams(layoutParamsSmallRight);
        this.mTxtProgress.setGravity(Gravity.RIGHT);
        this.mTxtProgress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(this.mTxtProgress);
        // Progress container
        RelativeLayout progress = new RelativeLayout(this);
        progress.setLayoutParams(layoutParams);
        root.addView(progress);
        // progress percent
        RelativeLayout.LayoutParams rlpPercent = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.FILL_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        rlpPercent.addRule(RelativeLayout.CENTER_VERTICAL);
        this.mProgressPercent = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        this.mProgressPercent.setLayoutParams(rlpPercent);
        this.mProgressPercent.setMax(100);
        this.mProgressPercent.setVisibility(View.INVISIBLE);
        progress.addView(this.mProgressPercent);
        //
        RelativeLayout.LayoutParams rlpCircle = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.FILL_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        rlpCircle.addRule(RelativeLayout.CENTER_VERTICAL);
        rlpCircle.addRule(RelativeLayout.CENTER_HORIZONTAL);
        this.mProgressCircle = new ProgressBar(this, null,
                android.R.attr.progressBarStyle);
        this.mProgressCircle.setLayoutParams(rlpCircle);
        this.mProgressCircle.setVisibility(View.INVISIBLE);
        progress.addView(this.mProgressCircle);
        // start dl
        this.mBtnStartDL = new Button(this);
        this.mBtnStartDL.setLayoutParams(layoutParams);
        this.mBtnStartDL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        this.mBtnStartDL.setText(this.mResourceBundle
                .getString("DownloaderActivityBase.S_START_DL"));
        this.mBtnStartDL.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloaderActivityBase.this.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        DownloaderActivityBase.this.startDownload();
                    }
                });
            }
        });
        root.addView(this.mBtnStartDL);
        // cancel dl
        this.mBtnCancelDL = new Button(this);
        this.mBtnCancelDL.setLayoutParams(layoutParams);
        this.mBtnCancelDL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        this.mBtnCancelDL.setText(this.mResourceBundle
                .getString("DownloaderActivityBase.S_CANCEL_DL"));
        this.mBtnCancelDL.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloaderActivityBase.this.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        DownloaderActivityBase.this.cancelDownload();
                    }
                });
            }
        });
        root.addView(this.mBtnCancelDL);
        // delete
        this.mBtnDelete = new Button(this);
        this.mBtnDelete.setLayoutParams(layoutParams);
        this.mBtnDelete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        this.mBtnDelete.setText(this.mResourceBundle
                .getString("DownloaderActivityBase.S_DELETE_FILE"));
        this.mBtnDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloaderActivityBase.this.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        DownloaderActivityBase.this.confirmDeleteDownload();
                    }
                });
            }
        });
        root.addView(this.mBtnDelete);
        // memory
        this.mTxtMemory = new TextView(this);
        this.mTxtMemory.setLayoutParams(layoutParamsSmallRight);
        this.mTxtMemory.setGravity(Gravity.RIGHT);
        this.mTxtMemory.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(this.mTxtMemory);
        this.changeMemoryText();
        // start
        this.mBtnStartApp = new Button(this);
        this.mBtnStartApp.setLayoutParams(layoutParams);
        this.mBtnStartApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        this.mBtnStartApp.setText(this.mResourceBundle
                .getString("DownloaderActivityBase.S_START_APP"));
        this.mBtnStartApp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloaderActivityBase.this.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        DownloaderActivityBase.this.startApp();
                    }
                });
            }
        });
        root.addView(this.mBtnStartApp);
        // message
        this.mTxtMessage = new TextView(this);
        this.mTxtMessage.setLayoutParams(layoutParams);
        this.mTxtMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(this.mTxtMessage);
        // init
        this.changeStatus();
    }

}
