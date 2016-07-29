package me.alexroth.gpgmail;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

/**
 * Main application used to initialize things app-wide.
 *
 * @author alex
 * @since 7/28/16
 */
public class GPGApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fresco.initialize(this);
    }

}
