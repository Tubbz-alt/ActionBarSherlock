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

    public boolean isEqualTo(Object obj) {
        if (entry == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Entry)) {
            return false;
        }
        Entry other = (Entry) obj;
        if (!entry.getIdentifier().equals(other.getIdentifier()))
        {
            return false;
        }
        if (Float.floatToIntBits(entry.getWeight()) != Float.floatToIntBits(other.getWeight())) {
            return false;
        }
        return true;
    }


    public int compareToEntry(Entry other) {
        return Float.floatToIntBits(other.getWeight()) - Float.floatToIntBits(entry.getWeight());
    }
}
