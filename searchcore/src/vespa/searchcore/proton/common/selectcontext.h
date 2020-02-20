// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/context.h>

namespace search::attribute { class IAttributeVector; }

namespace proton {

class CachedSelect;

namespace select { struct Guards; }

class SelectContext : public document::select::Context
{
public:
    SelectContext(const CachedSelect &cachedSelect);
    ~SelectContext();

    void getAttributeGuards();
    void dropAttributeGuards();

    uint32_t _docId;

    const search::attribute::IAttributeVector& guarded_attribute_at_index(uint32_t index) const noexcept;
private:
    std::unique_ptr<select::Guards> _guards;
    const CachedSelect &_cachedSelect;
};

} // namespace proton

