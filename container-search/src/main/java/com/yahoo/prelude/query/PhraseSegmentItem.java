// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;


/**
 * A term which contains a fixed length phrase, a collection of word terms,
 * resulting from a single segmentation operation.
 *
 * @author Steinar Knutsen
 */
public class PhraseSegmentItem extends IndexedSegmentItem {

    /** Whether this was explicitly written as a phrase using quotes by the user */
    private boolean explicit = false;

    /** Creates a phrase containing the same words and state (as pertinent) as the given SegmentAndItem. */
    public PhraseSegmentItem(AndSegmentItem andSegment) {
        super(andSegment.getRawWord(), andSegment.stringValue(), andSegment.isFromQuery(), andSegment.isStemmed(), andSegment.getOrigin());
        if (andSegment.getItemCount() > 0) {
            TermItem w = (TermItem) andSegment.getItem(0);
            setIndexName(w.getIndexName());
            for (Iterator<Item> i = andSegment.getItemIterator(); i.hasNext();) {
                TermItem word = (TermItem) i.next();
                addIndexedItem(word);
            }
        }
    }

    public PhraseSegmentItem(String rawWord, boolean isFromQuery, boolean stemmed) {
        super(rawWord, rawWord, isFromQuery, stemmed, null);
    }

    /**
     * Creates a phrase segment from strings
     *
     * @param rawWord the raw text as received in the request
     * @param current the normalized form of the raw text, or the raw text repeated if no normalized form is known
     * @param isFromQuery whether this originates in the request
     * @param stemmed whether this is stemmed
     */
    public PhraseSegmentItem(String rawWord, String current, boolean isFromQuery, boolean stemmed) {
        super(rawWord, current, isFromQuery, stemmed, null);
    }

    public PhraseSegmentItem(String rawWord, String current, boolean isFromQuery,
                             boolean stemmed, Substring substring) {
        super(rawWord, current, isFromQuery, stemmed, substring);
    }

    @Override
    public ItemType getItemType() {
        return ItemType.PHRASE;
    }

    @Override
    public String getName() {
        return "SPHRASE";
    }

    @Override
    public void setIndexName(String index) {
        super.setIndexName(index);
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            TermItem word = (TermItem) i.next();
            word.setIndexName(index);
        }
    }

    @Override
    public void setWeight(int weight) {
        super.setWeight(weight);
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item word = i.next();
            word.setWeight(weight);
        }
    }

    @Override
    public boolean acceptsItemsOfType(ItemType itemType) {
        return itemType == ItemType.WORD ||
               itemType == ItemType.WORD_ALTERNATIVES ||
               itemType == ItemType.INT ||
               itemType == ItemType.EXACT;
    }

    /**
     * Adds subitem. The word will have its index name set to the index name
     * of this phrase. If the item is a word, it will simply be added,
     * if the item is a phrase, each of the words of the phrase will be added.
     *
     * @throws IllegalArgumentException if the given item is not a TermItem or PhraseItem
     */
    @Override
    public void addItem(Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem || item instanceof WordAlternativesItem) {
            addIndexedItem((IndexedItem) item);
        } else if (item instanceof IntItem intItem) {
            addIndexedItem(intItem.asWord());
        } else {
            throw new IllegalArgumentException("Can not add " + item + " to a segment phrase");
        }
    }

    @Override
    public void addItem(int index, Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem || item instanceof WordAlternativesItem) {
            addIndexedItem(index, (IndexedItem) item);
        } else if (item instanceof IntItem intItem) {
            addIndexedItem(index, intItem.asWord());
        } else {
            throw new IllegalArgumentException("Can not add " + item + " to a phrase");
        }
    }

    @Override
    public Item setItem(int index, Item item) {
        if (item instanceof WordItem || item instanceof PhraseSegmentItem || item instanceof WordAlternativesItem) {
            return setIndexedItem(index, (IndexedItem) item);
        } else if (item instanceof IntItem intItem) {
            return setIndexedItem(index, intItem.asWord());
        } else {
            throw new IllegalArgumentException("Can not add " + item + " to a phrase");
        }
    }

    @Override
    public Optional<Item> extractSingleChild() {
        Optional<Item> extracted = super.extractSingleChild();
        extracted.ifPresent(e -> e.setWeight(this.getWeight()));
        return extracted;
    }


    private void addIndexedItem(IndexedItem word) {
        word.setIndexName(this.getIndexName());
        super.addItem((Item) word);
    }

    private void addIndexedItem(int index, IndexedItem word) {
        word.setIndexName(this.getIndexName());
        if (word instanceof Item item) {
            item.setWeight(this.getWeight());
        }
        super.addItem(index, (Item) word);
    }

    private Item setIndexedItem(int index, IndexedItem word) {
        word.setIndexName(this.getIndexName());
        if (word instanceof Item item) {
            item.setWeight(this.getWeight());
        }
        return super.setItem(index, (Item) word);
    }

    /**
     * Returns a subitem as a term item
     *
     * @param index the (0-base) index of the item to return
     * @throws IndexOutOfBoundsException if there is no subitem at index
     */
    public TermItem getTermItem(int index) {
        return (TermItem) getItem(index);
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
    }

    @Override
    public int encode(ByteBuffer buffer) {
        encodeThis(buffer);
        return encodeContent(buffer, 1);
    }

    public int encodeContent(ByteBuffer buffer) {
        return encodeContent(buffer, 0);
    }

    private int encodeContent(ByteBuffer buffer, int itemCount) {
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            Item subitem = i.next();
            itemCount += subitem.encode(buffer);
        }
        return itemCount;
    }


    /** Returns false, no parenthezes for phrases */
    @Override
    protected boolean shouldParenthesize() {
        return false;
    }

    /** Segment phrase items uses a empty heading instead of "SPHRASE " */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {}

    @Override
    protected void appendBodyString(StringBuilder buffer) {
        appendIndexString(buffer);
        appendContentsString(buffer);
    }

    void appendContentsString(StringBuilder buffer) {
        buffer.append("'");
        for (Iterator<Item> i = getItemIterator(); i.hasNext();) {
            var item = i.next();
            if (item instanceof WordItem wordItem) {
                buffer.append(wordItem.getWord());
            } else {
                item.appendHeadingString(buffer);
                item.appendBodyString(buffer);
            }
            if (i.hasNext()) {
                buffer.append(" ");
            }
        }
        buffer.append("'");
    }

    @Override
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

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("explicit", explicit);
    }

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        return this.explicit == ((PhraseSegmentItem)other).explicit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), explicit);
    }

    @Override
    SearchProtocol.QueryTreeItem toProtobuf() {
        // PhraseSegmentItem should be converted to a phrase
        var builder = SearchProtocol.ItemPhrase.newBuilder();
        builder.setProperties(ToProtobuf.buildTermProperties(this, getIndexName()));
        for (var child : items()) {
            builder.addChildren(child.toProtobuf());
        }
        return SearchProtocol.QueryTreeItem.newBuilder()
                .setItemPhrase(builder.build())
                .build();
    }

}
