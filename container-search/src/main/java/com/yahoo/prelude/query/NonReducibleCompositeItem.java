// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Optional;

/**
 * A composite item which specifies semantics which are not maintained
 * if an instance with a single child is replaced by the single child.
 * Most composites, like AND and OR, are reducible as e.g (AND a) is semantically equal to (a).
 * This type functions as a marker type for query rewriters.
 *
 * @author bratseth
 */
public abstract class NonReducibleCompositeItem extends CompositeItem {

    @Override
    public Optional<Item> extractSingleChild() {
        return Optional.empty();
    }

}
