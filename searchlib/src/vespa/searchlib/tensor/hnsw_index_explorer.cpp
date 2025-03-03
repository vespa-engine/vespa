// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_explorer.h"
#include "hnsw_index.h"
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

using vespalib::slime::ObjectInserter;

namespace search::tensor {

namespace {

const std::string LEVELS_STORE_NAME("levels_store");
const std::string LINKS_STORE_NAME("links_store");
const std::string NODEID_STORE_NAME("nodeid_store");

}

template <HnswIndexType type>
HnswIndexExplorer<type>::HnswIndexExplorer(const HnswIndex<type>& index)
    : _index(index)
{
}

template <HnswIndexType type>
HnswIndexExplorer<type>::~HnswIndexExplorer() = default;

template <HnswIndexType type>
void
HnswIndexExplorer<type>::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    (void) full;
    auto& object = inserter.insertObject();
    auto& graph = _index.get_graph();
    auto& memUsageObj = object.setObject("memory_usage");
    StateExplorerUtils::memory_usage_to_slime(_index.memory_usage(), memUsageObj.setObject("all"));
    StateExplorerUtils::memory_usage_to_slime(graph.nodes.getMemoryUsage(), memUsageObj.setObject("nodes"));
    StateExplorerUtils::memory_usage_to_slime(graph.levels_store.getMemoryUsage(), memUsageObj.setObject("levels"));
    StateExplorerUtils::memory_usage_to_slime(graph.links_store.getMemoryUsage(), memUsageObj.setObject("links"));
    object.setLong("nodeid_limit", graph.size());
    object.setLong("nodes", graph.get_active_nodes());
    auto& histogram_array = object.setArray("level_histogram");
    auto& links_hst_array = object.setArray("level_0_links_histogram");
    auto histograms = graph.histograms();
    uint32_t valid_nodes = 0;
    for (uint32_t hist_val : histograms.level_histogram) {
        histogram_array.addLong(hist_val);
        valid_nodes += hist_val;
    }
    object.setLong("valid_nodes", valid_nodes);
    for (uint32_t hist_val : histograms.links_histogram) {
        links_hst_array.addLong(hist_val);
    }
    auto count_result = _index.count_reachable_nodes();
    uint32_t unreachable = valid_nodes - count_result.first;
    if (count_result.second) {
        object.setLong("unreachable_nodes", unreachable);
    } else {
        object.setLong("unreachable_nodes_incomplete_count", unreachable);
    }
    auto entry_node = graph.get_entry_node();
    object.setLong("entry_nodeid", entry_node.nodeid);
    object.setLong("entry_level", entry_node.level);
    auto& cfgObj = object.setObject("cfg");
    auto& cfg = _index.config();
    cfgObj.setLong("max_links_at_level_0", cfg.max_links_at_level_0());
    cfgObj.setLong("max_links_on_inserts", cfg.max_links_on_inserts());
    cfgObj.setLong("neighbors_to_explore_at_construction",
                   cfg.neighbors_to_explore_at_construction());
}

template <HnswIndexType type>
std::vector<std::string>
HnswIndexExplorer<type>::get_children_names() const
{
    return { LEVELS_STORE_NAME, LINKS_STORE_NAME, NODEID_STORE_NAME };
}

template <HnswIndexType type>
std::unique_ptr<vespalib::StateExplorer>
HnswIndexExplorer<type>::get_child(std::string_view name) const
{
    auto& graph = _index.get_graph();
    if (name == LEVELS_STORE_NAME) {
        return graph.levels_store.make_state_explorer();;
    } else if (name == LINKS_STORE_NAME) {
        return graph.links_store.make_state_explorer();
    } else if (name == NODEID_STORE_NAME) {
        if constexpr (type == HnswIndexType::MULTI) {
            return _index.get_id_mapping().make_state_explorer();
        }
    }
    return {};
}

template class HnswIndexExplorer<HnswIndexType::SINGLE>;
template class HnswIndexExplorer<HnswIndexType::MULTI>;

}
