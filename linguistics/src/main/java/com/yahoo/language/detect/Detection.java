// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.detect;

import com.yahoo.language.Language;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * @author Einar M R Rosenvinge
 */
public class Detection {

    private final Language language;
    private final String encodingName;
    private final boolean local;

    public Detection(Language language, String encodingName, boolean local) {
        this.language = language;
        this.encodingName = encodingName;
        this.local = local;
    }

    public Language getLanguage() {
        return language;
    }

    public Charset getEncoding() {
        if (encodingName == null) {
            return null;
        }
        try {
            return Charset.forName(encodingName);
        } catch (UnsupportedCharsetException e) {
            // ignore
        }
        return null;
    }

    public String getEncodingName() {
        return encodingName;
    }

    public boolean isLocal() {
        return local;
    }

}
