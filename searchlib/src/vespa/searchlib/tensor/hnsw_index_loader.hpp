// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_loader.h"
#include "hnsw_graph.h"
#include <vespa/searchlib/util/fileutil.h>
#include <cassert>

namespace search::tensor {

template <typename ReaderType>
void
HnswIndexLoader<ReaderType>::init()
{
    _entry_nodeid = next_int();
    _entry_level = next_int();
    _num_nodes = next_int();
}

template <typename ReaderType>
HnswIndexLoader<ReaderType>::~HnswIndexLoader() = default;

template <typename ReaderType>
HnswIndexLoader<ReaderType>::HnswIndexLoader(HnswGraph& graph, std::unique_ptr<ReaderType> reader)
    : _graph(graph),
      _reader(std::move(reader)),
      _entry_nodeid(0),
      _entry_level(0),
      _num_nodes(0),
      _nodeid(0),
      _link_array(),
      _complete(false)
{
    init();
}

template <typename ReaderType>
bool
HnswIndexLoader<ReaderType>::load_next()
{
    assert(!_complete);
    if (_nodeid < _num_nodes) {
        uint32_t num_levels = next_int();
        if (num_levels > 0) {
            _graph.make_node(_nodeid, _nodeid, 0, num_levels);
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
        _graph.node_refs.ensure_size(std::max(_num_nodes, 1u));
        _graph.node_refs_size.store(std::max(_num_nodes, 1u), std::memory_order_release);
        _graph.trim_node_refs_size();
        auto entry_node_ref = _graph.get_node_ref(_entry_nodeid);
        _graph.set_entry_node({_entry_nodeid, entry_node_ref, _entry_level});
        _complete = true;
        return false;
    }
}

}
