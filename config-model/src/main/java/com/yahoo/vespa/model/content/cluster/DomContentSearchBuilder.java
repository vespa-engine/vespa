// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.content.ContentSearch;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
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
