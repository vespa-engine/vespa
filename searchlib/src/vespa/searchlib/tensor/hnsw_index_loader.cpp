// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_loader.h"
#include "hnsw_graph.h"
#include <vespa/searchlib/util/fileutil.h>

namespace search::tensor {

HnswIndexLoader::~HnswIndexLoader() {}

HnswIndexLoader::HnswIndexLoader(HnswGraph &graph)
    : _graph(graph)
{
}

bool
HnswIndexLoader::load(const fileutil::LoadedBuffer& buf)
{
    size_t num_readable = buf.size(sizeof(uint32_t));
    if (num_readable < 3) return false;
    const uint32_t *ptr = static_cast<const uint32_t *>(buf.buffer());
    const uint32_t *end = ptr + num_readable;
    uint32_t entry_docid = *ptr++;
    int32_t entry_level = *ptr++;
    uint32_t num_nodes = *ptr++;
    std::vector<uint32_t> link_array;
    for (uint32_t docid = 0; docid < num_nodes; ++docid) {
        if (ptr == end) return false;
        uint32_t num_levels = *ptr++;
        if (num_levels > 0) {
            _graph.make_node_for_document(docid, num_levels);
            for (uint32_t level = 0; level < num_levels; ++level) {
                uint32_t num_links = *ptr++;
                link_array.clear();
                while (num_links-- > 0) {
                    if (ptr == end) return false;
                    link_array.push_back(*ptr++);
                }
                _graph.set_link_array(docid, level, link_array);
            }
        }
    }
    _graph.set_entry_node(entry_docid, entry_level);
    return true;
}


}
