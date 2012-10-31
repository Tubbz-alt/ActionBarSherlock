package com.actionbarsherlock.widget;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class PrependedItemsOnTopComparatorTest {

    public static final float SMALL_WEIGHT = 10;
    public static final float MEDIUM_WEIGHT = 50;
    public static final float LARGE_WEIGHT = 100;
    public static final float VERY_LARGE_WEIGHT = 1000;
    public static final float IMMENSE_WEIGHT = 10000;
    public static final float VERY_IMMENSE_WEIGHT = 100000000;

    private PrependedItemsOnTopComparator comparator;
    private Entry normalEntryImmense = makeEntryWithWeight(IMMENSE_WEIGHT);
    private Entry normalEntryVeryLarge = makeEntryWithWeight(VERY_LARGE_WEIGHT);
    private Entry normalEntryLarge = makeEntryWithWeight(LARGE_WEIGHT);
    private Entry normalEntryMedium = makeEntryWithWeight(MEDIUM_WEIGHT);
    private Entry normalEntrySmall = makeEntryWithWeight(SMALL_WEIGHT);

    private Entry specialEntryVeryImmense = makeEntryWithWeight(VERY_IMMENSE_WEIGHT);
    private Entry specialEntryLarge = makeEntryWithWeight(LARGE_WEIGHT);
    private Entry specialEntryMedium = makeEntryWithWeight(MEDIUM_WEIGHT);
    private Entry specialEntrySmall = makeEntryWithWeight(SMALL_WEIGHT);

    @Before
    public void setUp() throws Exception {
        comparator = new PrependedItemsOnTopComparator(
                largestWeightOfNormalEntries(),
                specialEntrySmall,
                specialEntryMedium,
                specialEntryLarge);
    }

    @Test
    public void _findsLargestWeightOfEntries()
    {
        float largestWeight = largestWeightOfNormalEntries();

        assertThat(largestWeight, equalTo(IMMENSE_WEIGHT));
    }

    @Test
    public void _comparesTwoNormalEntriesAsTheyWouldCompareThemselves()
    {
        int selfComparisonResult = normalEntryLarge.compareTo(normalEntrySmall);
        int comparisonResult = comparator.compare(normalEntryLarge, normalEntrySmall);

        assertThat(comparisonResult, equalTo(selfComparisonResult));
    }

    @Test
    public void _alwaysDeclaresSpecialEntryToBeGreaterThanNormalEntryIfNormalEntryIsNotHighest()
    {
        int comparison1Result = comparator.compare(normalEntryVeryLarge, specialEntryLarge);
        int comparison2Result = comparator.compare(specialEntryLarge, normalEntryVeryLarge);

        assertThat(comparison1Result, greaterThan(0));
        assertThat(comparison2Result, lessThan(0));
    }

    @Test
    public void _declaresHighestNormalEntryGreaterThanSpecialEntryIfWeightGreaterThanAllSpecialEntries()
    {
        int comparison1Result = comparator.compare(normalEntryImmense, specialEntryLarge);
        int comparison2Result = comparator.compare(specialEntryLarge, normalEntryImmense);

        assertThat(comparison1Result, lessThan(0));
        assertThat(comparison2Result, greaterThan(0));
    }

    @Test
    public void _declaresHighestNormalEntrySmallerThanSpecialEntryIfWeightNotGreaterThanAllSpecialEntries()
    {
        comparator = new PrependedItemsOnTopComparator(
                        largestWeightOfNormalEntries(),
                        specialEntrySmall,
                        specialEntryMedium,
                        specialEntryLarge,
                        specialEntryVeryImmense);
        int comparison1Result = comparator.compare(normalEntryImmense, specialEntrySmall);
        int comparison2Result = comparator.compare(specialEntryLarge, normalEntryImmense);

        assertThat(comparison1Result, greaterThan(0));
        assertThat(comparison2Result, lessThan(0));
    }


    // ----------------------------------------- Helper methods --------------------------------------------------------

    private float largestWeightOfNormalEntries() {
        return PrependedItemsOnTopComparator.findLargestWeightOf(
                normalEntrySmall,
                normalEntryMedium,
                normalEntryLarge,
                normalEntryImmense);
    }

    private static Entry makeEntryWithWeight(final float weight) {
        return new Entry() {
            EntryHasherAndComparator<Entry> hasherAndComparator = new EntryHasherAndComparator<Entry>(this);
            @Override
            public float getWeight() {
                return weight;
            }

            @Override
            public void setWeight(float weight) {}

            @Override
            public String getIdentifier() {
                return null;
            }

            @Override
            public CharSequence getLabel() {
                return null;
            }

            @Override
            public String getIconUrl() {
                return null;
            }

            @Override
            public Drawable getIcon() {
                return null;
            }

            @Override
            public Intent getIntent() {
                return null;
            }

            @Override
            public int getFallbackIconResId() {
                return 0;
            }

            @Override
            public String getSubLabel() {
                return null;
            }

            @Override
            public int compareTo(Entry entry) {
                return hasherAndComparator.compareToEntry(entry);
            }
        };
    }
}
