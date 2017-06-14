// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.net.UrlTokenizer;
import com.yahoo.prelude.query.textualrepresentation.Discloser;


/**
 * Special words known by the index used for marking things.
 * The reserved word itself is not public, while a symbol representation is.
 *
 * @author  bratseth
 */
public class MarkerWordItem extends WordItem {

    /** Creates a special word item which marks the start of a host name */
    public static WordItem createStartOfHost() {
        return new MarkerWordItem("^", UrlTokenizer.TERM_STARTHOST);
    }

    /** Creates a special word item which marks the end of a host name */
    public static WordItem createEndOfHost() {
        return new MarkerWordItem("$", UrlTokenizer.TERM_ENDHOST);
    }

    private String markerWord;

    private MarkerWordItem(String publicSymbol, String markerWord) {
        super(publicSymbol);
        this.markerWord = markerWord;
    }

    /** Returns the marker word for encoding */
    protected String getEncodedWord() {
        return markerWord;
    }

    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        if (!(o instanceof MarkerWordItem)) {
            return false;
        }

        MarkerWordItem other = (MarkerWordItem) o;

        return markerWord.equals(other.markerWord);
    }

    public int hashCode() {
        return super.hashCode() + 499 * markerWord.hashCode();
    }

    @Override
    public void disclose(Discloser discloser) {
        super.disclose(discloser);
        discloser.addProperty("markerWord", markerWord);
    }
}
