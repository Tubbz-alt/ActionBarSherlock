package com.actionbarsherlock.widget;

import android.content.Intent;
import android.graphics.drawable.Drawable;

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
    Drawable getIcon();
    Intent getIntent();
}
