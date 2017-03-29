// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributesavetarget.h"

namespace search {

namespace multivalueattributesaver {

/*
 * Class to write to count files for multivalue attributes (.idx suffix).
 */
class CountWriter
{
    std::unique_ptr<search::BufferWriter> _countWriter;
    uint64_t _cnt;

public:
    CountWriter(IAttributeSaveTarget &saveTarget)
        : _countWriter(saveTarget.idxWriter().allocBufferWriter()),
          _cnt(0)
    {
        uint32_t initialCount = 0;
        _countWriter->write(&initialCount, sizeof(uint32_t));
    }

    ~CountWriter()
    {
        _countWriter->flush();
    }

    void
    writeCount(uint32_t count) {
        _cnt += count;
        uint32_t cnt32 = static_cast<uint32_t>(_cnt);
        _countWriter->write(&cnt32, sizeof(uint32_t));
    }
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
    {
    }

    ~WeightWriter()
    {
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
    WeightWriter(IAttributeSaveTarget &)
    {
    }

    ~WeightWriter()
    {
    }

    template <typename MultiValueT>
    void
    writeWeights(vespalib::ConstArrayRef<MultiValueT>) {
    }
};

} // namespace search::multivalueattributesaver

} // namespace search
