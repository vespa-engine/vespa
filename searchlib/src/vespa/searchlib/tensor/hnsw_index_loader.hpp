// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_loader.h"
#include "hnsw_graph.h"
#include <vespa/searchlib/util/fileutil.h>
#include <cassert>

namespace search::tensor {

template <typename ReaderType, HnswIndexType type>
void
HnswIndexLoader<ReaderType, type>::init()
{
    _entry_nodeid = next_int();
    _entry_level = next_int();
    _num_nodes = next_int();
}

template <typename ReaderType, HnswIndexType type>
HnswIndexLoader<ReaderType, type>::~HnswIndexLoader() = default;

template <typename ReaderType, HnswIndexType type>
HnswIndexLoader<ReaderType, type>::HnswIndexLoader(HnswGraph<type>& graph, IdMapping& id_mapping, std::unique_ptr<ReaderType> reader)
    : _graph(graph),
      _reader(std::move(reader)),
      _entry_nodeid(0),
      _entry_level(0),
      _num_nodes(0),
      _nodeid(0),
      _link_array(),
      _complete(false),
      _id_mapping(id_mapping)
{
    init();
}

template <typename ReaderType, HnswIndexType type>
bool
HnswIndexLoader<ReaderType, type>::load_next()
{
    assert(!_complete);
    static constexpr bool identity_mapping = (type == HnswIndexType::SINGLE);
    if (_nodeid < _num_nodes) {
        uint32_t num_levels = next_int();
        if (num_levels > 0) {
            uint32_t docid = identity_mapping ? _nodeid : next_int();
            uint32_t subspace = identity_mapping  ? 0 : next_int();
            _graph.make_node(_nodeid, docid, subspace, num_levels);
            for (uint32_t level = 0; level < num_levels; ++level) {
                uint32_t num_links = next_int();
                _link_array.clear();
                while (num_links-- > 0) {
                    _link_array.push_back(next_int());
                }
                _graph.set_link_array(_nodeid, level, _link_array);
            }
        }
    }
    if (++_nodeid < _num_nodes) {
        return true;
    } else {
        _graph.nodes.ensure_size(std::max(_num_nodes, 1u));
        _graph.nodes_size.store(std::max(_num_nodes, 1u), std::memory_order_release);
        _graph.trim_nodes_size();
        auto entry_levels_ref = _graph.get_levels_ref(_entry_nodeid);
        _graph.set_entry_node({_entry_nodeid, entry_levels_ref, _entry_level});
        _id_mapping.on_load(_graph.nodes.make_read_view(_graph.size()));
        _complete = true;
        return false;
    }
}

}
