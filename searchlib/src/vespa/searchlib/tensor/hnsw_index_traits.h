// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_type.h"

namespace search::tensor {

class HnswSimpleNode;
class HnswNode;
class HnswIdentityMapping;
class HnswMultiBestNeighbors;
class HnswNodeidMapping;
class HnswSingleBestNeighbors;

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
    using SearchBestNeighbors = HnswSingleBestNeighbors;
};

/*
 * Node type and id mapping for hnsw index type MULTI.
 *
 * Multiple nodes per document.
 * Managed mapping between nodeid and docid.
 */
template <>
class HnswIndexTraits<HnswIndexType::MULTI>
{
public:
    using NodeType = HnswNode;
    using IdMapping = HnswNodeidMapping;
    using SearchBestNeighbors = HnswMultiBestNeighbors;
};

}
