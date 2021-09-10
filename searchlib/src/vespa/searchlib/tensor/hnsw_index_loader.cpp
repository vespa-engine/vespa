// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_loader.h"
#include "hnsw_graph.h"
#include <vespa/searchlib/util/fileutil.h>

namespace search::tensor {

void
HnswIndexLoader::init()
{
    size_t num_readable = _buf->size(sizeof(uint32_t));
    _ptr = static_cast<const uint32_t *>(_buf->buffer());
    _end = _ptr + num_readable;
    _entry_docid = next_int();
    _entry_level = next_int();
    _num_nodes = next_int();
}

HnswIndexLoader::~HnswIndexLoader() {}


HnswIndexLoader::HnswIndexLoader(HnswGraph& graph, std::unique_ptr<fileutil::LoadedBuffer> buf)
    : _graph(graph),
      _buf(std::move(buf)),
      _ptr(nullptr),
      _end(nullptr),
      _entry_docid(0),
      _entry_level(0),
      _num_nodes(0),
      _docid(0),
      _link_array(),
      _complete(false)
{
    init();
}

bool
HnswIndexLoader::load_next()
{
    assert(!_complete);
    if (_docid < _num_nodes) {
        uint32_t num_levels = next_int();
        if (num_levels > 0) {
            _graph.make_node_for_document(_docid, num_levels);
            for (uint32_t level = 0; level < num_levels; ++level) {
                uint32_t num_links = next_int();
                _link_array.clear();
                while (num_links-- > 0) {
                    _link_array.push_back(next_int());
                }
                _graph.set_link_array(_docid, level, _link_array);
            }
        }
    }
    if (++_docid < _num_nodes) {
        return true;
    } else {
        _graph.node_refs.ensure_size(std::max(_num_nodes, 1u));
        _graph.node_refs_size.store(std::max(_num_nodes, 1u), std::memory_order_release);
        _graph.trim_node_refs_size();
        auto entry_node_ref = _graph.get_node_ref(_entry_docid);
        _graph.set_entry_node({_entry_docid, entry_node_ref, _entry_level});
        _complete = true;
        return false;
    }
}

}
