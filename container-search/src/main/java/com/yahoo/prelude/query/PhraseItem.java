// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * A term which contains a phrase - a collection of word terms
 *
 * @author bratseth
 * @author havardpe
 */
public class PhraseItem extends CompositeIndexedItem {

    /** Whether this was explicitly written as a phrase using quotes by the user */
    private boolean explicit = false;

    /** Creates an empty phrase */
    public PhraseItem() {}

    /** Creates an empty phrase which will search the given index */
    public PhraseItem(String indexName) {
        setIndexName(indexName);
    }

    /** Creates a phrase containing the given words */
    public PhraseItem(String[] words) {
        for (int i = 0; i < words.length; i++) {
            addIndexedItem(new WordItem(words[i]));
        }
    }

    public ItemType getItemType() {
        return ItemType.PHRASE;
    }

    public String getName() {
        return "PHRASE";
    }

    public void setIndexName(String index) {
        super.setIndexName(index);
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            IndexedItem word = (IndexedItem) i.next();
            word.setIndexName(index);
        }
    }

    /**
     * Sets whether this was explicitly written as a phrase using quotes by the
     * user
     */
    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    /**
     * Returns whether this was explicitly written as a phrase using quotes by
     * the user Default is false
     */
    public boolean isExplicit() {
        return explicit;
    }

    private IndexedItem convertIntToWord(Item orig) {
        IntItem o = (IntItem) orig;
        return new WordItem(o.stringValue(), o.getIndexName(), o.isFromQuery());
    }

    /**
     * Adds subitem. The word will have its index name set to the index name of
     * this phrase. If the item is a word, it will simply be added, if the item
     * is a phrase, each of the words of the phrase will be added.
     *
     * @throws IllegalArgumentException
     *             if the given item is not a WordItem or PhraseItem
     */
    public void addItem(Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem || item instanceof WordAlternativesItem) {
            addIndexedItem((IndexedItem) item);
        } else if (item instanceof IntItem) {
            addIndexedItem(convertIntToWord(item));
        } else if (item instanceof PhraseItem) {
            PhraseItem phrase = (PhraseItem) item;

            for (Iterator<Item> i = phrase.getItemIterator(); i.hasNext();) {
                addIndexedItem((IndexedItem) i.next());
            }
        } else {
            throw new IllegalArgumentException("Can not add " + item
                    + " to a phrase");
        }
    }

    @Override
    public void addItem(int index, Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem) {
            addIndexedItem(index, (IndexedItem) item);
        } else if (item instanceof IntItem) {
            addIndexedItem(index, convertIntToWord(item));
        } else if (item instanceof PhraseItem) {
            PhraseItem phrase = (PhraseItem) item;

            for (Iterator<Item> i = phrase.getItemIterator(); i.hasNext();) {
                addIndexedItem(index++, (WordItem) i.next());
            }
        } else {
            throw new IllegalArgumentException("Can not add " + item
                    + " to a phrase");
        }
    }

    @Override
    public Item setItem(int index, Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem) {
            return setIndexedItem(index, (IndexedItem) item);
        } else if (item instanceof IntItem) {
            return setIndexedItem(index, convertIntToWord(item));
        } else if (item instanceof PhraseItem) {
            PhraseItem phrase = (PhraseItem) item;
            Iterator<Item> i = phrase.getItemIterator();
            // we assume we don't try to add empty phrases
            IndexedItem firstItem = (IndexedItem) i.next();
            Item toReturn = setIndexedItem(index++, firstItem);

            while (i.hasNext()) {
                addIndexedItem(index++, (IndexedItem) i.next());
            }
            return toReturn;
        } else {
            throw new IllegalArgumentException("Can not add " + item
                    + " to a phrase");
        }
    }

    private void addIndexedItem(IndexedItem word) {
        word.setIndexName(this.getIndexName());
        super.addItem((Item) word);
    }

    private void addIndexedItem(int index, IndexedItem word) {
        word.setIndexName(this.getIndexName());
        super.addItem(index, (Item) word);
    }

    private Item setIndexedItem(int index, IndexedItem word) {
        word.setIndexName(this.getIndexName());
        return super.setItem(index, (Item) word);
    }

    /**
     * Returns a subitem as a word item
     *
     * @param index
     *            the (0-base) index of the item to return
     * @throws IndexOutOfBoundsException
     *             if there is no subitem at index
     */
    public WordItem getWordItem(int index) {
        return (WordItem) getItem(index);
    }

    /**
     * Returns a subitem as a block item,
     *
     * @param index
     *            the (0-base) index of the item to return
     * @throws IndexOutOfBoundsException
     *             if there is no subitem at index
     */
    public BlockItem getBlockItem(int index) {
        return (BlockItem) getItem(index);
    }

    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
    }

    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        int itemCount = 1;

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item subitem = i.next();

            if (subitem instanceof PhraseSegmentItem) {
                PhraseSegmentItem seg = (PhraseSegmentItem) subitem;

                // "What encode does, minus what encodeThis does"
                itemCount += seg.encodeContent(buffer);
            } else {
                itemCount += subitem.encode(buffer);
            }
        }
        return itemCount;
    }

    /**
     * Returns false, no parenthezes for phrases
     */
    protected boolean shouldParenthize() {
        return false;
    }

    /** Phrase items uses a empty heading instead of "PHRASE " */
    protected void appendHeadingString(StringBuilder buffer) { }

    protected void appendBodyString(StringBuilder buffer) {
        appendIndexString(buffer);

        buffer.append("\"");
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item item = i.next();

            if (item instanceof WordItem) {
                WordItem wordItem = (WordItem) item;

                buffer.append(wordItem.getWord());
            } else {
                PhraseSegmentItem seg = (PhraseSegmentItem) item;

                seg.appendContentsString(buffer);
            }
            if (i.hasNext()) {
                buffer.append(" ");
            }
        }
        buffer.append("\"");
    }

    public String getIndexedString() {
        StringBuilder buf = new StringBuilder();

        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            IndexedItem indexedItem = (IndexedItem) i.next();

            buf.append(indexedItem.getIndexedString());
            if (i.hasNext()) {
                buf.append(' ');
            }
        }
        return buf.toString();
    }

    protected int encodingArity() {
        return getNumWords();
    }

    public int getNumWords() {
        int numWords = 0;

        for (Iterator<Item> j = getItemIterator(); j.hasNext();) {
            numWords += ((IndexedItem) j.next()).getNumWords();
        }
        return numWords;
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("explicit", explicit);
    }
}
