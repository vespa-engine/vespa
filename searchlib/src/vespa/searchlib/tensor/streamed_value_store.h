// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_store.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/streamed/streamed_value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/shared_string_repo.h>

namespace search::tensor {

/**
 * Class for StreamedValue tensors in memory.
 */
class StreamedValueStore : public TensorStore {
public:
    using Value = vespalib::eval::Value;
    using ValueType = vespalib::eval::ValueType;
    using Handles = vespalib::SharedStringRepo::Handles;
    using MemoryUsage = vespalib::MemoryUsage;

    // interface for tensor entries
    struct TensorEntry {
        using SP = std::shared_ptr<TensorEntry>;
        virtual Value::UP create_fast_value_view(const ValueType &type_ref) const = 0;
        virtual void encode_value(const ValueType &type, vespalib::nbostream &target) const = 0;
        virtual MemoryUsage get_memory_usage() const = 0;
        virtual ~TensorEntry();
        static TensorEntry::SP create_shared_entry(const Value &value);
    };

    // implementation of tensor entries
    template <typename CT>
    struct TensorEntryImpl : public TensorEntry {
        Handles handles;
        std::vector<CT> cells;
        TensorEntryImpl(const Value &value, size_t num_mapped, size_t dense_size);
        Value::UP create_fast_value_view(const ValueType &type_ref) const override;
        void encode_value(const ValueType &type, vespalib::nbostream &target) const override;
        MemoryUsage get_memory_usage() const override;
        ~TensorEntryImpl() override;
    };

private:
    // Note: Must use SP (instead of UP) because of fallbackCopy() and initializeReservedElements() in BufferType,
    //       and implementation of move().
    using TensorStoreType = vespalib::datastore::DataStore<TensorEntry::SP>;

    class TensorBufferType : public vespalib::datastore::BufferType<TensorEntry::SP> {
    private:
        using ParentType = BufferType<TensorEntry::SP>;
        using ParentType::_emptyEntry;
        using CleanContext = typename ParentType::CleanContext;
    public:
        TensorBufferType() noexcept;
        void cleanHold(void* buffer, size_t offset, ElemCount num_elems, CleanContext clean_ctx) override;
    };
    TensorStoreType _concrete_store;
    const vespalib::eval::ValueType _tensor_type;
    EntryRef add_entry(TensorEntry::SP tensor);
public:
    StreamedValueStore(const vespalib::eval::ValueType &tensor_type);
    ~StreamedValueStore() override;

    using RefType = TensorStoreType::RefType;

    void holdTensor(EntryRef ref) override;
    EntryRef move(EntryRef ref) override;

    const TensorEntry * get_tensor_entry(EntryRef ref) const;
    bool encode_tensor(EntryRef ref, vespalib::nbostream &target) const;

    EntryRef store_tensor(const vespalib::eval::Value &tensor);
    EntryRef store_encoded_tensor(vespalib::nbostream &encoded);
};


}
