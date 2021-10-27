// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.net.UrlTokenizer;
import com.yahoo.prelude.query.textualrepresentation.Discloser;

import java.util.Objects;


/**
 * Special words known by the index used for marking things.
 * The reserved word itself is not public, while a symbol representation is.
 *
 * @author bratseth
 */
public class MarkerWordItem extends WordItem {

    private final String markerWord;

    private final static String startAnchor = "^";
    private final static String endAnchor = "$";

    private MarkerWordItem(String publicSymbol, String markerWord, String indexName) {
        super(publicSymbol, indexName);
        this.markerWord = markerWord;
    }

    public boolean isStartAnchor() { return getWord().equals(startAnchor); }

    public boolean isEndAnchor() { return getWord().equals(endAnchor); }

    /** Returns the marker word for encoding */
    @Override
    protected String getEncodedWord() {
        return markerWord;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        MarkerWordItem other = (MarkerWordItem) o;
        return markerWord.equals(other.markerWord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), markerWord);
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("markerWord", markerWord);
    }

    /** Creates a special word item which marks the start of a host name */
    public static MarkerWordItem createStartOfHost(String indexName) {
        return new MarkerWordItem(startAnchor, UrlTokenizer.TERM_STARTHOST, indexName);
    }

    /** Creates a special word item which marks the start of a host name, matching the default index */
    public static MarkerWordItem createStartOfHost() {
        return createStartOfHost("");
    }

    /** Creates a special word item which marks the end of a host name */
    public static MarkerWordItem createEndOfHost(String indexName) {
        return new MarkerWordItem(endAnchor, UrlTokenizer.TERM_ENDHOST, indexName);
    }

    /** Creates a special word item which marks the end of a host name matching the default index */
    public static MarkerWordItem createEndOfHost() {
        return createEndOfHost("");
    }

}
