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
    using AtomicEntryRef = search::datastore::AtomicEntryRef;

    // This uses 10 bits for buffer id -> 1024 buffers.
    // As we have very short arrays we get less fragmentation with fewer and larger buffers.
    using EntryRefType = search::datastore::EntryRefT<22>;

    // Provides mapping from document id -> node reference.
    // The reference is used to lookup the node data in NodeStore.
    using NodeRefVector = vespalib::RcuVector<AtomicEntryRef>;

    // This stores the level arrays for all nodes.
    // Each node consists of an array of levels (from level 0 to n) where each entry is a reference to the link array at that level.
    using NodeStore = search::datastore::ArrayStore<AtomicEntryRef, EntryRefType>;
    using StoreConfig = search::datastore::ArrayStoreConfig;
    using LevelArrayRef = NodeStore::ConstArrayRef;

    // This stores all link arrays.
    // A link array consists of the document ids of the nodes a particular node is linked to.
    using LinkStore = search::datastore::ArrayStore<uint32_t, EntryRefType>;
    using LinkArrayRef = LinkStore::ConstArrayRef;

    NodeRefVector node_refs;
    NodeStore     nodes;
    LinkStore     links;
    uint32_t      entry_docid;
    int32_t       entry_level;

    HnswGraph();

    ~HnswGraph();

    void make_node_for_document(uint32_t docid, uint32_t num_levels);

    void remove_node_for_document(uint32_t docid);

    LevelArrayRef get_level_array(uint32_t docid) const {
        auto node_ref = node_refs[docid].load_acquire();
        assert(node_ref.valid());
        return nodes.get(node_ref);
    }

    LinkArrayRef get_link_array(uint32_t docid, uint32_t level) const {
        auto levels = get_level_array(docid);
        assert(level < levels.size());
        return links.get(levels[level].load_acquire());
    }
    
    void set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& new_links);

    void set_entry_node(uint32_t docid, int32_t level) {
        entry_docid = docid;
        entry_level = level;
    }

    size_t size() const { return node_refs.size(); }

    std::vector<uint32_t> level_histogram() const;
};

}
