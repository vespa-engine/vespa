// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.prelude.query.parser.Token;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.protect.Validator;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A simple word or token to match in some field.
 *
 * @author bratseth
 * @author havardpe
 */
public class WordItem extends TermItem {

    /** True if this is not part of the special tokens dictionary */
    private boolean words = true;

    /** Is this word stemmed? */
    private boolean stemmed = false;

    /** Is this word produced from segmenting a block of word characters? */
    private boolean fromSegmented = false;

    /** If fromSegmented is true, this is the index into the list of segments */
    private int segmentIndex = 0;

    /** The word as it should be searched, never null */
    private String word;

    private boolean lowercased = false;

    public WordItem(String word) {
        this(word, "");
    }

    public WordItem(String word, String indexName) {
        this(word, indexName, false, null);
    }

    public WordItem(String word, boolean isFromQuery) {
        this(word, null, isFromQuery, null);
    }

    public WordItem(String word, String indexName, boolean isFromQuery) {
        this(word, indexName, isFromQuery, null);
    }

    public WordItem(Token word, boolean isFromQuery) {
        this(word.toString(), "", isFromQuery, word.substring);
    }

    public WordItem(String word, boolean isFromQuery, Substring origin) {
        this(word, "", isFromQuery, origin);
    }

    public WordItem(String word, String indexName, boolean isFromQuery, Substring origin) {
        super(indexName, isFromQuery, origin);
        setWord(word);
    }

    public ItemType getItemType() {
        return ItemType.WORD;
    }

    public String getName() {
        return "WORD";
    }

    public void setWord(String word) {
        Validator.ensureNotNull("The word of a word item", word);
        Validator.ensureNonEmpty("The word of a word item", word);
        this.word = word;
    }

    @Override
    protected void encodeThis(ByteBuffer buffer) {
        super.encodeThis(buffer); // takes care of index bytes
        putString(getEncodedWord(), buffer);
    }

    /** Returns the word for encoding. By default simply the word */
    protected String getEncodedWord() {
        return getIndexedString();
    }

    /** Returns the same as {@link #stringValue} */
    public String getWord() { return word; }

    /**
     * Returns this word as it should be used in executing the query.
     * This is usually (but not always) a normalized and stemmed form
     */
    @Override
    public String stringValue() { return word; }

    /** Same as #setWord */
    @Override
    public void setValue(String value) { setWord(value); }

    /**
     * Get the word exactly as received in the request.
     * This returns the same as getWord if no other raw form is known
     *
     * @return the raw form of this word, never null
     */
    @Override
    public String getRawWord() {
        if (getOrigin()!=null) return getOrigin().getValue();
        return word;
    }

    @Override
    public boolean isStemmed() { return stemmed; }

    public void setStemmed(boolean stemmed) { this.stemmed = stemmed; }

    public boolean isFromSegmented() {
        return fromSegmented;
    }

    public void setFromSegmented(boolean fromSegmented) {
        this.fromSegmented = fromSegmented;
    }

    public boolean isLowercased() {
        return lowercased;
    }

    public void setLowercased(boolean lowercased) {
        this.lowercased = lowercased;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    /** Word items uses a empty heading instead of "WORD " */
    @Override
    protected void appendHeadingString(StringBuilder buffer) {}

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        var other = (WordItem)o;
        if ( this.words != other.words) return false;
        if ( this.stemmed != other.stemmed) return false;
        if ( this.fromSegmented != other.fromSegmented) return false;
        if ( this.segmentIndex != other.segmentIndex) return false;
        if ( ! Objects.equals(this.word, other.word)) return false;
        if ( this.lowercased != other.lowercased) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), words, stemmed, fromSegmented, segmentIndex, word, lowercased);
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public String getIndexedString() {
        return word;
    }

    /** Returns true if this consists of regular word characters. Returns false if this represents a "special token" */
    @Override
    public boolean isWords() {
        return words;
    }

    /** Sets if this consists of regular word characters (true) or represents a "special token" (false) */
    public void setWords(boolean words) {
        this.words = words;
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("fromSegmented", fromSegmented);
        discloser.addProperty("segmentIndex", segmentIndex);
        discloser.addProperty("stemmed", stemmed);
        discloser.addProperty("words", words);
    }

}
