// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/** Counts documents contributing to a group, even when a document contributes several elements. */
@Beta
public class DocumentCountAggregator extends AggregatorNode {

    public DocumentCountAggregator() {
        this(null, null);
    }

    private DocumentCountAggregator(String label, Integer level) {
        super("doccount", label, level);
    }

    @Override
    public DocumentCountAggregator copy() {
        return new DocumentCountAggregator(getLabel(), getLevelOrNull());
    }

}
