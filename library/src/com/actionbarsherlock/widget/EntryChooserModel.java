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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.DataSetObservable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * <p>
 * This class represents a data model for choosing a component for handing a
 * given {@link Intent}. The model is responsible for querying the system for
 * activities that can handle the given intent and order found activities
 * based on historical data of previous choices. The model also allows custom entries
 * to be included alongside the resolved activities. The historical data is stored
 * in an application private file. If a client does not want to have persistent
 * choice history the file can be omitted, thus the entries will be ordered
 * based on historical usage for the current session.
 * <p>
 * </p>
 * For each backing history file there is a singleton instance of this class. Thus,
 * several clients that specify the same history file will share the same model. Note
 * that if multiple clients are sharing the same model they should implement semantically
 * equivalent functionality since setting the model intent will change the found
 * activities and they may be inconsistent with the functionality of some of the clients.
 * For example, choosing a share activity can be implemented by a single backing
 * model and two different views for performing the selection. If however, one of the
 * views is used for sharing but the other for importing, for example, then each
 * view should be backed by a separate model.
 * </p>
 * <p>
 * The way clients interact with this class is as follows:
 * </p>
 * <p>
 * <pre>
 * <code>
 *  // Get a model and set it to a couple of clients with semantically similar function.
 *  EntryChooserModel dataModel =
 *      EntryChooserModel.get(context, "task_specific_history_file_name.xml");
 *
 *  EntryChooserModelClient modelClient1 = getActivityChooserModelClient1();
 *  modelClient1.setEntryChooserModel(dataModel);
 *
 *  EntryChooserModelClient modelClient2 = getActivityChooserModelClient2();
 *  modelClient2.setEntryChooserModel(dataModel);
 *
 *  // Set an intent to choose a an activity for.
 *  dataModel.setIntent(intent);
 * <pre>
 * <code>
 * </p>
 * <p>
 * <strong>Note:</strong> This class is thread safe.
 * </p>
 *
 * @hide
 */
class EntryChooserModel extends DataSetObservable {


    /**
     * Client that utilizes an {@link EntryChooserModel}.
     */
    public interface EntryChooserModelClient
    {

        /**
         * Sets the {@link EntryChooserModel}.
         *
         * @param dataModel The model.
         */
        public void setEntryChooserModel(EntryChooserModel dataModel);
    }

    /**
     * Defines a sorter that is responsible for sorting the entries
     * based on the provided historical choices and an intent.
     */
    public interface EntrySorter
    {

        /**
         * Sorts the <code>entries</code> in descending order of relevance
         * based on previous history and an intent.
         *
         * @param intent The {@link Intent}.
         * @param entries Entries to be sorted.
         * @param historicalRecords Historical records.
         */
        // This cannot be done by a simple comparator since an Entry weight
        // is computed from history. Note that Entry implements Comparable.
        public void sort(Intent intent, List<Entry> entries,
                List<HistoricalRecord> historicalRecords);
    }

    /**
     * Listener for choosing an entry.
     */
    public interface OnChooseEntryListener
    {

        /**
         * Called when an entry has been chosen. The client can decide whether
         * an entry can be chosen and if so the caller of
         * {@link EntryChooserModel#chooseEntry} will receive and {@link Intent}
         * for launching it.
         * <p>
         * <strong>Note:</strong> Modifying the intent is not permitted and
         *     any changes to the latter will be ignored.
         * </p>
         *
         * @param host The listener's host model.
         * @param intent The intent for launching the chosen entry.
         * @return Whether the intent is handled and should not be delivered to clients.
         *
         * @see EntryChooserModel#chooseEntry
         */
        public boolean onChooseEntry(EntryChooserModel host, Intent intent);
    }

    /**
     * Flag for selecting debug mode.
     */
    private static final boolean DEBUG = false;

    /**
     * Tag used for logging.
     */
    private static final String LOG_TAG = EntryChooserModel.class.getSimpleName();

    /**
     * The root tag in the history file.
     */
    private static final String TAG_HISTORICAL_RECORDS = "historical-records";

    /**
     * The tag for a record in the history file.
     */
    private static final String TAG_HISTORICAL_RECORD = "historical-record";

    /**
     * Attribute for the entry id.
     */
    private static final String ATTRIBUTE_ENTRY_IDENTIFIER = "entry-id";

