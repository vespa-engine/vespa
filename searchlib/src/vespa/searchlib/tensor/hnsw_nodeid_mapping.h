// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generation_hold_list.h>
#include <vespa/vespalib/util/growstrategy.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <cstdint>
#include <vector>

namespace search::tensor {

class HnswNode;

/**
 * Class used to keep track of the mapping from docid to array of nodeids.
 * A nodeid is an identifier for a node in the HNSW graph that represents a single vector.
 *
 * The nodeids are allocated by this class.
 * Nodeids that are freed are reused when no reader threads are accessing them (after a hold cycle).
 *
 * Note: Only the writer thread should use this class.
 */
class HnswNodeidMapping {
private:
    using generation_t = vespalib::GenerationHandler::generation_t;
    using NodeidStore = vespalib::datastore::ArrayStore<uint32_t>;
    using NodeidHoldList = vespalib::GenerationHoldList<uint32_t, false, true>;
    using NodeidFreeList = std::vector<uint32_t>;

    // Maps from docid to EntryRef used to get the array of nodeids from the NodeidStore.
    std::vector<vespalib::datastore::EntryRef> _refs;
    vespalib::GrowStrategy _grow_strategy;
    uint32_t _nodeid_limit;
    NodeidStore _nodeids;
    NodeidHoldList _hold_list;
    NodeidFreeList _free_list;

    void ensure_refs_size(uint32_t docid);
    uint32_t allocate_id();
    void allocate_docid_to_nodeids_mapping(std::vector<uint32_t> histogram);
    void populate_docid_to_nodeids_mapping_and_free_list(vespalib::ConstArrayRef<HnswNode> nodes);
    void assert_all_subspaces_have_valid_nodeid(uint32_t docid_limit);

public:
    HnswNodeidMapping();
    ~HnswNodeidMapping();
    vespalib::ConstArrayRef<uint32_t> allocate_ids(uint32_t docid, uint32_t subspaces);
    vespalib::ConstArrayRef<uint32_t> get_ids(uint32_t docid) const;
    void free_ids(uint32_t docid);

    void assign_generation(generation_t current_gen);
    void reclaim_memory(generation_t oldest_used_gen);
    void on_load(vespalib::ConstArrayRef<HnswNode> nodes);
    vespalib::AddressSpace address_space_usage() const { return _nodeids.addressSpaceUsage(); }
    vespalib::MemoryUsage memory_usage() const;
    vespalib::MemoryUsage update_stat(const vespalib::datastore::CompactionStrategy& compaction_strategy);
    bool consider_compact() const noexcept { return _nodeids.consider_compact(); }
    void compact_worst(const vespalib::datastore::CompactionStrategy& compaction_strategy);
};

}

namespace vespalib {
    extern template class GenerationHoldList<uint32_t, false, true>;
}
