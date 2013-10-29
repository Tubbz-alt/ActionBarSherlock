/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionbarsherlock.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import com.actionbarsherlock.R;
import com.actionbarsherlock.view.ActionProvider;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;
import com.actionbarsherlock.view.SubMenu;

/**
 * This is a provider for a share action. It is responsible for creating views
 * that enable data sharing and also to show a sub menu with sharing activities
 * if the hosting item is placed on the overflow menu.
 * <p>
 * Here is how to use the action provider with custom backing file in a {@link MenuItem}:
 * </p>
 * <p>
 * <pre>
 * <code>
 *  // In Activity#onCreateOptionsMenu
 *  public boolean onCreateOptionsMenu(Menu menu) {
 *      // Get the menu item.
 *      MenuItem menuItem = menu.findItem(R.id.my_menu_item);
 *      // Get the provider and hold onto it to set/change the share intent.
 *      mShareActionProvider = (ShareActionProvider) menuItem.getActionProvider();
 *      // Set history different from the default before getting the action
 *      // view since a call to {@link MenuItem#getActionView() MenuItem.getActionView()} calls
 *      // {@link ActionProvider#onCreateActionView()} which uses the backing file name. Omit this
 *      // line if using the default share history file is desired.
 *      mShareActionProvider.setShareHistoryFileName("custom_share_history.xml");
 *      . . .
 *  }
 *
 *  // Somewhere in the application.
 *  public void doShare(Intent shareIntent) {
 *      // When you want to share set the share intent.
 *      mShareActionProvider.setShareIntent(shareIntent);
 *  }
 * </pre>
 * </code>
 * </p>
 * <p>
 * <strong>Note:</strong> While the sample snippet demonstrates how to use this provider
 * in the context of a menu item, the use of the provider is not limited to menu items.
 * </p>
 *
 * @see ActionProvider
 */
public class ShareActionProvider extends ActionProvider {

    private static final int NOT_DEFINED = -1;
    private EntryChooserView activityChooserView;
    private int viewId = NOT_DEFINED;
    private boolean mostCommonItemEnabled = true;

    /**
     * Listener for the event of selecting a share target.
     */
    public interface OnShareTargetSelectedListener {

        /**
         * Called when a share target has been selected. The client can
         * decide whether to handle the intent or rely on the default
         * behavior which is launching it.
         * <p>
         * <strong>Note:</strong> Modifying the intent is not permitted and
         *     any changes to the latter will be ignored.
         * </p>
         *
         * @param source The source of the notification.
         * @param intent The intent for launching the chosen share target.
         * @return Whether the client has handled the intent.
         */
        public boolean onShareTargetSelected(ShareActionProvider source, Intent intent);
    }

    /**
     * The default for the maximal number of activities shown in the sub-menu.
     */
    private static final int DEFAULT_INITIAL_ACTIVITY_COUNT = 4;

    /**
     * The the maximum number activities shown in the sub-menu.
     */
    private final int mMaxShownActivityCount = DEFAULT_INITIAL_ACTIVITY_COUNT;

    /**
     * Listener for handling menu item clicks.
     */
    private final ShareMenuItemOnMenuItemClickListener mOnMenuItemClickListener =
        new ShareMenuItemOnMenuItemClickListener();

    /**
     * The default name for storing share history.
     */
    public static final String DEFAULT_SHARE_HISTORY_FILE_NAME = "share_history.xml";

    /**
     * Context for accessing resources.
     */
    private final Context mContext;

    /**
     * The name of the file with share history data.
     */
    private String mShareHistoryFileName = DEFAULT_SHARE_HISTORY_FILE_NAME;

    private OnShareTargetSelectedListener mOnShareTargetSelectedListener;

