// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"

namespace search { class BitVector; }

namespace search::attribute {

/**
 * Class for saving a single bool attribute.
 */
class SingleBoolAttributeSaver : public AttributeSaver
{
    std::unique_ptr<const BitVector> _bv;
    bool onSave(IAttributeSaveTarget& saveTarget) override;
public:
    SingleBoolAttributeSaver(const AttributeHeader& header, std::unique_ptr<const BitVector> bv);
    ~SingleBoolAttributeSaver() override;
};

}
