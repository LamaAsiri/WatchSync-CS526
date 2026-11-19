package com.cs526.watchsync;

import android.app.Application;

import com.cs526.watchsync.util.Lang;

/** Runs once when the app starts, before any screen. */
public class WatchSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Lang.init(this);
        Lang.apply(this); // saved language, also when Android reopens a screen directly
    }
}
