// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_attribute_context.h"
#include "attribute_map.h"

namespace search::fef::test {

using IAttributeVector = attribute::IAttributeVector;

const IAttributeVector *
MockAttributeContext::getAttribute(std::string_view name) const {
    return _attributes.getAttribute(name);
}
const IAttributeVector *
MockAttributeContext::getAttributeStableEnum(std::string_view name) const {
    return getAttribute(name);
}
void
MockAttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const {
    _attributes.getAttributeList(list);
}

void
MockAttributeContext::asyncForAttribute(std::string_view, std::unique_ptr<attribute::IAttributeFunctor>) const {
}

}
