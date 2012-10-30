package com.actionbarsherlock.widget;

/**
 * This class is an abstract implementation of the {@link Entry} interface that provides implementation of the weight
 * and the calculation of the {@link #equals(Object)}  and {@link #hashCode()}  methods. Most classes that want to
 * implement the {@link Entry} interface will instead extend this class since it provides the expected behaviour.<br />
 * <br/>
 * They should implement the interface directly if they want to specifically provide a different behaviour.
 */
public abstract class DefaultAbstractEntry implements Entry
{
    /**
     * Weight of the entry. Useful for sorting.
     */
    private float weight;
    private final EntryHasherAndComparator<DefaultAbstractEntry> hasher = new EntryHasherAndComparator<DefaultAbstractEntry>(this);

    public float getWeight()
    {
        return weight;
    }

    public void setWeight(float weight)
    {
        this.weight = weight;
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
}
