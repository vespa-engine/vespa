// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/simple_value.h>

#include <vespa/eval/tensor/mixed/packed_mappings.h>
#include <vespa/eval/tensor/mixed/packed_mappings_builder.h>

namespace vespalib::eval::packed_mixed_tensor {

/**
 * An implementation of Value modeling a mixed tensor,
 * where all the data (cells and sparse address mappings)
 * can reside in a self-contained, contigous block of memory.
 * Currently must be built by a PackedMixedTensorBuilder.
 * Immutable (all data always const).
 **/
class PackedMixedTensor : public Value, public Value::Index
{
private:
    const ValueType _type;
    const TypedCells _cells;
    const PackedMappings _mappings;

    PackedMixedTensor(const ValueType &type,
                      TypedCells cells,
                      const PackedMappings &mappings);

    template<typename T> friend class PackedMixedTensorBuilder;

public:
    ~PackedMixedTensor() override;

    // Value API:
    const ValueType &type() const override { return _type; }
    const Value::Index &index() const override { return *this; }
    TypedCells cells() const override { return _cells; }

    MemoryUsage get_memory_usage() const override;

    // Value::Index API:
    size_t size() const override { return _mappings.size(); }
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;

    // memory management:
    static size_t add_for_alignment(size_t sz) {
        size_t unalign = sz & 15;
        return (unalign == 0) ? unalign : (16 - unalign);
    }
    static void operator delete(void *ptr, size_t sz) {
        if (sz != sizeof(PackedMixedTensor)) {
            abort();
        }
        size_t allocated_sz = ((PackedMixedTensor *)ptr)->get_memory_usage().usedBytes();
        ::operator delete(ptr, allocated_sz);
    }
};

} // namespace
