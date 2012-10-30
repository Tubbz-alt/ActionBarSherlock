package com.actionbarsherlock.widget;

/**
 * This class provides methods to compute the haschode of an entry and its equals method.
 */
public class EntryHasherAndComparator<T extends Entry> {

    private T entry;

    public EntryHasherAndComparator(T entry) {
        this.entry = entry;
    }

    public int computeHashCode() {
        return 31 + Float.floatToIntBits(entry.getWeight());
    }

    public boolean isEqualTo(Object otherObj) {
        if (entry == otherObj) {
            return true;
        }
        if (otherObj == null) {
            return false;
        }
        if (!(otherObj instanceof Entry)) {
            return false;
        }
        Entry otherEntry = (Entry) otherObj;
        if (!entry.getIdentifier().equals(otherEntry.getIdentifier()))
        {
            return false;
        }
        if (Float.floatToIntBits(entry.getWeight()) != Float.floatToIntBits(otherEntry.getWeight())) {
            return false;
        }
        return true;
    }


    public int compareToEntry(Entry other) {
        return Float.floatToIntBits(other.getWeight()) - Float.floatToIntBits(entry.getWeight());
    }
}
