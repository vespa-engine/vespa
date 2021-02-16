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
    entry_docid_and_level()
{
    node_refs.ensure_size(1, AtomicEntryRef());
    EntryNode entry;
    set_entry_node(entry);
}

HnswGraph::~HnswGraph() = default;

HnswGraph::NodeRef
HnswGraph::make_node_for_document(uint32_t docid, uint32_t num_levels)
{
    node_refs.ensure_size(docid + 1, AtomicEntryRef());
    // A document cannot be added twice.
    assert(!node_refs[docid].load_acquire().valid());
    // Note: The level array instance lives as long as the document is present in the index.
    vespalib::Array<AtomicEntryRef> levels(num_levels, AtomicEntryRef());
    auto node_ref = nodes.add(levels);
    node_refs[docid].store_release(node_ref);
    return node_ref;
}

void
HnswGraph::remove_node_for_document(uint32_t docid)
{
    auto node_ref = node_refs[docid].load_acquire();
    assert(node_ref.valid());
    auto levels = nodes.get(node_ref);
    vespalib::datastore::EntryRef invalid;
    node_refs[docid].store_release(invalid);
    // Ensure data referenced through the old ref can be recycled:
    nodes.remove(node_ref);
    for (size_t i = 0; i < levels.size(); ++i) {
        auto old_links_ref = levels[i].load_acquire();
        links.remove(old_links_ref);
    }
}

void     
HnswGraph::set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& new_links)
{
    auto new_links_ref = links.add(new_links);
    auto node_ref = node_refs[docid].load_acquire();
    assert(node_ref.valid());
    auto levels = nodes.get_writable(node_ref);
    assert(level < levels.size());
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

void
HnswGraph::set_entry_node(EntryNode node) {
    uint64_t value = node.level;
    value <<= 32;
    value |= node.docid;
    if (node.node_ref.valid()) {
        assert(node.level >= 0);
        assert(node.docid > 0);
    } else {
        assert(node.level == -1);
        assert(node.docid == 0);
    }
    entry_docid_and_level.store(value, std::memory_order_release);
}

} // namespace

namespace vespalib {

template class RcuVectorBase<vespalib::datastore::AtomicEntryRef>;

}
