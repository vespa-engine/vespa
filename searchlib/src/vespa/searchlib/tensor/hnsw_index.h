// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_base.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::tensor {

/**
 * Concrete implementation of a hierarchical navigable small world graph (HNSW)
 * that is used for approximate K-nearest neighbor search.
 *
 * See HnswIndexBase for more details.
 *
 * The FloatType template argument specifies the data type used in the vectors (4 byte float or 8 byte double).
 */
template <typename FloatType = float>
class HnswIndex : public HnswIndexBase {
private:
    using Vector = vespalib::ConstArrayRef<FloatType>;

    inline Vector get_vector(uint32_t docid) const {
        return _vectors.get_vector(docid).template typify<FloatType>();
    }

    double calc_distance(uint32_t lhs_docid, uint32_t rhs_docid) const override;
    double calc_distance(const Vector& lhs, uint32_t rhs_docid) const;
    void search_layer(const Vector& input, uint32_t neighbors_to_find, FurthestPriQ& found_neighbors, uint32_t level);

public:
    HnswIndex(const DocVectorAccess& vectors, const Config& cfg);
    ~HnswIndex() override;

    void add_document(uint32_t docid) override;
    void remove_document(uint32_t docid) override;
};

template class HnswIndex<float>;
template class HnswIndex<double>;

}

