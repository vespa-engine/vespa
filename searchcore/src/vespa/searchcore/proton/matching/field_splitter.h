// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/node.h>

namespace proton::matching {

/**
 * FieldSplitter - Splits multi-field query terms into separate per-field term instances
 *
 * This utility transforms query trees to normalize field references. When a query term
 * can match multiple fields (e.g., searching "foo" in both "title" and "body"), this
 * splitter creates separate term instances for each field and combines them with OR nodes.
 *
 * Purpose:
 * - Simplifies query execution by ensuring each term operates on exactly one field
 * - Enables field-specific optimizations and statistics
 * - Resolves field references early in the query processing pipeline
 *
 * Key behaviors:
 * - Terms with single field: Pass through unchanged
 * - Terms with multiple fields: Split into OR(term_field1, term_field2, ...)
 * - Phrase nodes: Split into per-field phrases, forcing children to use phrase's field
 * - Equiv nodes: Group children by field, creating one Equiv per field
 * - Multi-term nodes: WeightedSet, DotProduct, WandTerm, InTerm, WordAlternatives
 *
 * Error handling:
 * - Returns original tree if splitting fails
 * - Reports issues via vespalib::Issue when errors occur
 * - Logs the transformed tree for debugging
 *
 * Example transformation:
 *   Input:  StringTerm("search", fields=[title, body])
 *   Output: OR(StringTerm("search", field=title), StringTerm("search", field=body))
 */
struct FieldSplitter {
    using Node = search::query::Node;
    /**
     * Splits multi-field terms in the query tree into separate per-field instances.
     *
     * Takes a query tree and transforms it so that each term operates on a single field.
     * Terms with multiple fields are split and connected with OR nodes. Returns the
     * transformed tree, or the original tree if splitting fails.
     *
     * @param node The query tree to transform (ownership is transferred)
     * @return The transformed query tree (or original if splitting fails)
     */
    static Node::UP split_terms(Node::UP node);
};

}
