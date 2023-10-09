// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_type.h"
#include "hnsw_node.h"
#include "hnsw_simple_node.h"

namespace search::tensor {

/*
 * Meta data for node during save of hnsw graph.
 */
template <HnswIndexType type>
class HnswIndexSaverMetaDataNode;

/*
 * Meta data for node during save of hnsw graph with one node per document and
 * identity mapping between nodeid and docid.
 */
template <>
class HnswIndexSaverMetaDataNode<HnswIndexType::SINGLE>
{
    uint32_t _refs_offset;

public:
    HnswIndexSaverMetaDataNode(uint32_t refs_offset) noexcept
        : _refs_offset(refs_offset)
    {
    }
    HnswIndexSaverMetaDataNode(uint32_t refs_offset, const HnswSimpleNode&) noexcept
        : _refs_offset(refs_offset)
    {
    }
    uint32_t get_refs_offset() const noexcept { return _refs_offset; }
    static constexpr bool identity_mapping = true;
};

/*
 * Meta data for node during save of hnsw graph with multiple nodes per document and
 * managed mapping between nodeid and docid.
 */
template <>
class HnswIndexSaverMetaDataNode<HnswIndexType::MULTI>
{
    uint32_t _refs_offset;
    uint32_t _docid;
    uint32_t _subspace;
public:
    HnswIndexSaverMetaDataNode(uint32_t refs_offset) noexcept
        : _refs_offset(refs_offset),
          _docid(0),
          _subspace(0)
    {
    }
    HnswIndexSaverMetaDataNode(uint32_t refs_offset, const HnswNode& node) noexcept
        : _refs_offset(refs_offset),
          _docid(node.acquire_docid()),
          _subspace(node.acquire_subspace())
    {
    }
    uint32_t get_refs_offset() const noexcept { return _refs_offset; }
    uint32_t get_docid() const noexcept { return _docid; }
    uint32_t get_subspace() const noexcept { return _subspace; }
    static constexpr bool identity_mapping = false;
};

}
