// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <cstdint>
#include <memory>
#include <vector>

namespace vespalib::slime { struct Inserter; }

namespace search::fileutil { class LoadedBuffer; }

namespace search::tensor {

class NearestNeighborIndexSaver;

/**
 * Interface for an index that is used for (approximate) nearest neighbor search.
 */
class NearestNeighborIndex {
public:
    using generation_t = vespalib::GenerationHandler::generation_t;
    struct Neighbor {
        uint32_t docid;
        double distance;
        Neighbor(uint32_t id, double dist)
          : docid(id), distance(dist)
        {}
        Neighbor() : docid(0), distance(0.0) {}
    };
    virtual ~NearestNeighborIndex() {}
    virtual void add_document(uint32_t docid) = 0;
    virtual void remove_document(uint32_t docid) = 0;
    virtual void transfer_hold_lists(generation_t current_gen) = 0;
    virtual void trim_hold_lists(generation_t first_used_gen) = 0;
    virtual vespalib::MemoryUsage memory_usage() const = 0;
    virtual void get_state(const vespalib::slime::Inserter& inserter) const = 0;

    /**
     * Creates a saver that is used to save the index to binary form.
     *
     * This function is always called by the attribute write thread,
     * and the caller ensures that an attribute read guard is held during the lifetime of the saver.
     */
    virtual std::unique_ptr<NearestNeighborIndexSaver> make_saver() const = 0;
    virtual void load(const fileutil::LoadedBuffer& buf) = 0;

    virtual std::vector<Neighbor> find_top_k(uint32_t k,
                                             vespalib::tensor::TypedCells vector,
                                             uint32_t explore_k) const = 0;

    virtual const DistanceFunction *distance_function() const = 0;
};

}