    private EntryChooserModel.OnChooseEntryListener mOnChooseEntryListener;
    private OnClickListener dropDownListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
      }
    };

    /**
     * Creates a new instance.
     *
     * @param context Context for accessing resources.
     */
    public ShareActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * Sets a listener to be notified when a share target has been selected.
     * The listener can optionally decide to handle the selection and
     * not rely on the default behavior which is to launch the activity.
     * <p>
     * <strong>Note:</strong> If you choose the backing share history file
     *     you will still be notified in this callback.
     * </p>
     * @param listener The listener.
     */
    public void setOnShareTargetSelectedListener(OnShareTargetSelectedListener listener) {
        mOnShareTargetSelectedListener = listener;
        setActivityChooserPolicyIfNeeded();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateActionView() {
        // Create the view and set its data model.
        EntryChooserModel dataModel = EntryChooserModel.get(mContext, mShareHistoryFileName);
        activityChooserView = new EntryChooserView(mContext);
        activityChooserView.setOnDropDownListener(dropDownListener);

        activityChooserView.setEntryChooserModel(dataModel);
        if (viewId != NOT_DEFINED) {
            activityChooserView.setId(viewId);
        }
        activityChooserView.setMostCommonItemEnabled(mostCommonItemEnabled);

        // Lookup and set the expand action icon.
        TypedValue outTypedValue = new TypedValue();
        mContext.getTheme().resolveAttribute(R.attr.actionModeShareDrawable, outTypedValue, true);
        Drawable drawable = mContext.getResources().getDrawable(outTypedValue.resourceId);
        activityChooserView.setExpandActivityOverflowButtonDrawable(drawable);
        activityChooserView.setProvider(this);

        // Set content description.
        activityChooserView.setDefaultActionButtonContentDescription(
                R.string.abs__shareactionprovider_share_with_application);
        activityChooserView.setExpandActivityOverflowButtonContentDescription(
                R.string.abs__shareactionprovider_share_with);


        return activityChooserView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSubMenu() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPrepareSubMenu(SubMenu subMenu) {
        // Clear since the order of items may change.
        subMenu.clear();

        EntryChooserModel dataModel = EntryChooserModel.get(mContext, mShareHistoryFileName);
        final int expandedActivityCount = dataModel.getEntryCount();
        final int collapsedActivityCount = Math.min(expandedActivityCount, mMaxShownActivityCount);

        // Populate the sub-menu with a sub set of the activities.
        for (int i = 0; i < collapsedActivityCount; i++) {
            Entry entry = dataModel.getEntry(i);
            subMenu.add(0, i, i, entry.getLabel())
                .setOnMenuItemClickListener(mOnMenuItemClickListener);
            Drawable entryIcon = entry.getIcon();
            if (entryIcon != null) {
                subMenu.setIcon(entryIcon);
            }
        }

        if (collapsedActivityCount < expandedActivityCount) {
            // Add a sub-menu for showing all activities as a list item.
            SubMenu expandedSubMenu = subMenu.addSubMenu(Menu.NONE, collapsedActivityCount,
                    collapsedActivityCount,
                    mContext.getString(R.string.abs__activity_chooser_view_see_all));
            for (int i = 0; i < expandedActivityCount; i++) {
                Entry activity = dataModel.getEntry(i);
                expandedSubMenu.add(0, i, i, activity.getLabel())
                    .setIcon(activity.getIcon())
                    .setOnMenuItemClickListener(mOnMenuItemClickListener);
            }
        }
    }

    /**
     * Sets the file name of a file for persisting the share history which
     * history will be used for ordering share targets. This file will be used
     * for all view created by {@link #onCreateActionView()}. Defaults to
     * {@link #DEFAULT_SHARE_HISTORY_FILE_NAME}. Set to <code>null</code>
     * if share history should not be persisted between sessions.
     * <p>
     * <strong>Note:</strong> The history file name can be set any time, however
     * only the action views created by {@link #onCreateActionView()} after setting
     * the file name will be backed by the provided file.
     * <p>
     *
     * @param shareHistoryFile The share history file name.
     */
    public void setShareHistoryFileName(String shareHistoryFile) {
        mShareHistoryFileName = shareHistoryFile;
        setActivityChooserPolicyIfNeeded();
    }

    /**
     * Sets an intent with information about the share action. Here is a
     * sample for constructing a share intent:
     * <p>
     * <pre>
     * <code>
     *  Intent shareIntent = new Intent(Intent.ACTION_SEND);
     *  shareIntent.setType("image/*");
     *  Uri uri = Uri.fromFile(new File(getFilesDir(), "foo.jpg"));
     *  shareIntent.putExtra(Intent.EXTRA_STREAM, uri.toString());
     * </pre>
     * </code>
     * </p>
     *
     * @param shareIntent The share intent.
     *
     * @see Intent#ACTION_SEND
     * @see Intent#ACTION_SEND_MULTIPLE
     */
    public void setShareIntent(Intent shareIntent) {
        EntryChooserModel dataModel = EntryChooserModel.get(mContext,
                mShareHistoryFileName);
        dataModel.setIntent(shareIntent);
    }

    /**
     * Allows custom entries to be added <strong>before</strong> the normal entries which are resolved via the share intent.<br />
     * This method <strong>MUST</strong> be called before calling {@link ShareActionProvider#setShareIntent(android.content.Intent)}.</br>
     * <strong>Note that this will clear any existing prepended entries set before.</strong>
     * @param customEntries The entries to prepend.
     */
    public void setPrependedEntries(Entry... customEntries)
    {
        EntryChooserModel dataModel = EntryChooserModel.get(mContext, mShareHistoryFileName);
        dataModel.setPrependedEntries(customEntries);
    }

    /**
     * Allows custom entries to be added alongside the normal entries which are resolved via the share intent.<br />
     * This method <strong>MUST</strong> be called before calling {@link ShareActionProvider#setShareIntent(android.content.Intent)}.</br>
     * <strong>Note that this will clear any existing additional entries set before.</strong>
     * @param customEntries The entries to add.
     */
    public void setAdditionalEntries(Entry... customEntries)
    {
        EntryChooserModel dataModel = EntryChooserModel.get(mContext, mShareHistoryFileName);
        dataModel.setAdditionalEntries(customEntries);
    }

    /**
     * Call this method to set the id of the internal share view. This will only work if called both before AND after
     * the view has been created.
     * @param viewId The id for the view
     */
    public void setActionViewId(int viewId) {
        this.viewId = viewId;
        if (activityChooserView != null) {
            activityChooserView.setId(viewId);
        }
    }

    /**
     * Disables or enables most common item visibility on the share view,
     * @param mostCommonItemEnabled visibility of the most common item selected.
     */
    public void setMostCommonItemEnabled(boolean mostCommonItemEnabled) {
        this.mostCommonItemEnabled = mostCommonItemEnabled;
        if (activityChooserView != null) {
            activityChooserView.setMostCommonItemEnabled(mostCommonItemEnabled);
        }
    }

    public void setOnDropDownListener(View.OnClickListener dropDownListener) {
      this.dropDownListener = dropDownListener;
    }

    /**
     * Reusable listener for handling share item clicks.
     */
    private class ShareMenuItemOnMenuItemClickListener implements OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            EntryChooserModel dataModel = EntryChooserModel.get(mContext,
                    mShareHistoryFileName);
            final int itemId = item.getItemId();
            Intent launchIntent = dataModel.chooseEntry(itemId);
            if (launchIntent != null) {
                mContext.startActivity(launchIntent);
            }
            return true;
        }
    }

    /**
     * Set the activity chooser policy of the model backed by the current
     * share history file if needed which is if there is a registered callback.
     */
    private void setActivityChooserPolicyIfNeeded() {
        if (mOnShareTargetSelectedListener == null) {
            return;
        }
        if (mOnChooseEntryListener == null) {
            mOnChooseEntryListener = new ShareAcitivityChooserModelPolicy();
        }
        EntryChooserModel dataModel = EntryChooserModel.get(mContext, mShareHistoryFileName);
        dataModel.setOnChooseEntryListener(mOnChooseEntryListener);
    }

    /**
     * Policy that delegates to the {@link OnShareTargetSelectedListener}, if such.
     */
    private class ShareAcitivityChooserModelPolicy implements EntryChooserModel.OnChooseEntryListener
    {
        @Override
        public boolean onChooseEntry(EntryChooserModel host, Intent intent) {
            if (mOnShareTargetSelectedListener != null) {
                return mOnShareTargetSelectedListener.onShareTargetSelected(
                        ShareActionProvider.this, intent);
            }
            return false;
        }
    }
}
