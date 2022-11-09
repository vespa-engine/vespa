// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_graph.h"
#include "hnsw_index.h"
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/vespalib/datastore/array_store.hpp>

namespace search::tensor {

HnswGraph::HnswGraph()
  : node_refs(),
    node_refs_size(1u),
    nodes(HnswIndex::make_default_node_store_config(), {}),
    links(HnswIndex::make_default_link_store_config(), {}),
    entry_nodeid_and_level()
{
    node_refs.ensure_size(1, NodeType());
    EntryNode entry;
    set_entry_node(entry);
}

HnswGraph::~HnswGraph() = default;

HnswGraph::NodeRef
HnswGraph::make_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, uint32_t num_levels)
{
    node_refs.ensure_size(nodeid + 1, NodeType());
    // A document cannot be added twice.
    assert(!get_node_ref(nodeid).valid());
    // Note: The level array instance lives as long as the document is present in the index.
    std::vector<AtomicEntryRef> levels(num_levels, AtomicEntryRef());
    auto node_ref = nodes.add(levels);
    auto& node = node_refs[nodeid];
    node.ref().store_release(node_ref);
    node.store_docid(docid);
    node.store_subspace(subspace);
    if (nodeid >= node_refs_size.load(std::memory_order_relaxed)) {
        node_refs_size.store(nodeid + 1, std::memory_order_release);
    }
    return node_ref;
}

void
HnswGraph::remove_node(uint32_t nodeid)
{
    auto node_ref = get_node_ref(nodeid);
    assert(node_ref.valid());
    auto levels = nodes.get(node_ref);
    vespalib::datastore::EntryRef invalid;
    node_refs[nodeid].ref().store_release(invalid);
    // Ensure data referenced through the old ref can be recycled:
    nodes.remove(node_ref);
    for (size_t i = 0; i < levels.size(); ++i) {
        auto old_links_ref = levels[i].load_relaxed();
        links.remove(old_links_ref);
    }
    if (nodeid + 1 == node_refs_size.load(std::memory_order_relaxed)) {
        trim_node_refs_size();
    }
}

void
HnswGraph::trim_node_refs_size()
{
    uint32_t check_nodeid = node_refs_size.load(std::memory_order_relaxed) - 1;
    while (check_nodeid > 0u && !get_node_ref(check_nodeid).valid()) {
        --check_nodeid;
    }
    node_refs_size.store(check_nodeid + 1, std::memory_order_release);
}

void     
HnswGraph::set_link_array(uint32_t nodeid, uint32_t level, const LinkArrayRef& new_links)
{
    auto new_links_ref = links.add(new_links);
    auto node_ref = get_node_ref(nodeid);
    assert(node_ref.valid());
    auto levels = nodes.get_writable(node_ref);
    assert(level < levels.size());
    auto old_links_ref = levels[level].load_relaxed();
    levels[level].store_release(new_links_ref);
    links.remove(old_links_ref);
}

HnswGraph::Histograms
HnswGraph::histograms() const
{
    Histograms result;
    size_t num_nodes = node_refs_size.load(std::memory_order_acquire);
    for (size_t i = 0; i < num_nodes; ++i) {
        auto node_ref = acquire_node_ref(i);
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
    value |= node.nodeid;
    if (node.node_ref.valid()) {
        assert(node.level >= 0);
        assert(node.nodeid > 0);
    } else {
        assert(node.level == -1);
        assert(node.nodeid == 0);
    }
    entry_nodeid_and_level.store(value, std::memory_order_release);
}

} // namespace

namespace vespalib {

template class RcuVectorBase<search::tensor::HnswSimpleNode>;
template class RcuVector<search::tensor::HnswSimpleNode>;

}
