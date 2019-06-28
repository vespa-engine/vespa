// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

class DenseDimensionCombiner {

    struct SideDim {
        uint32_t idx;
        uint32_t size;
        uint32_t sideMultiplier;
        uint32_t outputMultiplier;
    };
    struct CommonDim {
        uint32_t idx;
        uint32_t size;
        uint32_t leftMultiplier;
        uint32_t rightMultiplier;
        uint32_t outputMultiplier;
    };

    struct SideDims {
        std::vector<SideDim> dims;
        uint32_t index;
        uint32_t totalSize;

        SideDims() : dims(), index(0), totalSize(1u) {}

        void reset(uint32_t &outIndex) {
            for (SideDim& d : dims) {
                index -= d.idx * d.sideMultiplier;
                outIndex -= d.idx * d.outputMultiplier;
                d.idx = 0;
            }
            if (index >= totalSize) {
                index -= totalSize;
            }
        }
        void step(uint32_t &outIndex) {
            for (SideDim& d : dims) {
                d.idx++;
                index += d.sideMultiplier;
                outIndex += d.outputMultiplier;
                if (d.idx < d.size) return;
                index -= d.idx * d.sideMultiplier;
                outIndex -= d.idx * d.outputMultiplier;
                d.idx = 0;
            }
            index += totalSize;
        }
    };
    SideDims _left;
    SideDims _right;
    std::vector<CommonDim> _commonDims;
    uint32_t _outputIndex;
    uint32_t _outputSize;

public:
    size_t leftIdx() const { return _left.index; }
    size_t rightIdx() const { return _right.index; }
    size_t outputIdx() const { return _outputIndex; }

    bool leftInRange() const { return _left.index < _left.totalSize; }
    bool rightInRange() const { return _right.index < _right.totalSize; }
    bool commonInRange() const { return _outputIndex < _outputSize; }

    void leftReset() { _left.reset(_outputIndex); }
    void stepLeft() { _left.step(_outputIndex); }

    void rightReset() { _right.reset(_outputIndex); }
    void stepRight() { _right.step(_outputIndex); }

    void commonReset() {
        for (CommonDim& cd : _commonDims) {
            _left.index -= cd.idx * cd.leftMultiplier;
            _right.index -= cd.idx * cd.rightMultiplier;
            _outputIndex -= cd.idx * cd.outputMultiplier;
            cd.idx = 0;
        }
        if (_outputIndex >= _outputSize) {
            _outputIndex -= _outputSize;
        }
    }

    void stepCommon() {
        size_t lim = _commonDims.size();
        for (size_t i = 0; i < lim; ++i) {
            CommonDim &cd = _commonDims[i];
            cd.idx++;
            _left.index += cd.leftMultiplier;
            _right.index += cd.rightMultiplier;
            _outputIndex += cd.outputMultiplier;
            if (cd.idx < cd.size) return;
            _left.index -= cd.idx * cd.leftMultiplier;
            _right.index -= cd.idx * cd.rightMultiplier;
            _outputIndex -= cd.idx * cd.outputMultiplier;
            cd.idx = 0;
        }
        _outputIndex += _outputSize;
    }

    const eval::ValueType result_type;

    DenseDimensionCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs);

    ~DenseDimensionCombiner();
};

}
