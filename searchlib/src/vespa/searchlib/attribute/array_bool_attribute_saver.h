// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "save_utils.h"

namespace search { class BufferWriter; }

namespace search::attribute {

class RawBufferStore;

/**
 * Class for saving an array bool attribute.
 */
class ArrayBoolAttributeSaver : public AttributeSaver
{
    EntryRefVector        _ref_vector;
    const RawBufferStore& _raw_store;

    void save_raw_store(BufferWriter& writer) const;
    bool onSave(IAttributeSaveTarget& saveTarget) override;
public:
    ArrayBoolAttributeSaver(vespalib::GenerationHandler::Guard&& guard,
                            const attribute::AttributeHeader& header,
                            EntryRefVector&& ref_vector,
                            const RawBufferStore& raw_store);
    ~ArrayBoolAttributeSaver();
};

}
