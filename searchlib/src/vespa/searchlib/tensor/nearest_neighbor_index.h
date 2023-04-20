// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"
#include "prepare_result.h"
#include "vector_bundle.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <cstdint>
#include <memory>
#include <vector>

class FastOS_FileInterface;

namespace vespalib::datastore {
class CompactionSpec;
class CompactionStrategy;
}
namespace vespalib::slime { struct Inserter; }

namespace search::fileutil { class LoadedBuffer; }

namespace search { class AddressSpaceUsage; }
namespace search::queryeval { class GlobalFilter; }

namespace search::tensor {

class NearestNeighborIndexLoader;
class NearestNeighborIndexSaver;

/**
 * Interface for an index that is used for (approximate) nearest neighbor search.
 */
class NearestNeighborIndex {
public:
    using GlobalFilter = search::queryeval::GlobalFilter;
    using CompactionSpec = vespalib::datastore::CompactionSpec;
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    using generation_t = vespalib::GenerationHandler::generation_t;
    struct Neighbor {
        uint32_t docid;
        double distance;
        Neighbor(uint32_t id, double dist) noexcept
          : docid(id), distance(dist)
        {}
        Neighbor() noexcept : docid(0), distance(0.0) {}
        bool operator==(const Neighbor& rhs) const {
            return docid == rhs.docid && distance == rhs.distance;
        }
    };
    virtual ~NearestNeighborIndex() = default;
    virtual void add_document(uint32_t docid) = 0;

    /**
     * Performs the prepare step in a two-phase operation to add a document to the index.
     *
     * This function can be called by any thread.
     * The document to add is represented by the given vector as it is _not_ stored in the enclosing tensor attribute at this point in time.
     * It should return the result of the costly and non-modifying part of this operation.
     * The given read guard must be kept in the result.
     */
    virtual std::unique_ptr<PrepareResult> prepare_add_document(uint32_t docid,
                                                                VectorBundle vectors,
                                                                vespalib::GenerationHandler::Guard read_guard) const = 0;
    /**
     * Performs the complete step in a two-phase operation to add a document to the index.
     *
     * This function is only called by the attribute writer thread.
     * It uses the result from the prepare step to do the modifying changes.
     */
    virtual void complete_add_document(uint32_t docid, std::unique_ptr<PrepareResult> prepare_result) = 0;

    virtual void remove_document(uint32_t docid) = 0;
    virtual void assign_generation(generation_t current_gen) = 0;
    virtual void reclaim_memory(generation_t first_used_gen) = 0;
    virtual bool consider_compact(const CompactionStrategy& compaction_strategy) = 0;
    virtual vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy) = 0;
    virtual vespalib::MemoryUsage memory_usage() const = 0;
    virtual void populate_address_space_usage(search::AddressSpaceUsage& usage) const = 0;
    virtual void get_state(const vespalib::slime::Inserter& inserter) const = 0;
    virtual void shrink_lid_space(uint32_t doc_id_limit) = 0;

    /**
     * Creates a saver that is used to save the index to binary form.
     *
     * This function is always called by the attribute write thread,
     * and the caller ensures that an attribute read guard is held during the lifetime of the saver.
     */
    virtual std::unique_ptr<NearestNeighborIndexSaver> make_saver() const = 0;

    /**
     * Creates a loader that is used to load the index from the given file.
     *
     * This might throw std::runtime_error.
     */
    virtual std::unique_ptr<NearestNeighborIndexLoader> make_loader(FastOS_FileInterface& file) = 0;

    virtual std::vector<Neighbor> find_top_k(uint32_t k,
                                             const BoundDistanceFunction &df,
                                             uint32_t explore_k,
                                             double distance_threshold) const = 0;

    // only return neighbors where the corresponding filter bit is set
    virtual std::vector<Neighbor> find_top_k_with_filter(uint32_t k,
                                                         const BoundDistanceFunction &df,
                                                         const GlobalFilter &filter,
                                                         uint32_t explore_k,
                                                         double distance_threshold) const = 0;

    virtual DistanceFunctionFactory &distance_function_factory() const = 0;
};

}
