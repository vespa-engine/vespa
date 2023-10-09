// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::multivalueattributesaver {

/*
 * Class to write to count files for multivalue attributes (.idx suffix).
 */
class CountWriter
{
    std::unique_ptr<search::BufferWriter> _countWriter;
    uint64_t _cnt;

public:
    CountWriter(IAttributeSaveTarget &saveTarget);
    ~CountWriter();

    void writeCount(uint32_t count);
};

/*
 * Class to write to weight files (or not) for multivalue attributes.
 */
template <bool hasWeight>
class WeightWriter;

/*
 * Class to write to weight files for multivalue attributes (.weight suffix).
 */
template <>
class WeightWriter<true>
{
    std::unique_ptr<search::BufferWriter> _weightWriter;

public:
    WeightWriter(IAttributeSaveTarget &saveTarget)
        : _weightWriter(saveTarget.weightWriter().allocBufferWriter())
    {}

    ~WeightWriter() {
        _weightWriter->flush();
    }

    template <typename MultiValueT>
    void
    writeWeights(vespalib::ConstArrayRef<MultiValueT> values) {
        for (const MultiValueT &valueRef : values) {
            int32_t weight = valueRef.weight();
            _weightWriter->write(&weight, sizeof(int32_t));
        }
    }
};

/*
 * Class to not write to weight files for multivalue attributes.
 */
template <>
class WeightWriter<false>
{
public:
    WeightWriter(IAttributeSaveTarget &) {}

    ~WeightWriter() {}

    template <typename MultiValueT>
    void writeWeights(vespalib::ConstArrayRef<MultiValueT>) {}
};

}
