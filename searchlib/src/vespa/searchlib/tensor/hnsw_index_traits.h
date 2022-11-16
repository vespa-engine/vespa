// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_type.h"

namespace search::tensor {

class HnswSimpleNode;
class HnswIdentityMapping;

/*
 * Class that selects what node type and id mapping to use based on
 * hnsw index type.
 */
template <HnswIndexType type>
class HnswIndexTraits;

/*
 * Node type and id mapping for hnsw index type SINGLE.
 *
 * One node per document.
 * Identity mapping between nodeid and docid.
 */
template <>
class HnswIndexTraits<HnswIndexType::SINGLE>
{
public:
    using NodeType = HnswSimpleNode;
    using IdMapping = HnswIdentityMapping;
};

}
