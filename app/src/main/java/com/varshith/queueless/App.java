package com.varshith.queueless;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * Application entry point.
 * Material 3 dynamic colour (Android 12+) is applied to every Activity;
 * devices without dynamic colour keep our bold indigo seed theme.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
