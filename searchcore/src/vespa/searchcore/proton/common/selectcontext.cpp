// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "selectcontext.h"
#include "cachedselect.h"
#include <vespa/document/select/value.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/readable_attribute_vector.h>
#include <cassert>

namespace proton {

using document::select::Value;
using document::select::Context;
using search::AttributeVector;
using search::attribute::AttributeReadGuard;

namespace select {
struct Guards : public std::vector<std::unique_ptr<AttributeReadGuard>> {
    using Parent = std::vector<std::unique_ptr<AttributeReadGuard>>;
    using Parent::Parent;
};
}

SelectContext::SelectContext(const CachedSelect &cachedSelect)
    : Context(),
      _docId(0u),
      _guards(std::make_unique<select::Guards>(cachedSelect.attributes().size())),
      _cachedSelect(cachedSelect)
{ }

SelectContext::~SelectContext() = default;

void
SelectContext::getAttributeGuards()
{
    _guards->clear();
    _guards->reserve(_cachedSelect.attributes().size());
    for (const auto& attr : _cachedSelect.attributes()) {
        _guards->emplace_back(attr->makeReadGuard(false));
    }
}

void
SelectContext::dropAttributeGuards()
{
    _guards->clear();
}

const search::attribute::IAttributeVector&
SelectContext::guarded_attribute_at_index(uint32_t index) const noexcept
{
    assert(index < _guards->size());
    assert((*_guards)[index].get() != nullptr);
    return *((*_guards)[index])->attribute();
}

}