    /**
     * Attribute for the choice time.
     */
    private static final String ATTRIBUTE_TIME = "time";

    /**
     * Attribute for the choice weight.
     */
    private static final String ATTRIBUTE_WEIGHT = "weight";

    /**
     * The default name of the choice history file.
     */
    public static final String DEFAULT_HISTORY_FILE_NAME =
        "entry_choser_model_history.xml";

    /**
     * The default maximal length of the choice history.
     */
    public static final int DEFAULT_HISTORY_MAX_LENGTH = 50;

    /**
     * The amount with which to inflate a chosen entry when set as default.
     */
    private static final int DEFAULT_ENTRY_INFLATION = 5;

    /**
     * Default weight for a choice record.
     */
    private static final float DEFAULT_HISTORICAL_RECORD_WEIGHT = 1.0f;

    /**
     * The extension of the history file.
     */
    private static final String HISTORY_FILE_EXTENSION = ".xml";

    /**
     * An invalid item index.
     */
    private static final int INVALID_INDEX = -1;

    /**
     * Lock to guard the model registry.
     */
    private static final Object sRegistryLock = new Object();

    /**
     * This the registry for data models.
     */
    private static final Map<String, EntryChooserModel> sDataModelRegistry =
        new HashMap<String, EntryChooserModel>();

    /**
     * Lock for synchronizing on this instance.
     */
    private final Object mInstanceLock = new Object();

    /**
     * List of entries that will be displayed.
     */
    private final List<Entry> mEntries = new ArrayList<Entry>();

    /**
     * List with historical choice records.
     */
    private final List<HistoricalRecord> mHistoricalRecords = new ArrayList<HistoricalRecord>();

    /**
     * Context for accessing resources.
     */
    private final Context mContext;

    /**
     * The name of the history file that backs this model.
     */
    private final String mHistoryFileName;

    /**
     * The intent for which a activity is being chosen.
     */
    private Intent mIntent;

    /**
     * The sorter for ordering entries based on intent and past choices.
     */
    private EntrySorter mEntrySorter = new DefaultSorter();

    /**
     * The maximal length of the choice history.
     */
    private int mHistoryMaxSize = DEFAULT_HISTORY_MAX_LENGTH;

    /**
     * Flag whether choice history can be read. In general many clients can
     * share the same data model and {@link #readHistoricalData()} may be called
     * by arbitrary of them any number of times. Therefore, this class guarantees
     * that the very first read succeeds and subsequent reads can be performed
     * only after a call to {@link #persistHistoricalData()} followed by change
     * of the share records.
     */
    private boolean mCanReadHistoricalData = true;

    /**
     * Flag whether the choice history was read. This is used to enforce that
     * before calling {@link #persistHistoricalData()} a call to
     * {@link #persistHistoricalData()} has been made. This aims to avoid a
     * scenario in which a choice history file exits, it is not read yet and
     * it is overwritten. Note that always all historical records are read in
     * full and the file is rewritten. This is necessary since we need to
     * purge old records that are outside of the sliding window of past choices.
     */
    private boolean mReadShareHistoryCalled = false;

    /**
     * Flag whether the choice records have changed. In general many clients can
     * share the same data model and {@link #persistHistoricalData()} may be called
     * by arbitrary of them any number of times. Therefore, this class guarantees
     * that choice history will be persisted only if it has changed.
     */
    private boolean mHistoricalRecordsChanged = true;

    /**
     * Hander for scheduling work on client tread.
     */
    private final Handler mHandler = new Handler();

    /**
     * Policy for controlling how the model handles chosen entries.
     */
    private OnChooseEntryListener mEntryChoserModelPolicy;

    private final List<Entry> prependedEntries = new ArrayList<Entry>();
    private final List<Entry> additionalEntries = new ArrayList<Entry>();

