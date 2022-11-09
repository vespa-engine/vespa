// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_simple_node.h"
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::tensor {

/**
 * Storage of a hierarchical navigable small world graph (HNSW)
 * that is used for approximate K-nearest neighbor search.
 */
struct HnswGraph {
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;

    // This uses 10 bits for buffer id -> 1024 buffers.
    // As we have very short arrays we get less fragmentation with fewer and larger buffers.
    using LevelArrayEntryRefType = vespalib::datastore::EntryRefT<22>;

    // This uses 12 bits for buffer id -> 4096 buffers.
    using LinkArrayEntryRefType = vespalib::datastore::EntryRefT<20>;

    using NodeType = HnswSimpleNode;

    // Provides mapping from document id -> node reference.
    // The reference is used to lookup the node data in NodeStore.
    using NodeRefVector = vespalib::RcuVector<NodeType>;
    using NodeRef = vespalib::datastore::EntryRef;

    // This stores the level arrays for all nodes.
    // Each node consists of an array of levels (from level 0 to n) where each entry is a reference to the link array at that level.
    using NodeStore = vespalib::datastore::ArrayStore<AtomicEntryRef, LevelArrayEntryRefType>;
    using LevelArrayRef = NodeStore::ConstArrayRef;

    // This stores all link arrays.
    // A link array consists of the document ids of the nodes a particular node is linked to.
    using LinkStore = vespalib::datastore::ArrayStore<uint32_t, LinkArrayEntryRefType>;
    using LinkArrayRef = LinkStore::ConstArrayRef;

    NodeRefVector node_refs;
    std::atomic<uint32_t> node_refs_size;
    NodeStore     nodes;
    LinkStore     links;

    std::atomic<uint64_t> entry_nodeid_and_level;

    HnswGraph();
    ~HnswGraph();

    NodeRef make_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, uint32_t num_levels);

    void remove_node(uint32_t nodeid);

    void trim_node_refs_size();

    NodeRef get_node_ref(uint32_t nodeid) const {
        return node_refs.get_elem_ref(nodeid).ref().load_relaxed(); // Called from writer only
    }

    NodeRef acquire_node_ref(uint32_t nodeid) const {
        return node_refs.acquire_elem_ref(nodeid).ref().load_acquire();
    }

    bool still_valid(uint32_t nodeid, NodeRef node_ref) const {
        return node_ref.valid() && (acquire_node_ref(nodeid) == node_ref);
    }

    LevelArrayRef get_level_array(NodeRef node_ref) const {
        if (node_ref.valid()) {
            return nodes.get(node_ref);
        }
        return LevelArrayRef();
    }

    LevelArrayRef get_level_array(uint32_t nodeid) const {
        auto node_ref = get_node_ref(nodeid);
        return get_level_array(node_ref);
    }

    LevelArrayRef acquire_level_array(uint32_t nodeid) const {
        auto node_ref = acquire_node_ref(nodeid);
        return get_level_array(node_ref);
    }

    LinkArrayRef get_link_array(LevelArrayRef levels, uint32_t level) const {
        if (level < levels.size()) {
            auto links_ref = levels[level].load_acquire();
            if (links_ref.valid()) {
                return links.get(links_ref);
            }
        }
        return LinkArrayRef();
    }

    LinkArrayRef get_link_array(uint32_t nodeid, uint32_t level) const {
        auto levels = get_level_array(nodeid);
        return get_link_array(levels, level);
    }

    LinkArrayRef acquire_link_array(uint32_t nodeid, uint32_t level) const {
        auto levels = acquire_level_array(nodeid);
        return get_link_array(levels, level);
    }

    LinkArrayRef get_link_array(NodeRef node_ref, uint32_t level) const {
        auto levels = get_level_array(node_ref);
        return get_link_array(levels, level);
    }

    void set_link_array(uint32_t nodeid, uint32_t level, const LinkArrayRef& new_links);

    struct EntryNode {
        uint32_t nodeid;
        NodeRef node_ref;
        int32_t level;
        EntryNode()
          : nodeid(0), // Note that nodeid 0 is reserved and never used
            node_ref(),
            level(-1)
        {}
        EntryNode(uint32_t nodeid_in, NodeRef node_ref_in, int32_t level_in)
          : nodeid(nodeid_in),
            node_ref(node_ref_in),
            level(level_in)
        {}
    };

    void set_entry_node(EntryNode node);

    uint64_t get_entry_atomic() const {
        return entry_nodeid_and_level.load(std::memory_order_acquire);
    }

    EntryNode get_entry_node() const {
        EntryNode entry;
        while (true) {
            uint64_t value = get_entry_atomic();
            entry.nodeid = (uint32_t)value;
            entry.node_ref = acquire_node_ref(entry.nodeid);
            entry.level = (int32_t)(value >> 32);
            if ((entry.nodeid == 0)
                && (entry.level == -1)
                && ! entry.node_ref.valid())
            {
                // invalid in every way
                return entry;
            }
            if ((entry.nodeid > 0)
                && (entry.level > -1)
                && entry.node_ref.valid()
                && (get_entry_atomic() == value))
            {
                // valid in every way
                return entry;
            }
        }
    }

    size_t size() const { return node_refs_size.load(std::memory_order_acquire); }

    struct Histograms {
        std::vector<uint32_t> level_histogram;
        std::vector<uint32_t> links_histogram;
    };
    Histograms histograms() const;
};

}
