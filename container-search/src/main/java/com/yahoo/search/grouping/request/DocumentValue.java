// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a document value in a {@link GroupingExpression}. As such, the subclasses of this can only be
 * used as document-level expressions (i.e. level 0, see {@link GroupingExpression#resolveLevel(int)}).
 *
 * @author Simon Thoresen Hult
 */
public abstract class DocumentValue extends GroupingExpression {

    protected DocumentValue(String image, String label, Integer level) {
        super(image, label, level);
    }

    @Override
    public void resolveLevel(int level) {
        if (level != 0) {
            throw new IllegalArgumentException("Expression '" + this + "' not applicable for " +
                                               GroupingOperation.getLevelDesc(level) + ".");
        }
        super.resolveLevel(level);
    }

}
