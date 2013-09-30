package com.actionbarsherlock.internal;

import android.graphics.Bitmap;
import android.util.Log;
import com.webimageloader.ImageLoader;
import com.webimageloader.Request;
import com.webimageloader.loader.MemoryCache;
import com.webimageloader.transformation.Transformation;

import java.io.IOException;

public class NoOpWarningImageLoader implements ImageLoader {
    @Override
    public MemoryCache.DebugInfo getMemoryCacheInfo() {
        warn();
        return null;
    }

    @Override
    public MemoryCache getMemoryCache() {
        warn();
        return null;
    }

    @Override
    public Bitmap loadBlocking(String url) throws IOException {
        warn();
        return null;
    }

    @Override
    public Bitmap loadBlocking(String url, Transformation transformation) throws IOException {
        warn();
        return null;
    }

    @Override
    public Bitmap loadBlocking(Request request) throws IOException {
        warn();
        return null;
    }

    @Override
    public Bitmap loadBlocking(Request request, ProgressListener progressListener) throws IOException {
        warn();
        return null;
    }

    @Override
    public void preload(String url) {
        warn();
    }

    @Override
    public void preload(String url, Transformation transformation) {
        warn();
    }

    @Override
    public void preload(Request request) {
        warn();
    }

    @Override
    public <T> Bitmap load(T tag, String url, Listener<T> listener) {
        warn();
        return null;
    }

    @Override
    public <T> Bitmap load(T tag, String url, Transformation transformation, Listener<T> listener) {
        warn();
        return null;
    }

    @Override
    public <T> Bitmap load(T tag, Request request, Listener<T> listener) {
        warn();
        return null;
    }

    @Override
    public <T> Bitmap load(T tag, Request request, Listener<T> listener, ProgressListener progressListener) {
        warn();
        return null;
    }

    private void warn() {
        Log.w("ImageLoader", "Using a no-op image loader. Did you inject a real one via ShareActionProvider.setImageLoader?");
    }

    @Override
    public <T> void cancel(T tag) {
        warn();
    }

    @Override
    public void destroy() {
        warn();
    }
}
