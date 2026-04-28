// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.function.Consumer;

/**
 * Shared parsing of the {@code <prepend>} element for embedder components.
 *
 * @author bjorncs
 */
record EmbedderPrependConfig(String query, String document) {

    void applyTo(Consumer<String> querySetter, Consumer<String> documentSetter) {
        if (query != null) querySetter.accept(query);
        if (document != null) documentSetter.accept(document);
    }

    static EmbedderPrependConfig parsePrependElement(Element parent) {
        Element prepend = XML.getChild(parent, "prepend");
        if (prepend == null) return null;
        String query = XML.getChildValue(prepend, "query").orElse(null);
        String document = XML.getChildValue(prepend, "document").orElse(null);
        return new EmbedderPrependConfig(query, document);
    }
}
