// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/typify.h>

namespace search::tensor {

/**
 * Class for storing serialized tensors in memory
 */
class StreamedValueStore : public TensorStore {
public:
    using RefType = vespalib::datastore::AlignedEntryRefT<22, 2>;
    using DataStoreType = vespalib::datastore::DataStoreT<RefType>;

    struct StreamedValueData {
        bool valid;
        vespalib::eval::TypedCells cells_ref;
        size_t num_subspaces;
        vespalib::ConstArrayRef<char> labels_buffer;
        operator bool() const { return valid; }
    };

    struct DataFromType {
        uint32_t num_mapped_dimensions;
        uint32_t dense_subspace_size;
        vespalib::eval::CellType cell_type;

        DataFromType(const vespalib::eval::ValueType& type)
          : num_mapped_dimensions(type.count_mapped_dimensions()),
            dense_subspace_size(type.dense_subspace_size()),
            cell_type(type.cell_type())
        {}
    };

private:
    DataStoreType _concreteStore;
    vespalib::datastore::BufferType<char> _bufferType;
    vespalib::eval::ValueType _tensor_type;
    DataFromType _data_from_type;
    
    void my_encode(const vespalib::eval::Value::Index &index,
                   vespalib::nbostream &target) const;

    std::pair<const char *, uint32_t> getRawBuffer(RefType ref) const;
    vespalib::datastore::Handle<char> allocRawBuffer(uint32_t size);
public:
    StreamedValueStore(const vespalib::eval::ValueType &tensor_type);
    virtual ~StreamedValueStore();

    virtual void holdTensor(EntryRef ref) override;
    virtual EntryRef move(EntryRef ref) override;

    StreamedValueData get_tensor_data(EntryRef ref) const;
    bool encode_tensor(EntryRef ref, vespalib::nbostream &target) const;

    EntryRef store_tensor(const vespalib::eval::Value &tensor);
    EntryRef store_encoded_tensor(vespalib::nbostream &encoded);
};


}
