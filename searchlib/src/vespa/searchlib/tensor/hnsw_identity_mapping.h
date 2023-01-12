// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <cstdint>
#include <cassert>

namespace vespalib::datastore { class CompactionStrategy; }

namespace search::tensor {

class HnswSimpleNode;

/*
 * Class used to maintain mapping from docid to nodeid for dense tensors
 * (one node per document).
 */
class HnswIdentityMapping {
    using generation_t = vespalib::GenerationHandler::generation_t;
    uint32_t _nodeid;
public:
    HnswIdentityMapping()
        : _nodeid(0u)
    {
    }
    vespalib::ConstArrayRef<uint32_t> allocate_ids(uint32_t docid, uint32_t subspaces) {
        assert(subspaces == 1u);
        _nodeid = docid;
        return {&_nodeid, 1};
    }
    vespalib::ConstArrayRef<uint32_t> get_ids(uint32_t docid) {
        _nodeid = docid;
        return {&_nodeid, 1};
    }
    void free_ids(uint32_t docid) { (void) docid; }
    void assign_generation(generation_t current_gen) { (void) current_gen; };
    void reclaim_memory(generation_t oldest_used_gen) { (void) oldest_used_gen; };
    void on_load(vespalib::ConstArrayRef<HnswSimpleNode> nodes) { (void) nodes; }
    vespalib::MemoryUsage memory_usage() const { return vespalib::MemoryUsage(); }
    vespalib::MemoryUsage update_stat(const vespalib::datastore::CompactionStrategy&) { return vespalib::MemoryUsage(); }
    static bool consider_compact() noexcept { return false; }
    static void compact_worst(const vespalib::datastore::CompactionStrategy&) {}
};

}
