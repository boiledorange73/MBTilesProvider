package com.gmail.boiledorange73.and4.ut.app;

import java.lang.reflect.Field;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

public class ActivityBase extends Activity {
    private Handler mHandler;

    protected Handler getHandler() {
        return this.mHandler;
    }

    public boolean isStartedAsMain() {
        Intent intent = this.getIntent();
        if (intent == null) {
            return true;
        }
        String action = intent.getAction();
        if (action == null) {
            return true;
        }
        return action.equals(Intent.ACTION_MAIN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mHandler = new Handler();
        // creates action bar if supported.
        Field[] fields = Window.class.getFields();
        if( fields != null ) {
            for( Field f : fields ) {
                if( "FEATURE_ACTION_BAR".equals(f.getName()) ) {
                    try {
                        this.getWindow().requestFeature((Integer) f.get(null));
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
    
    @Override
    protected  void onDestroy() {
        this.mHandler = null;
        super.onDestroy();
    }
}
