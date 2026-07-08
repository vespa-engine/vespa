// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"

#include <vespa/searchlib/common/transient_bitvector_snapshot.h>

namespace search::attribute {

/**
 * Class for saving a single bool attribute.
 */
class SingleBoolAttributeSaver : public AttributeSaver {
    TransientBitVectorSnapshot _bv_snapshot;
    bool onSave(IAttributeSaveTarget& saveTarget) override;

public:
    SingleBoolAttributeSaver(const AttributeHeader& header, TransientBitVectorSnapshot bv_snapshot);
    ~SingleBoolAttributeSaver() override;
};

} // namespace search::attribute
