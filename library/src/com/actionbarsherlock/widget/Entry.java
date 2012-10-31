package com.actionbarsherlock.widget;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

/**
 * Implementations of this class <strong>MUST</strong> override {@link #hashCode()} and {@link #equals(Object)} to avoid
 * duplicate entries when deserializing from history file. As a convenience, the class {@link EntryHasherAndComparator}
 * can be used to implement these methods as well as the method {@link #compareTo(Object)}
 */
public interface Entry extends Comparable<Entry> {
    float getWeight();
    void setWeight(float weight);
    String getIdentifier();
    CharSequence getLabel();

    /**
     * This method will be called if the method {@link #getIcon()} returns null. In that case, if this method returns
     * a valid image Url, it will be used as the icon for the list entry.
     * @return Null or a valid image url to be used for the list entry.
     */
    @Nullable String getIconUrl();

    /**
     * If this method returns a drawable, then it will be used in the list. If it is null, then if the method
     * {@link #getIconUrl()} returns a valid Url, it will be used to load an icon.
     * @return Null for async icon loading, or a drawable to be used in the list.
     */
    @Nullable Drawable getIcon();
    Intent getIntent();

    /**
     * This will be used if {@link #getIcon()} returns null and {@link #getIconUrl()} returns a valid image Url.
     * The drawable with the resource id returned here, will be used as the fallback drawable in case fetching the
     * image from the {@link #getIconUrl()} link fails to load.
     * @return A drawable resource id to be used as a fallback.
     */
    @Nonnegative
    int getFallbackIconResId();

    /**
     * If this method returns a string, the entry will have two lines and this string will be the subtitle.
     * @return Null to have one line entry, or a string to be displayed as the second line.
     */
    @Nullable
    String getSubLabel();
}
