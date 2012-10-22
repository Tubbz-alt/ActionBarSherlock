package com.actionbarsherlock.widget;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PrependedItemsOnTopComparator implements Comparator<Entry> {

    private List<Entry> entriesToBePutOnTop;
    private float largestNormalEntriesWeight;
    private float largestSpecialEntriesWeight;

    public PrependedItemsOnTopComparator(float largestNormalEntriesWeight, Collection<Entry> entriesToBePutOnTop) {
        this(largestNormalEntriesWeight, entriesToBePutOnTop.toArray(new Entry[entriesToBePutOnTop.size()]));
    }

    public PrependedItemsOnTopComparator(float largestNormalEntriesWeight, Entry... entriesToBePutOnTop) {
        this.largestNormalEntriesWeight = largestNormalEntriesWeight;
        this.entriesToBePutOnTop = Arrays.asList(entriesToBePutOnTop);
        largestSpecialEntriesWeight = findLargestWeightOf(entriesToBePutOnTop);
    }

    @Override
    public int compare(Entry entry1, Entry entry2) {
        if (entriesToBePutOnTop.contains(entry2) && !entriesToBePutOnTop.contains(entry1))
        {
            return entryHasHighestNormalEntryWeight(entry1)
                   && largestNormalEntriesWeight > largestSpecialEntriesWeight
                    ? -1
                    : 1;
        }
        if (entriesToBePutOnTop.contains(entry1) && !entriesToBePutOnTop.contains(entry2))
        {
            return entryHasHighestNormalEntryWeight(entry2)
                   && largestNormalEntriesWeight > largestSpecialEntriesWeight
                    ? 1
                    : -1;
        }
        return entry1.compareTo(entry2);
    }

    private boolean entryHasHighestNormalEntryWeight(Entry entry) {
        return entry.getWeight() == largestNormalEntriesWeight;
    }

    public static float findLargestWeightOf(List<Entry> entries) {
        return findLargestWeightOf(entries.toArray(new Entry[entries.size()]));
    }

    public static float findLargestWeightOf(Entry... entries) {
        float largestWeight = 0;
        for (Entry entry : entries)
        {
            float entryWeight = entry.getWeight();
            if (entryWeight > largestWeight)
            {
                largestWeight = entryWeight;
            }
        }
        return largestWeight;
    }
}
