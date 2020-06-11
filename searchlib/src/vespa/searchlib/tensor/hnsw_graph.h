// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::tensor {

/**
 * Stroage of a hierarchical navigable small world graph (HNSW)
 * that is used for approximate K-nearest neighbor search.
 */
struct HnswGraph {
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;

    // This uses 10 bits for buffer id -> 1024 buffers.
    // As we have very short arrays we get less fragmentation with fewer and larger buffers.
    using EntryRefType = vespalib::datastore::EntryRefT<22>;

    // Provides mapping from document id -> node reference.
    // The reference is used to lookup the node data in NodeStore.
    using NodeRefVector = vespalib::RcuVector<AtomicEntryRef>;
    using NodeRef = vespalib::datastore::EntryRef;

    // This stores the level arrays for all nodes.
    // Each node consists of an array of levels (from level 0 to n) where each entry is a reference to the link array at that level.
    using NodeStore = vespalib::datastore::ArrayStore<AtomicEntryRef, EntryRefType>;
    using StoreConfig = vespalib::datastore::ArrayStoreConfig;
    using LevelArrayRef = NodeStore::ConstArrayRef;

    // This stores all link arrays.
    // A link array consists of the document ids of the nodes a particular node is linked to.
    using LinkStore = vespalib::datastore::ArrayStore<uint32_t, EntryRefType>;
    using LinkArrayRef = LinkStore::ConstArrayRef;

    NodeRefVector node_refs;
    NodeStore     nodes;
    LinkStore     links;

    std::atomic<uint64_t> entry_docid_and_level;

    HnswGraph();

    ~HnswGraph();

    void make_node_for_document(uint32_t docid, uint32_t num_levels);

    void remove_node_for_document(uint32_t docid);

    NodeRef get_node_ref(uint32_t docid) const {
        return node_refs[docid].load_acquire();
    }

    LevelArrayRef get_level_array(uint32_t docid) const {
        auto node_ref = get_node_ref(docid);
        assert(node_ref.valid());
        return nodes.get(node_ref);
    }

    LevelArrayRef get_level_array(uint32_t docid, NodeRef cached_ref) const {
        auto node_ref = get_node_ref(docid);
        if (node_ref.valid() && (cached_ref == node_ref)) {
            return nodes.get(node_ref);
        }
        return LevelArrayRef();
    }

    LinkArrayRef get_link_array(uint32_t docid, uint32_t level) const {
        auto levels = get_level_array(docid);
        assert(level < levels.size());
        return links.get(levels[level].load_acquire());
    }

    LinkArrayRef get_link_array(uint32_t docid, LevelArrayRef cached_levels, uint32_t level) const {
        auto node_ref = get_node_ref(docid);
        auto levels = get_level_array(docid, node_ref);
        if ((levels == cached_levels) && (level < levels.size())) {
            return links.get(levels[level].load_acquire());
        }
        return LinkArrayRef();
    }

    void set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& new_links);

    struct EntryNode {
        uint32_t docid;
        int32_t level;
        EntryNode()
          : docid(0), // Note that docid 0 is reserved and never used
            level(-1)
        {}
        EntryNode(uint32_t docid_in, int32_t level_in)
          : docid(docid_in),
            level(level_in)
        {}
    };

    void set_entry_node(EntryNode node) {
        uint64_t value = node.level;
        value <<= 32;
        value |= node.docid;
        entry_docid_and_level.store(value, std::memory_order_release);
    }

    EntryNode get_entry_node() const {
        EntryNode entry;
        uint64_t value = entry_docid_and_level.load(std::memory_order_acquire);
        entry.docid = (uint32_t)value;
        entry.level = (int32_t)(value >> 32);
        return entry;
    }

    size_t size() const { return node_refs.size(); }

    struct Histograms {
        std::vector<uint32_t> level_histogram;
        std::vector<uint32_t> links_histogram;
    };
    Histograms histograms() const;
};

}
