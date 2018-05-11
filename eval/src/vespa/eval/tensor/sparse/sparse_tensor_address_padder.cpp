// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_padder.h"
#include "sparse_tensor_address_decoder.h"
#include <cassert>

namespace vespalib::tensor {

SparseTensorAddressPadder::SparseTensorAddressPadder(const eval::ValueType &resultType,
                                                     const eval::ValueType &inputType)
    : SparseTensorAddressBuilder(),
      _padOps()
{
    auto resultDimsItr = resultType.dimensions().cbegin();
    auto resultDimsItrEnd = resultType.dimensions().cend();
    for (auto &dim : inputType.dimensions()) {
        while (resultDimsItr != resultDimsItrEnd &&
               resultDimsItr->name < dim.name) {
            _padOps.push_back(PadOp::PAD);
            ++resultDimsItr;
        }
        assert(resultDimsItr != resultDimsItrEnd &&
               resultDimsItr->name == dim.name);
        _padOps.push_back(PadOp::COPY);
        ++resultDimsItr;
    }
    while (resultDimsItr != resultDimsItrEnd) {
        _padOps.push_back(PadOp::PAD);
        ++resultDimsItr;
    }
}

SparseTensorAddressPadder::~SparseTensorAddressPadder() = default;

void
SparseTensorAddressPadder::padAddress(SparseTensorAddressRef ref)
{
    clear();
    SparseTensorAddressDecoder addr(ref);
    for (auto op : _padOps) {
        switch (op) {
        case PadOp::PAD:
            addUndefined();
            break;
        default:
            add(addr.decodeLabel());
        }
    }
    assert(!addr.valid());
}

}