    /**
     * Gets the data model backed by the contents of the provided file with historical data.
     * Note that only one data model is backed by a given file, thus multiple calls with
     * the same file name will return the same model instance. If no such instance is present
     * it is created.
     * <p>
     * <strong>Note:</strong> To use the default historical data file clients should explicitly
     * pass as file name {@link #DEFAULT_HISTORY_FILE_NAME}. If no persistence of the choice
     * history is desired clients should pass <code>null</code> for the file name. In such
     * case a new model is returned for each invocation.
     * </p>
     *
     * <p>
     * <strong>Always use difference historical data files for semantically different actions.
     * For example, sharing is different from importing.</strong>
     * </p>
     *
     * @param context Context for loading resources.
     * @param historyFileName File name with choice history, <code>null</code>
     *        if the model should not be backed by a file. In this case the entries
     *        will be ordered only by data from the current session.
     *
     * @return The model.
     */
    public static EntryChooserModel get(Context context, String historyFileName) {
        synchronized (sRegistryLock) {
            EntryChooserModel dataModel = sDataModelRegistry.get(historyFileName);
            if (dataModel == null) {
                dataModel = new EntryChooserModel(context, historyFileName);
                sDataModelRegistry.put(historyFileName, dataModel);
            }
            dataModel.readHistoricalData();
            return dataModel;
        }
    }

    /**
     * Creates a new instance.
     *
     * @param context Context for loading resources.
     * @param historyFileName The history XML file.
     */
    private EntryChooserModel(Context context, String historyFileName) {
        mContext = context.getApplicationContext();
        if (!TextUtils.isEmpty(historyFileName)
                && !historyFileName.endsWith(HISTORY_FILE_EXTENSION)) {
            mHistoryFileName = historyFileName + HISTORY_FILE_EXTENSION;
        } else {
            mHistoryFileName = historyFileName;
        }
    }

    /**
     * Sets an intent for which to choose an activity.
     * <p>
     * <strong>Note:</strong> Clients must set only semantically similar
     * intents for each data model.
     * <p>
     *
     * @param intent The intent.
     */
    public void setIntent(Intent intent) {
        synchronized (mInstanceLock) {
            if (mIntent == intent) {
                return;
            }
            mIntent = intent;
            loadEntriesLocked();
        }
    }

    /**
     * Gets the intent for which an activity is being chosen.
     *
     * @return The intent.
     */
    public Intent getIntent() {
        synchronized (mInstanceLock) {
            return mIntent;
        }
    }

    /**
     * Allows custom entries to be added <strong>before</strong> the normal entries which are resolved via the share intent.<br />
     * This method <strong>MUST</strong> be called before calling {@link EntryChooserModel#setIntent(android.content.Intent)}.</br>
     * <strong>Note that this will clear any existing prepended entries set before.</strong>
     * @param customEntries The entries to prepend.
     */
    public void setPrependedEntries(Entry... customEntries)
    {
        prependedEntries.clear();
        prependedEntries.addAll(Arrays.asList(customEntries));
    }

    /**
      * Allows custom entries to be added alongside the normal entries which are resolved via the share intent.<br />
      * This method <strong>MUST</strong> be called before calling {@link ShareActionProvider#setShareIntent(android.content.Intent)}.</br>
      * <strong>Note that this will clear any existing additional entries set before.</strong>
      * @param customEntries The entries to add.
      */
    public void setAdditionalEntries(Entry... customEntries)
    {
        additionalEntries.clear();
        additionalEntries.addAll(Arrays.asList(customEntries));
    }

    /**
     * Gets the total number of entries including custom ones.
     *
     * @return The entry count including custom entries.
     *
     * @see #setIntent(Intent) #appendCustomEntries #prependCustomEntries
     */
    public int getEntryCount() {
        synchronized (mInstanceLock) {
            return mEntries.size();
        }
    }

    /**
     * Gets the number of activities that can handle the intent. (the entries without the custom ones)
     *
     * @return The activity count.
     *
     * @see #setIntent(Intent)
     */
    public int getResolvedActivitiesEntryCount() {
        synchronized (mInstanceLock) {
            return mEntries.size() - prependedEntries.size() - additionalEntries.size();
        }
    }

    /**
     * Gets an entry at a given index.
     *
     * @return The entry.
     *
     * @see Entry
     * @see #setIntent(Intent)
     */
    public Entry getEntry(int index) {
        synchronized (mInstanceLock) {
            return mEntries.get(index);
        }
    }

