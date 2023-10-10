// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_attribute_context.h"
#include "attribute_map.h"

namespace search::fef::test {

using IAttributeVector = attribute::IAttributeVector;

const IAttributeVector *
MockAttributeContext::getAttribute(const string & name) const {
    return _attributes.getAttribute(name);
}
const IAttributeVector *
MockAttributeContext::getAttributeStableEnum(const string & name) const {
    return getAttribute(name);
}
void
MockAttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const {
    _attributes.getAttributeList(list);
}

void
MockAttributeContext::asyncForAttribute(const vespalib::string &, std::unique_ptr<attribute::IAttributeFunctor>) const {
}

}
