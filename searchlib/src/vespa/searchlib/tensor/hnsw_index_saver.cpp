// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_saver.h"

#include "hnsw_graph.h"

#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/util/bufferwriter.h>

#include <cassert>
#include <limits>

using search::common::FileHeaderContext;
using vespalib::GenerationGuard;

namespace search::tensor {

namespace {

template <HnswIndexType type> size_t count_valid_link_arrays(const HnswGraph<type>& graph) {
    size_t count(0);
    size_t num_nodes = graph.nodes.get_size(); // Called from writer only
    for (size_t i = 0; i < num_nodes; ++i) {
        auto levels_ref = graph.get_levels_ref(i);
        if (levels_ref.valid()) {
            count += graph.levels_store.get(levels_ref).size();
        }
    }
    return count;
}

} // namespace

template <HnswIndexType type>
HnswIndexSaver<type>::Metadata::Metadata() : entry_nodeid(0), entry_level(-1), refs(), nodes() {
}

template <HnswIndexType type> HnswIndexSaver<type>::Metadata::~Metadata() = default;

template <HnswIndexType type> HnswIndexSaver<type>::~HnswIndexSaver() {
    _guard = GenerationGuard();
    _graph.set_last_flush_duration(FileHeaderContext::make_flush_duration(_index_flush_start_time));
}

template <HnswIndexType type>
HnswIndexSaver<type>::HnswIndexSaver(const HnswGraph<type>& graph)
    : _graph_links(graph.links_store),
      _metadata(),
      _guard(graph.make_guard()),
      _index_flush_start_time(std::chrono::steady_clock::now()),
      _graph(graph) {
    auto entry = graph.get_entry_node();
    _metadata.entry_nodeid = entry.nodeid;
    _metadata.entry_level = entry.level;
    size_t num_nodes = graph.nodes.get_size(); // Called from writer only
    assert(num_nodes <= (std::numeric_limits<uint32_t>::max() - 1));
    size_t link_array_count = count_valid_link_arrays(graph);
    assert(link_array_count <= std::numeric_limits<uint32_t>::max());
    _metadata.refs.reserve(link_array_count);
    _metadata.nodes.reserve(num_nodes + 1);
    for (size_t i = 0; i < num_nodes; ++i) {
        auto& node = graph.nodes.get_elem_ref(i);
        _metadata.nodes.emplace_back(_metadata.refs.size(), node);
        auto levels_ref = node.levels_ref().load_relaxed();
        if (levels_ref.valid()) {
            auto levels = graph.levels_store.get(levels_ref);
            for (const auto& links_ref : levels) {
                _metadata.refs.push_back(links_ref.load_relaxed());
            }
        }
    }
    _metadata.nodes.emplace_back(_metadata.refs.size());
}

template <HnswIndexType type> void HnswIndexSaver<type>::save(BufferWriter& writer) const {
    writer.write(&_metadata.entry_nodeid, sizeof(uint32_t));
    writer.write(&_metadata.entry_level, sizeof(int32_t));
    uint32_t num_nodes = _metadata.nodes.size() - 1;
    writer.write(&num_nodes, sizeof(uint32_t));
    for (uint32_t i(0); i < num_nodes; i++) {
        auto&    node = _metadata.nodes[i];
        uint32_t offset = node.get_refs_offset();
        uint32_t next_offset = _metadata.nodes[i + 1].get_refs_offset();
        uint32_t num_levels = next_offset - offset;
        writer.write(&num_levels, sizeof(uint32_t));
        if (num_levels > 0) {
            if constexpr (!HnswIndexSaverMetadataNode<type>::identity_mapping) {
                uint32_t docid = node.get_docid();
                uint32_t subspace = node.get_subspace();
                writer.write(&docid, sizeof(uint32_t));
                writer.write(&subspace, sizeof(uint32_t));
            }
        }
        for (; offset < next_offset; offset++) {
            auto links_ref = _metadata.refs[offset];
            if (links_ref.valid()) {
                std::span<const uint32_t> link_array = _graph_links.get(links_ref);
                uint32_t                  num_links = link_array.size();
                writer.write(&num_links, sizeof(uint32_t));
                writer.write(link_array.data(), sizeof(uint32_t) * num_links);
            } else {
                uint32_t num_links = 0;
                writer.write(&num_links, sizeof(uint32_t));
            }
        }
    }
    writer.flush();
}

template class HnswIndexSaver<HnswIndexType::SINGLE>;
template class HnswIndexSaver<HnswIndexType::MULTI>;

} // namespace search::tensor
