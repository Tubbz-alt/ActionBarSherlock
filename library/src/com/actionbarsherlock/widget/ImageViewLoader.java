package com.actionbarsherlock.widget;

import android.util.Log;
import android.widget.ImageView;

public interface ImageViewLoader {
    ImageViewLoader NO_OP = new ImageViewLoader() {
        @Override
        public void loadImageView(ImageView imageView, String url, int fallbackIconResId) {}
    };
    ImageViewLoader NO_OP_WARNING = new ImageViewLoader() {
        @Override
        public void loadImageView(ImageView imageView, String url, int fallbackIconResId) {
            Log.w(ImageViewLoader.class.getSimpleName(),
                    "Using no-op ImageViewLoader. Did you provide an implementation as a system service in your application class " +
                            "with the name 'com.actionbarsherlock.widget.ImageViewLoader'?");
        }
    };

    void loadImageView(ImageView imageView, String url, int fallbackIconResId);
}
