// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_saver.h"
#include "hnsw_graph.h"
#include <vespa/vespalib/util/bufferwriter.h>

namespace search::tensor {

HnswIndexSaver::~HnswIndexSaver() {}

HnswIndexSaver::HnswIndexSaver(const HnswGraph &graph)
    : _graph(graph), _meta_data()
{
    _meta_data.entry_docid = _graph.entry_docid;
    _meta_data.entry_level = _graph.entry_level;
    size_t num_nodes = _graph.node_refs.size();
    _meta_data.nodes.reserve(num_nodes);
    for (size_t i = 0; i < num_nodes; ++i) {
        LevelVector node;
        auto node_ref = _graph.node_refs[i].load_acquire();
        if (node_ref.valid()) {
            auto levels = _graph.nodes.get(node_ref);
            for (const auto& links_ref : levels) {
                auto level = links_ref.load_acquire();
                node.push_back(level);
            }
        }
        _meta_data.nodes.emplace_back(std::move(node));
    }
}

void
HnswIndexSaver::save(BufferWriter& writer) const
{
    writer.write(&_meta_data.entry_docid, sizeof(uint32_t));
    writer.write(&_meta_data.entry_level, sizeof(int32_t));
    uint32_t num_nodes = _meta_data.nodes.size();
    writer.write(&num_nodes, sizeof(uint32_t));
    for (const auto &node : _meta_data.nodes) {
        uint32_t num_levels = node.size();
        writer.write(&num_levels, sizeof(uint32_t));
        for (auto links_ref : node) {
            if (links_ref.valid()) {
                vespalib::ConstArrayRef<uint32_t> link_array = _graph.links.get(links_ref);
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
