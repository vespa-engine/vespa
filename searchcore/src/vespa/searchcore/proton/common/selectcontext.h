// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/context.h>
#include <vespa/document/base/documentid.h>
#include <cstdint>

namespace search::attribute { class IAttributeVector; }

namespace proton {

class CachedSelect;

namespace select { struct Guards; }

/*
 * This class contains information about the current document in a document select expression used for visiting.
 * Nodes in the parsed document select expression use the context to access the values needed for evaluating the
 * expression. It owns the read guards for the attribute vectors being accessed by the selection expression and
 * possibly also a copy of a document id from document meta store. This can be used to evaluate the document
 * selection expression without retrieving the full document from disk, and get a full or partial result.
 */
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

    uint32_t             _lid;               // Local document id for lookup in attribute vector
    document::DocumentId _document_id_copy;  // Copy of document id from document meta store

    const search::attribute::IAttributeVector& guarded_attribute_at_index(uint32_t index) const noexcept;
private:
    std::unique_ptr<select::Guards> _guards; // Attribute vector guards (held for a short time)
    const CachedSelect &_cachedSelect;       // Cached select expressions, used to get attribute vector guards.
};

} // namespace proton

