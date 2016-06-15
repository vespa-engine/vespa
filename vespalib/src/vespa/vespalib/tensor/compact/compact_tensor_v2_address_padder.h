// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compact_tensor_v2_address_builder.h"
#include "compact_tensor_v2_address_decoder.h"

namespace vespalib {
namespace tensor {


/**
 * This class transforms serialized compact tensor v2 addresses by padding
 * in "undefined" labels for new dimensions.
 */
class CompactTensorV2AddressPadder : public CompactTensorV2AddressBuilder
{
    enum class PadOp
    {
        PAD,
        COPY
    };

    std::vector<PadOp> _padOps;

public:
    CompactTensorV2AddressPadder(const TensorDimensions &resultDims,
                                 const TensorDimensions &inputDims)
        : CompactTensorV2AddressBuilder(),
          _padOps()
    {
        auto resultDimsItr = resultDims.cbegin();
        auto resultDimsItrEnd = resultDims.cend();
        for (auto &dim : inputDims) {
            while (resultDimsItr != resultDimsItrEnd && *resultDimsItr < dim) {
                _padOps.push_back(PadOp::PAD);
                ++resultDimsItr;
            }
            assert(resultDimsItr != resultDimsItrEnd && *resultDimsItr == dim);
            _padOps.push_back(PadOp::COPY);
            ++resultDimsItr;
        }
        while (resultDimsItr != resultDimsItrEnd) {
            _padOps.push_back(PadOp::PAD);
            ++resultDimsItr;
        }
    }

    void
    padAddress(CompactTensorAddressRef ref)
    {
        clear();
        CompactTensorV2AddressDecoder addr(ref);
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
};


} // namespace vespalib::tensor
} // namespace vespalib
