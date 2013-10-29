package com.actionbarsherlock.widget;

import android.widget.ImageView;

public interface ImageViewLoader {
    ImageViewLoader NO_OP = new ImageViewLoader() {
        @Override
        public void loadImageView(ImageView imageView, String url, int fallbackIconResId) {}
    };

    void loadImageView(ImageView imageView, String url, int fallbackIconResId);
}
