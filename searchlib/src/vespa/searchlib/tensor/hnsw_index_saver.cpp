// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_saver.h"
#include "hnsw_graph.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <limits>
#include <cassert>

namespace search::tensor {

namespace {

size_t
count_valid_link_arrays(const HnswGraph & graph) {
    size_t count(0);
    size_t num_nodes = graph.node_refs.get_size(); // Called from writer only
    for (size_t i = 0; i < num_nodes; ++i) {
        auto node_ref = graph.get_node_ref(i);
        if (node_ref.valid()) {
            count += graph.nodes.get(node_ref).size();
        }
    }
    return count;
}

}

HnswIndexSaver::MetaData::MetaData()
    : entry_nodeid(0),
      entry_level(-1),
      refs(),
      nodes()
{}
HnswIndexSaver::MetaData::~MetaData() = default;
HnswIndexSaver::~HnswIndexSaver() = default;

HnswIndexSaver::HnswIndexSaver(const HnswGraph &graph)
    : _graph_links(graph.links), _meta_data()
{
    auto entry = graph.get_entry_node();
    _meta_data.entry_nodeid = entry.nodeid;
    _meta_data.entry_level = entry.level;
    size_t num_nodes = graph.node_refs.get_size(); // Called from writer only
    assert (num_nodes <= (std::numeric_limits<uint32_t>::max() - 1));
    size_t link_array_count = count_valid_link_arrays(graph);
    assert (link_array_count <= std::numeric_limits<uint32_t>::max());
    _meta_data.refs.reserve(link_array_count);
    _meta_data.nodes.reserve(num_nodes+1);
    for (size_t i = 0; i < num_nodes; ++i) {
        _meta_data.nodes.push_back(_meta_data.refs.size());
        auto node_ref = graph.get_node_ref(i);
        if (node_ref.valid()) {
            auto levels = graph.nodes.get(node_ref);
            for (const auto& links_ref : levels) {
                _meta_data.refs.push_back(links_ref.load_relaxed());
            }
        }
    }
    _meta_data.nodes.push_back(_meta_data.refs.size());
}

void
HnswIndexSaver::save(BufferWriter& writer) const
{
    writer.write(&_meta_data.entry_nodeid, sizeof(uint32_t));
    writer.write(&_meta_data.entry_level, sizeof(int32_t));
    uint32_t num_nodes = _meta_data.nodes.size() - 1;
    writer.write(&num_nodes, sizeof(uint32_t));
    for (uint32_t i(0); i < num_nodes; i++) {
        uint32_t offset = _meta_data.nodes[i];
        uint32_t next_offset = _meta_data.nodes[i+1];
        uint32_t num_levels = next_offset - offset;
        writer.write(&num_levels, sizeof(uint32_t));
        for (; offset < next_offset; offset++) {
            auto links_ref = _meta_data.refs[offset];
            if (links_ref.valid()) {
                vespalib::ConstArrayRef<uint32_t> link_array = _graph_links.get(links_ref);
                uint32_t num_links = link_array.size();
                writer.write(&num_links, sizeof(uint32_t));
                writer.write(link_array.cbegin(), sizeof(uint32_t)*num_links);
            } else {
                uint32_t num_links = 0;
                writer.write(&num_links, sizeof(uint32_t));
            }
        }
    }
    writer.flush();
}

}