    /**
     * Gets the index of a the given entry.
     *
     * @param entry The entry index.
     *
     * @return The index if found, -1 otherwise.
     */
    public int getEntryIndex(Entry entry) {
        List<Entry> entries = mEntries;
        final int entryCount = entries.size();
        for (int i = 0; i < entryCount; i++) {
            Entry currentEntry = entries.get(i);
            if (currentEntry.equals(entry)) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    /**
     * Chooses an entry to handle the current intent. This will result in
     * adding a historical record for that action and construct intent with
     * its component name set such that it can be immediately started by the
     * client.
     * <p>
     * <strong>Note:</strong> By calling this method the client guarantees
     * that the returned intent will be started. This intent is returned to
     * the client solely to let additional customization before the start.
     * </p>
     *
     * @return An {@link Intent} for launching the entry or null if the
     *         policy has consumed the intent.
     *
     * @see HistoricalRecord
     * @see EntryChooserModel.OnChooseEntryListener
     */
    public Intent chooseEntry(int index) {
        Entry chosenEntry = mEntries.get(index);


        Intent choiceIntent = chosenEntry.getIntent();

        if (mEntryChoserModelPolicy != null) {
            // Do not allow the policy to change the intent.
            Intent choiceIntentCopy = new Intent(choiceIntent);
            final boolean handled = mEntryChoserModelPolicy.onChooseEntry(this,
                    choiceIntentCopy);
            if (handled) {
                return null;
            }
        }

        HistoricalRecord historicalRecord = new HistoricalRecord(chosenEntry.getIdentifier(),
                System.currentTimeMillis(), DEFAULT_HISTORICAL_RECORD_WEIGHT);
        addHisoricalRecord(historicalRecord);

        return choiceIntent;
    }

    /**
     * Sets the listener for choosing an entry.
     *
     * @param listener The listener.
     */
    public void setOnChooseEntryListener(OnChooseEntryListener listener) {
        mEntryChoserModelPolicy = listener;
    }

    /**
     * Gets the default entry, The default entry is defined as the one
     * with highest rank i.e. the first one in the list of entries that can
     * handle the intent.
     *
     * @return The default entry, <code>null</code> id not entries.
     *
     * @see #getEntry
     */
    public Entry getDefaultEntry() {
        synchronized (mInstanceLock) {
            if (!mEntries.isEmpty()) {
                return mEntries.get(0);
            }
        }
        return null;
    }

    /**
     * Sets the default entry. The default entry is set by adding a
     * historical record with weight high enough that this entry will
     * become the highest ranked. Such a strategy guarantees that the default
     * will eventually change if not used. Also the weight of the record for
     * setting a default is inflated with a constant amount to guarantee that
     * it will stay as default for awhile.
     *
     * @param index The index of the entry to set as default.
     */
    public void setDefaultEntry(int index) {
        Entry newDefaultEntry = mEntries.get(index);
        Entry oldDefaultEntry = mEntries.get(0);

        final float weight;
        if (oldDefaultEntry != null) {
            // Add a record with weight enough to boost the chosen at the top.
            weight = oldDefaultEntry.getWeight() - newDefaultEntry.getWeight()
                + DEFAULT_ENTRY_INFLATION;
        } else {
            weight = DEFAULT_HISTORICAL_RECORD_WEIGHT;
        }

        HistoricalRecord historicalRecord = new HistoricalRecord(newDefaultEntry.getIdentifier(),
                System.currentTimeMillis(), weight);
        addHisoricalRecord(historicalRecord);
    }

    /**
     * Reads the history data from the backing file if the latter
     * was provided. Calling this method more than once before a call
     * to {@link #persistHistoricalData()} has been made has no effect.
     * <p>
     * <strong>Note:</strong> Historical data is read asynchronously and
     *       as soon as the reading is completed any registered
     *       {@link android.database.DataSetObserver}s will be notified. Also no historical
     *       data is read until this method is invoked.
     * <p>
     */
    private void readHistoricalData() {
        synchronized (mInstanceLock) {
            if (!mCanReadHistoricalData || !mHistoricalRecordsChanged) {
                return;
            }
            mCanReadHistoricalData = false;
            mReadShareHistoryCalled = true;
            if (!TextUtils.isEmpty(mHistoryFileName)) {
                /*AsyncTask.*/SERIAL_EXECUTOR.execute(new HistoryLoader());
            }
        }
    }

    private static final Executor SERIAL_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Persists the history data to the backing file if the latter
     * was provided. Calling this method before a call to {@link #readHistoricalData()}
     * throws an exception. Calling this method more than one without choosing an
     * entry has not effect.
     *
     * @throws IllegalStateException If this method is called before a call to
     *         {@link #readHistoricalData()}.
     */
    private void persistHistoricalData() {
        synchronized (mInstanceLock) {
            if (!mReadShareHistoryCalled) {
                throw new IllegalStateException("No preceding call to #readHistoricalData");
            }
            if (!mHistoricalRecordsChanged) {
                return;
            }
            mHistoricalRecordsChanged = false;
            mCanReadHistoricalData = true;
            if (!TextUtils.isEmpty(mHistoryFileName)) {
                /*AsyncTask.*/SERIAL_EXECUTOR.execute(new HistoryPersister());
            }
        }
    }

    /**
     * Sets the sorter for ordering entries based on historical data and an intent.
     *
     * @param entrySorter The sorter.
     *
     * @see EntryChooserModel.EntrySorter
     */
    public void setEntrySorter(EntrySorter entrySorter) {
        synchronized (mInstanceLock) {
            if (mEntrySorter == entrySorter) {
                return;
            }
            mEntrySorter = entrySorter;
            sortEntries();
        }
    }

    /**
     * Sorts the entries based on history and an intent. If
     * a sorter is not specified, then a default implementation is used.
     *
     * @see #setEntrySorter
     */
    private void sortEntries() {
        synchronized (mInstanceLock) {
            if (mEntrySorter != null && !mEntries.isEmpty()) {
                mEntrySorter.sort(mIntent, mEntries,
                        Collections.unmodifiableList(mHistoricalRecords));
                notifyChanged();
            }
        }
    }

    /**
     * Sets the maximal size of the historical data. Defaults to
     * {@link #DEFAULT_HISTORY_MAX_LENGTH}
     * <p>
     *   <strong>Note:</strong> Setting this property will immediately
     *   enforce the specified max history size by dropping enough old
     *   historical records to enforce the desired size. Thus, any
     *   records that exceed the history size will be discarded and
     *   irreversibly lost.
     * </p>
     *
     * @param historyMaxSize The max history size.
     */
    public void setHistoryMaxSize(int historyMaxSize) {
        synchronized (mInstanceLock) {
            if (mHistoryMaxSize == historyMaxSize) {
                return;
            }
            mHistoryMaxSize = historyMaxSize;
            pruneExcessiveHistoricalRecordsLocked();
            sortEntries();
        }
    }

    /**
     * Gets the history max size.
     *
     * @return The history max size.
     */
    public int getHistoryMaxSize() {
        synchronized (mInstanceLock) {
            return mHistoryMaxSize;
        }
    }

    /**
     * Gets the history size.
     *
     * @return The history size.
     */
    public int getHistorySize() {
        synchronized (mInstanceLock) {
            return mHistoricalRecords.size();
        }
    }

    /**
     * Adds a historical record.
     *
     * @param historicalRecord The record to add.
     * @return True if the record was added.
     */
    private boolean addHisoricalRecord(HistoricalRecord historicalRecord) {
        synchronized (mInstanceLock) {
            final boolean added = mHistoricalRecords.add(historicalRecord);
            if (added) {
                mHistoricalRecordsChanged = true;
                pruneExcessiveHistoricalRecordsLocked();
                persistHistoricalData();
                sortEntries();
            }
            return added;
        }
    }

    /**
     * Prunes older excessive records to guarantee {@link #mHistoryMaxSize}.
     */
    private void pruneExcessiveHistoricalRecordsLocked() {
        List<HistoricalRecord> choiceRecords = mHistoricalRecords;
        final int pruneCount = choiceRecords.size() - mHistoryMaxSize;
        if (pruneCount <= 0) {
            return;
        }
        mHistoricalRecordsChanged = true;
        for (int i = 0; i < pruneCount; i++) {
            HistoricalRecord prunedRecord = choiceRecords.remove(0);
            if (DEBUG) {
                Log.i(LOG_TAG, "Pruned: " + prunedRecord);
            }
        }
    }

    /**
     * Loads the entries.
     */
    private void loadEntriesLocked() {
        mEntries.clear();
        if (!prependedEntries.isEmpty()) {
            for (Entry prependedEntry : prependedEntries) {
                mEntries.add(prependedEntry);
            }
        }
        if (mIntent != null) {
            List<ResolveInfo> resolveInfos =
                mContext.getPackageManager().queryIntentActivities(mIntent, 0);
            final int resolveInfoCount = resolveInfos.size();
            for (int i = 0; i < resolveInfoCount; i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                mEntries.add(new ActivityResolveInfo(resolveInfo, mContext.getPackageManager()));
            }
        }
        if (!additionalEntries.isEmpty()) {
            for (Entry appendedEntry : additionalEntries) {
                mEntries.add(appendedEntry);
            }
        }
        if (mEntries.isEmpty()) {
            notifyChanged();
        } else {
            sortEntries();
        }
    }

    /**
     * Represents a record in the history.
     */
    public final static class HistoricalRecord {

        /**
         * A unique identifier for the entry.
         */
        public final String uniqueIdentifier;

        /**
         * The choice time.
         */
        public final long time;

        /**
         * The record weight.
         */
        public final float weight;

        /**
         * Creates a new instance.
         *
         * @param uniqueIdentifier A unique identifier for the entry.
         * @param time The time the entry was chosen.
         * @param weight The weight of the record.
         */
        public HistoricalRecord(String uniqueIdentifier, long time, float weight) {
            this.uniqueIdentifier = uniqueIdentifier;
            this.time = time;
            this.weight = weight;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((uniqueIdentifier == null) ? 0 : uniqueIdentifier.hashCode());
            result = prime * result + (int) (time ^ (time >>> 32));
            result = prime * result + Float.floatToIntBits(weight);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            HistoricalRecord other = (HistoricalRecord) obj;
            if (uniqueIdentifier == null) {
                if (other.uniqueIdentifier != null) {
                    return false;
                }
            } else if (!uniqueIdentifier.equals(other.uniqueIdentifier)) {
                return false;
            }
            if (time != other.time) {
                return false;
            }
            if (Float.floatToIntBits(weight) != Float.floatToIntBits(other.weight)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            builder.append("; uniqueIdentifier:").append(uniqueIdentifier);
            builder.append("; time:").append(time);
            builder.append("; weight:").append(new BigDecimal(weight));
            builder.append("]");
            return builder.toString();
        }
    }

    /**
     * Represents an activity.
     */
    public final class ActivityResolveInfo implements Entry {
        /**
         * The {@link ResolveInfo} of the activity.
         */
        public final ResolveInfo resolveInfo;

        /**
         * Weight of the activity. Useful for sorting.
         */
        private float weight;
        private final CharSequence label;
        private final Drawable icon;
        private final EntryHasherAndComparator<ActivityResolveInfo> hasher;

        public float getWeight()
        {
            return weight;
        }

        private ComponentName getComponentName()
        {
            return new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }

        public void setWeight(float weight)
        {
            this.weight = weight;
        }

        @Override
        public String getIdentifier()
        {
            return resolveInfo.activityInfo.packageName;
        }

        @Override
        public CharSequence getLabel()
        {
            return label;
        }

        @Override
        public Drawable getIcon()
        {
            return icon;
        }

        @Override
        public Intent getIntent()
        {
            Intent intent = new Intent(mIntent);
            ComponentName chosenName = getComponentName();
            intent.setComponent(chosenName);
            return intent;
        }

        /**
         * Creates a new instance.
         *
         * @param resolveInfo activity {@link android.content.pm.ResolveInfo}.
         * @param packageManager
         */
        public ActivityResolveInfo(ResolveInfo resolveInfo, PackageManager packageManager) {
            this.resolveInfo = resolveInfo;
            label = resolveInfo.loadLabel(packageManager);
            icon = resolveInfo.loadIcon(packageManager);

            hasher = new EntryHasherAndComparator<ActivityResolveInfo>(this);
        }

        @Override
        public int hashCode() {
            return hasher.computeHashCode();
        }

        @Override
        public boolean equals(Object obj) {
           return hasher.isEqualTo(obj);
        }


        @Override
        public int compareTo(Entry entry)
        {
            return hasher.compareToEntry(entry);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            builder.append("resolveInfo:").append(resolveInfo.toString());
            builder.append("; weight:").append(new BigDecimal(weight));
            builder.append("]");
            return builder.toString();
        }
    }

    /**
     * Default entry sorter implementation.
     */
    private final class DefaultSorter implements EntrySorter
    {
        private static final float WEIGHT_DECAY_COEFFICIENT = 0.95f;

        private final Map<String, Entry> mUniqueIdToEntryMap =
            new HashMap<String, Entry>();

        public void sort(Intent intent, List<Entry> entries,
                List<HistoricalRecord> historicalRecords) {
            Map<String, Entry> uniqueIdNameToEntryMap =
                    mUniqueIdToEntryMap;
            uniqueIdNameToEntryMap.clear();

            final int entryCount = entries.size();
            for (int i = 0; i < entryCount; i++) {
                Entry entry = entries.get(i);
                entry.setWeight(0.0f);
                String packageName = entry.getIdentifier();
                uniqueIdNameToEntryMap.put(packageName, entry);
            }

            final int lastShareIndex = historicalRecords.size() - 1;
            float nextRecordWeight = 1;
            for (int i = lastShareIndex; i >= 0; i--) {
                HistoricalRecord historicalRecord = historicalRecords.get(i);
                String uniqueIdentifier = historicalRecord.uniqueIdentifier;
                Entry entry = uniqueIdNameToEntryMap.get(uniqueIdentifier);
                if (entry != null) {
                    entry.setWeight(entry.getWeight() + historicalRecord.weight * nextRecordWeight);
                    nextRecordWeight = nextRecordWeight * WEIGHT_DECAY_COEFFICIENT;
                }
            }

//            Collections.sort(entries);
//            float largeWeight = Float.MAX_VALUE;
//            Entry firstEntry = null;
//            if (entries.size() > 0)
//            {
//                firstEntry = entries.get(0);
//                firstEntry.setWeight(largeWeight);
//                largeWeight--;
//            }
//            for (Entry prependedEntry : prependedEntries)
//            {
//                // yes, we need reference equality here. If it is the first entry, it will have already had its weight
//                // set.
//                if (prependedEntry != firstEntry)
//                {
//                    prependedEntry.setWeight(largeWeight);
//                    largeWeight--;
//                }
//            }

            entries.removeAll(prependedEntries);
            float largestWeightOfNormalEntries = PrependedItemsOnTopComparator.findLargestWeightOf(entries);
            entries.addAll(prependedEntries);
            Collections.sort(entries, new PrependedItemsOnTopComparator(largestWeightOfNormalEntries, prependedEntries));

            if (DEBUG) {
                for (int i = 0; i < entryCount; i++) {
                    Log.i(LOG_TAG, "Sorted: " + entries.get(i));
                }
            }
        }
    }

    /**
     * Command for reading the historical records from a file off the UI thread.
     */
    private final class HistoryLoader implements Runnable {

       public void run() {
            FileInputStream fis = null;
            try {
                fis = mContext.openFileInput(mHistoryFileName);
            } catch (FileNotFoundException fnfe) {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Could not open historical records file: " + mHistoryFileName);
                }
                return;
            }
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(fis, null);

                int type = XmlPullParser.START_DOCUMENT;
                while (type != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {
                    type = parser.next();
                }

                if (!TAG_HISTORICAL_RECORDS.equals(parser.getName())) {
                    throw new XmlPullParserException("Share records file does not start with "
                            + TAG_HISTORICAL_RECORDS + " tag.");
                }

                List<HistoricalRecord> readRecords = new ArrayList<HistoricalRecord>();

                while (true) {
                    type = parser.next();
                    if (type == XmlPullParser.END_DOCUMENT) {
                        break;
                    }
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }
                    String nodeName = parser.getName();
                    if (!TAG_HISTORICAL_RECORD.equals(nodeName)) {
                        throw new XmlPullParserException("Share records file not well-formed.");
                    }

                    String entryIdentifier = parser.getAttributeValue(null, ATTRIBUTE_ENTRY_IDENTIFIER);
                    final long time =
                        Long.parseLong(parser.getAttributeValue(null, ATTRIBUTE_TIME));
                    final float weight =
                        Float.parseFloat(parser.getAttributeValue(null, ATTRIBUTE_WEIGHT));

                    HistoricalRecord readRecord = new HistoricalRecord(entryIdentifier, time,
                            weight);
                    readRecords.add(readRecord);

                    if (DEBUG) {
                        Log.i(LOG_TAG, "Read " + readRecord.toString());
                    }
                }

                if (DEBUG) {
                    Log.i(LOG_TAG, "Read " + readRecords.size() + " historical records.");
                }

                synchronized (mInstanceLock) {
                    Set<HistoricalRecord> uniqueShareRecords =
                        new LinkedHashSet<HistoricalRecord>(readRecords);

                    // Make sure no duplicates. Example: Read a file with
                    // one record, add one record, persist the two records,
                    // add a record, read the persisted records - the
                    // read two records should not be added again.
                    List<HistoricalRecord> historicalRecords = mHistoricalRecords;
                    final int historicalRecordsCount = historicalRecords.size();
                    for (int i = historicalRecordsCount - 1; i >= 0; i--) {
                        HistoricalRecord historicalRecord = historicalRecords.get(i);
                        uniqueShareRecords.add(historicalRecord);
                    }

                    if (historicalRecords.size() == uniqueShareRecords.size()) {
                        return;
                    }

                    // Make sure the oldest records go to the end.
                    historicalRecords.clear();
                    historicalRecords.addAll(uniqueShareRecords);

                    mHistoricalRecordsChanged = true;

                    // Do this on the client thread since the client may be on the UI
                    // thread, wait for data changes which happen during sorting, and
                    // perform UI modification based on the data change.
                    mHandler.post(new Runnable() {
                        public void run() {
                            pruneExcessiveHistoricalRecordsLocked();
                            sortEntries();
                        }
                    });
                }
            } catch (XmlPullParserException xppe) {
                Log.e(LOG_TAG, "Error reading historical recrod file: " + mHistoryFileName, xppe);
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Error reading historical recrod file: " + mHistoryFileName, ioe);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException ioe) {
                        /* ignore */
                    }
                }
            }
        }
    }

    /**
     * Command for persisting the historical records to a file off the UI thread.
     */
    private final class HistoryPersister implements Runnable {

        public void run() {
            FileOutputStream fos = null;
            List<HistoricalRecord> records = null;

            synchronized (mInstanceLock) {
                records = new ArrayList<HistoricalRecord>(mHistoricalRecords);
            }

            try {
                fos = mContext.openFileOutput(mHistoryFileName, Context.MODE_PRIVATE);
            } catch (FileNotFoundException fnfe) {
                Log.e(LOG_TAG, "Error writing historical recrod file: " + mHistoryFileName, fnfe);
                return;
            }

            XmlSerializer serializer = Xml.newSerializer();

            try {
                serializer.setOutput(fos, null);
                serializer.startDocument("UTF-8", true);
                serializer.startTag(null, TAG_HISTORICAL_RECORDS);

                final int recordCount = records.size();
                for (int i = 0; i < recordCount; i++) {
                    HistoricalRecord record = records.remove(0);
                    serializer.startTag(null, TAG_HISTORICAL_RECORD);
                    serializer.attribute(null, ATTRIBUTE_ENTRY_IDENTIFIER, record.uniqueIdentifier);
                    serializer.attribute(null, ATTRIBUTE_TIME, String.valueOf(record.time));
                    serializer.attribute(null, ATTRIBUTE_WEIGHT, String.valueOf(record.weight));
                    serializer.endTag(null, TAG_HISTORICAL_RECORD);
                    if (DEBUG) {
                        Log.i(LOG_TAG, "Wrote " + record.toString());
                    }
                }

                serializer.endTag(null, TAG_HISTORICAL_RECORDS);
                serializer.endDocument();

                if (DEBUG) {
                    Log.i(LOG_TAG, "Wrote " + recordCount + " historical records.");
                }
            } catch (IllegalArgumentException iae) {
                Log.e(LOG_TAG, "Error writing historical recrod file: " + mHistoryFileName, iae);
            } catch (IllegalStateException ise) {
                Log.e(LOG_TAG, "Error writing historical recrod file: " + mHistoryFileName, ise);
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Error writing historical recrod file: " + mHistoryFileName, ioe);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        /* ignore */
                    }
                }
            }
        }
    }
}
