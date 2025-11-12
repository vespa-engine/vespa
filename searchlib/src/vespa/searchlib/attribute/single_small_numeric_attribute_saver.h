// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"

namespace search::attribute {

/**
 * Class for saving a single value small numeric attribute.
 */
class SingleSmallNumericAttributeSaver : public AttributeSaver
{
    uint32_t              _num_docs;
    std::vector<uint32_t> _word_data;

    bool onSave(IAttributeSaveTarget& saveTarget) override;
public:
    SingleSmallNumericAttributeSaver(const attribute::AttributeHeader& header, uint32_t num_docs,
                                     std::vector<uint32_t> word_data);
    ~SingleSmallNumericAttributeSaver() override;
};

}
