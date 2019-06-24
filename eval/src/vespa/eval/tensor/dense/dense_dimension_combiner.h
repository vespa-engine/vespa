// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::tensor {

class DenseDimensionCombiner {

    struct LeftDim {
        uint32_t idx;
        uint32_t size;
        uint32_t leftMultiplier;
        uint32_t outputMultiplier;
    };
    struct RightDim {
        uint32_t idx;
        uint32_t size;
        uint32_t rightMultiplier;
        uint32_t outputMultiplier;
    };
    struct CommonDim {
        uint32_t idx;
        uint32_t size;
        uint32_t leftMultiplier;
        uint32_t rightMultiplier;
        uint32_t outputMultiplier;
    };

    std::vector<LeftDim> _leftDims;
    std::vector<RightDim> _rightDims;
    std::vector<CommonDim> _commonDims;

    uint32_t _leftIndex;
    uint32_t _rightIndex;
    uint32_t _outputIndex;

    uint32_t _leftOnlySize;
    uint32_t _rightOnlySize;
    uint32_t _outputSize;

public:
    size_t leftIdx() const { return _leftIndex; }
    size_t rightIdx() const { return _rightIndex; }
    size_t outputIdx() const { return _outputIndex; }

    bool leftInRange() const { return _leftIndex < _leftOnlySize; }
    bool rightInRange() const { return _rightIndex < _rightOnlySize; }
    bool commonInRange() const { return _outputIndex < _outputSize; }

    void leftReset() {
        for (LeftDim& ld : _leftDims) {
            _leftIndex -= ld.idx * ld.leftMultiplier;
            _outputIndex -= ld.idx * ld.outputMultiplier;
            ld.idx = 0;
        }
        if (_leftIndex >= _leftOnlySize) _leftIndex -= _leftOnlySize;
    }

    void stepLeft() {
        size_t lim = _leftDims.size();
        for (size_t i = 0; i < lim; ++i) {
            LeftDim& ld = _leftDims[i];
            ld.idx++;
            _leftIndex += ld.leftMultiplier;
            _outputIndex += ld.outputMultiplier;
            if (ld.idx < ld.size) return;
            _leftIndex -= ld.idx * ld.leftMultiplier;
            _outputIndex -= ld.idx * ld.outputMultiplier;
            ld.idx = 0;
        }
        _leftIndex += _leftOnlySize;
    }


    void rightReset() {
        for (RightDim& rd : _rightDims) {
            _rightIndex -= rd.idx * rd.rightMultiplier;
            _outputIndex -= rd.idx * rd.outputMultiplier;
            rd.idx = 0;
        }
        if (_rightIndex >= _rightOnlySize) _rightIndex -= _rightOnlySize;
    }

    void stepRight() {
        size_t lim = _rightDims.size();
        for (size_t i = 0; i < lim; ++i) {
            RightDim& rd = _rightDims[i];
            rd.idx++;
            _rightIndex += rd.rightMultiplier;
            _outputIndex += rd.outputMultiplier;
            if (rd.idx < rd.size) return;
            _rightIndex -= rd.idx * rd.rightMultiplier;
            _outputIndex -= rd.idx * rd.outputMultiplier;
            rd.idx = 0;
        }
        _rightIndex += _rightOnlySize;
    }

    void commonReset() {
        for (CommonDim& cd : _commonDims) {
            _leftIndex -= cd.idx * cd.leftMultiplier;
            _rightIndex -= cd.idx * cd.rightMultiplier;
            _outputIndex -= cd.idx * cd.outputMultiplier;
            cd.idx = 0;
        }
        if (_outputIndex >= _outputSize) _outputIndex -= _outputSize;
    }

    void stepCommon() {
        size_t lim = _commonDims.size();
        for (size_t i = 0; i < lim; ++i) {
            CommonDim &cd = _commonDims[i];
            cd.idx++;
            _leftIndex += cd.leftMultiplier;
            _rightIndex += cd.rightMultiplier;
            _outputIndex += cd.outputMultiplier;
            if (cd.idx < cd.size) return;
            _leftIndex -= cd.idx * cd.leftMultiplier;
            _rightIndex -= cd.idx * cd.rightMultiplier;
            _outputIndex -= cd.idx * cd.outputMultiplier;
            cd.idx = 0;
        }
        _outputIndex += _outputSize;
    }

    DenseDimensionCombiner(const eval::ValueType &lhs, const eval::ValueType &rhs);

    ~DenseDimensionCombiner();

    void dump() const;
};

}
