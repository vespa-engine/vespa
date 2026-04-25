// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/context.h>
#include <vespa/document/base/documentid.h>
#include <cstdint>

namespace search::attribute { class IAttributeVector; }

namespace proton {

class CachedSelect;

namespace select { struct Guards; }

class SelectContext : public document::select::Context
{
public:
    SelectContext(const CachedSelect &cachedSelect);
    SelectContext(const SelectContext&) = delete;
    SelectContext(SelectContext&&) = delete;
    ~SelectContext();
    SelectContext& operator=(const SelectContext&) = delete;
    SelectContext& operator=(SelectContext&&) = delete;

    void getAttributeGuards();
    void dropAttributeGuards();

    uint32_t             _lid;
    document::DocumentId _document_id_copy;

    const search::attribute::IAttributeVector& guarded_attribute_at_index(uint32_t index) const noexcept;
private:
    std::unique_ptr<select::Guards> _guards;
    const CachedSelect &_cachedSelect;
};

} // namespace proton

