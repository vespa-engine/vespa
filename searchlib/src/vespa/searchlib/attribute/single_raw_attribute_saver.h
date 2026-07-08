// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "save_utils.h"

namespace search {
class BufferWriter;
}

namespace search::attribute {

class RawBufferStore;

/**
 * Class for saving a single raw attribute.
 */
class SingleRawAttributeSaver : public AttributeSaver {
    EntryRefVectorSnapshot _ref_vector_snapshot;
    const RawBufferStore&  _raw_store;

    void save_raw_store(BufferWriter& writer) const;
    bool onSave(IAttributeSaveTarget& saveTarget) override;

public:
    SingleRawAttributeSaver(vespalib::GenerationGuard&& guard, const attribute::AttributeHeader& header,
                            EntryRefVectorSnapshot&& ref_vector_snapshot, const RawBufferStore& raw_store);
    ~SingleRawAttributeSaver();
};

} // namespace search::attribute
