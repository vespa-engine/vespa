// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_attribute.h"
#include <vespa/vespalib/stllike/allocator.h>

namespace search::attribute {

/**
 * Attribute vector storing a single raw value per document in streaming search.
 */
class SingleRawExtAttribute : public RawAttribute,
                              public IExtendAttribute
{
    std::vector<char, vespalib::allocator_large<char>>         _buffer;
    std::vector<uint32_t, vespalib::allocator_large<uint32_t>> _offsets;
public:
    SingleRawExtAttribute(const vespalib::string& name);
    ~SingleRawExtAttribute() override;
    void onCommit() override;
    void onUpdateStat() override;
    bool addDoc(DocId& docId) override;
    bool add(vespalib::ConstArrayRef<char> v, int32_t) override;
    vespalib::ConstArrayRef<char> get_raw(DocId docid) const override;
    IExtendAttribute* getExtendInterface() override;
};

}
