// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/typify.h>

namespace search::tensor {

/**
 * Class for storing tensors in memory, with a special serialization
 * format that can be used directly to make a StreamedValueView.
 *
 * The tensor type is owned by the store itself and will not be
 * serialized at all.
 *
 * The parameters for serialization (see DataFromType) are:
 * - number of mapped dimensions [MD]
 * - dense subspace size [DS]
 * - size of each cell [CS] - currently 4 (float) or 8 (double)
 * - alignment for cells - currently 4 (float) or 8 (double)
 * While the tensor value to be serialized has:
 * - number of dense subspaces [ND]
 * - labels for dense subspaces, ND * MD strings
 * - cell values, ND * DS cells (each either float or double)
 * The serialization format looks like:
 *
 *   [bytes]     : [format]                : [description]
 *      4        :  n.b.o. uint32_ t       : num cells = ND * DS
 *  CS * ND * DS :  native float or double : cells
 *   (depends)   :  n.b.o. strings         : ND * MD label strings
 *
 * Here, n.b.o. means network byte order, or more precisely
 * it's the format vespalib::nbostream uses for the given data type,
 * including strings (where exact format depends on the string length).
 * Note that the only unpredictably-sized data (the labels) are kept
 * last.
 * If we ever make a "hbostream" which uses host byte order, we
 * could switch to that instead since these data are only kept in
 * memory.
 */
class StreamedValueStore : public TensorStore {
public:
    using RefType = vespalib::datastore::AlignedEntryRefT<22, 3>;
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
    
    void serialize_labels(const vespalib::eval::Value::Index &index,
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
