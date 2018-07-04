// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.content.ContentSearch;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * @author Simon Thoresen Hult
 */
public class DomContentSearchBuilder {

    public static ContentSearch build(ModelElement contentXml) {
        ContentSearch.Builder builder = new ContentSearch.Builder();
        ModelElement searchElement = contentXml.getChild("search");
        if (searchElement == null) {
            return builder.build();
        }
        builder.setQueryTimeout(searchElement.childAsDouble("query-timeout"));
        builder.setVisibilityDelay(searchElement.childAsDouble("visibility-delay"));
        return builder.build();
    }
}
