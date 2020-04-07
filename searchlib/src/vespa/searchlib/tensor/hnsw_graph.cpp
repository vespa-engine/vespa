// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_graph.h"
#include "hnsw_index.h"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace search::tensor {

HnswGraph::HnswGraph()
  : node_refs(),
    nodes(HnswIndex::make_default_node_store_config()),
    links(HnswIndex::make_default_link_store_config()),
    entry_docid(0), // Note that docid 0 is reserved and never used
    entry_level(-1)
{}

HnswGraph::~HnswGraph() {}

void
HnswGraph::make_node_for_document(uint32_t docid, uint32_t num_levels)
{
    node_refs.ensure_size(docid + 1, AtomicEntryRef());
    // A document cannot be added twice.
    assert(!node_refs[docid].load_acquire().valid());
    // Note: The level array instance lives as long as the document is present in the index.
    vespalib::Array<AtomicEntryRef> levels(num_levels, AtomicEntryRef());
    auto node_ref = nodes.add(levels);
    node_refs[docid].store_release(node_ref);
}

void
HnswGraph::remove_node_for_document(uint32_t docid)
{
    auto node_ref = node_refs[docid].load_acquire();
    nodes.remove(node_ref);
    search::datastore::EntryRef invalid;
    node_refs[docid].store_release(invalid);
}

void     
HnswGraph::set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& new_links)
{
    auto new_links_ref = links.add(new_links);
    auto node_ref = node_refs[docid].load_acquire();
    assert(node_ref.valid());
    auto levels = nodes.get_writable(node_ref);
    auto old_links_ref = levels[level].load_acquire();
    levels[level].store_release(new_links_ref);
    links.remove(old_links_ref);
}

HnswGraph::Histograms
HnswGraph::histograms() const
{
    Histograms result;
    size_t num_nodes = node_refs.size();
    for (size_t i = 0; i < num_nodes; ++i) {
        auto node_ref = node_refs[i].load_acquire();
        if (node_ref.valid()) {
            uint32_t levels = 0;
            uint32_t l0links = 0;
            auto level_array = nodes.get(node_ref);
            levels = level_array.size();
            if (levels > 0) {
                auto links_ref = level_array[0].load_acquire();
                auto link_array = links.get(links_ref);
                l0links = link_array.size();
            }
            while (result.level_histogram.size() <= levels) {
                result.level_histogram.push_back(0);
            }
            ++result.level_histogram[levels];
            while (result.links_histogram.size() <= l0links) {
                result.links_histogram.push_back(0);
            }
            ++result.links_histogram[l0links];
        }
    }
    return result;
}

} // namespace
