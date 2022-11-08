// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace vespalib { class Executor; }

namespace search::tensor {

class BlobSequenceReader;
class DenseTensorStore;
class NearestNeighborIndex;
class TensorAttribute;
class TensorStore;

/**
 * Class for loading a tensor attribute.
 * Will also load the nearest neighbor index.
 */
class TensorAttributeLoader {
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using GenerationHandler = vespalib::GenerationHandler;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;
    TensorAttribute&      _attr;
    GenerationHandler&    _generation_handler;
    RefVector&            _ref_vector;
    TensorStore&          _store;
    NearestNeighborIndex* _index;

    void load_dense_tensor_store(BlobSequenceReader& reader, uint32_t docid_limit, DenseTensorStore& dense_store);
    void load_tensor_store(BlobSequenceReader& reader, uint32_t docid_limit);
    void build_index(vespalib::Executor* executor, uint32_t docid_limit);
    bool load_index();

public:
    TensorAttributeLoader(TensorAttribute& attr, GenerationHandler& generation_handler, RefVector& ref_vector, TensorStore& store, NearestNeighborIndex* index);
    ~TensorAttributeLoader();
    bool on_load(vespalib::Executor* executor);
};

}

