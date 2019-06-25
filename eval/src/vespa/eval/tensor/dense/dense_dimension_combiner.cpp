// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dimension_combiner.h"
#include <cassert>

namespace vespalib::tensor {

DenseDimensionCombiner::~DenseDimensionCombiner() = default;

DenseDimensionCombiner::DenseDimensionCombiner(const eval::ValueType &lhs,
                                               const eval::ValueType &rhs)
  : _leftDims(), _rightDims(), _commonDims(),
    _leftIndex(0), _rightIndex(0), _outputIndex(0),
    _leftOnlySize(1u), _rightOnlySize(1u), _outputSize(1u),
    result_type(eval::ValueType::join(lhs, rhs))
{
    assert(lhs.is_dense());
    assert(rhs.is_dense());
    assert(result_type.is_dense());

    const auto &lDims = lhs.dimensions();
    const auto &rDims = rhs.dimensions();
    const auto &oDims = result_type.dimensions();

    size_t i = lDims.size();
    size_t j = rDims.size();
    size_t k = oDims.size();

    uint32_t lMul = 1;
    uint32_t rMul = 1;
    uint32_t oMul = 1;

    while (k-- > 0) {
        if ((i > 0) && (lDims[i-1].name == oDims[k].name)) {
            --i;
            // left dim match
            if ((j > 0) && (rDims[j-1].name == oDims[k].name)) {
                // both dim match
                --j;
                CommonDim cd;
                cd.idx = 0;
                cd.leftMultiplier = lMul;
                cd.rightMultiplier = rMul;
                cd.outputMultiplier = oMul;
                assert(lDims[i].size == oDims[k].size);
                assert(rDims[j].size == oDims[k].size);
                cd.size = oDims[k].size;
                lMul *= cd.size;
                rMul *= cd.size;
                oMul *= cd.size;
                _leftOnlySize *= cd.size;
                _rightOnlySize *= cd.size;
                _outputSize *= cd.size;
                _commonDims.push_back(cd);
            } else {
                SideDim ld;
                ld.idx = 0;
                ld.sideMultiplier = lMul;
                ld.outputMultiplier = oMul;
                assert(lDims[i].size == oDims[k].size);
                ld.size = oDims[k].size;
                lMul *= ld.size;
                oMul *= ld.size;
                _leftOnlySize *= ld.size;
                _outputSize *= ld.size;
                _leftDims.push_back(ld);
            }
        } else {
            // right dim match
            assert(j > 0);
            assert(rDims[j-1].name == oDims[k].name);
            --j;
            SideDim rd;
            rd.idx = 0;
            rd.sideMultiplier = rMul;
            rd.outputMultiplier = oMul;
            assert(rDims[j].size == oDims[k].size);
            rd.size = oDims[k].size;
            rMul *= rd.size;
            oMul *= rd.size;
            _rightOnlySize *= rd.size;
            _outputSize *= rd.size;
            _rightDims.push_back(rd);
        }
    }
}

void
DenseDimensionCombiner::dump() const
{
    fprintf(stderr, "DenseDimensionCombiner: %u * %u -> %u\n", _leftOnlySize, _rightOnlySize, _outputSize);
    for (const SideDim& ld : _leftDims) {
        fprintf(stderr, "ld curidx=%u (of %u) leftmul=%u outmul=%u\n",
                ld.idx, ld.size, ld.sideMultiplier, ld.outputMultiplier);
    }
    for (const SideDim& rd : _rightDims) {
        fprintf(stderr, "rd curidx=%u (of %u) rightmul=%u outmul=%u\n",
                rd.idx, rd.size, rd.sideMultiplier, rd.outputMultiplier);
    }
    for (const CommonDim& cd : _commonDims) {
        fprintf(stderr, "cd curidx=%u (of %u) leftmul=%u rightmul=%u outmul=%u\n",
                cd.idx, cd.size, cd.leftMultiplier, cd.rightMultiplier, cd.outputMultiplier);
    }
    fprintf(stderr, "Left Index: %u\n", _leftIndex);
    fprintf(stderr, "Right Index: %u\n", _rightIndex);
    fprintf(stderr, "Output Index: %u\n", _outputIndex);
}


} // namespace

