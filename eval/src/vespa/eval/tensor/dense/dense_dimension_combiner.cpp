// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dimension_combiner.h"
#include <cassert>

namespace vespalib::tensor {

DenseDimensionCombiner::~DenseDimensionCombiner() = default;

DenseDimensionCombiner::DenseDimensionCombiner(const eval::ValueType &lhs,
                                               const eval::ValueType &rhs)
  : _left(), _right(),
    _commonDims(),
    _outputIndex(0),
    _outputSize(1u),
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
                _left.totalSize *= cd.size;
                _right.totalSize *= cd.size;
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
                _outputSize *= ld.size;
                _left.totalSize *= ld.size;
                _left.dims.push_back(ld);
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
            _outputSize *= rd.size;
            _right.totalSize *= rd.size;
            _right.dims.push_back(rd);
        }
    }
}


} // namespace

