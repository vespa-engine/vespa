// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

/*
 * Enum class that selects what type of hnsw index to use.
 *
 * SINGLE: One node per document. Identity mapping between nodeid and docid.
 *
 * MULTI: Multiple nodes per document. Managed mapping between nodeid and docid.
 */
enum class HnswIndexType
{
    SINGLE,
    MULTI
};

}
