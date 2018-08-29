// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "requestcontext.h"
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton {

using search::attribute::IAttributeVector;

RequestContext::RequestContext(const Doom & softDoom, IAttributeContext & attributeContext) :
    _softDoom(softDoom),
    _attributeContext(attributeContext)
{ }

const search::attribute::IAttributeVector *
RequestContext::getAttribute(const vespalib::string &name) const
{
    return _attributeContext.getAttribute(name);
}

const search::attribute::IAttributeVector *
RequestContext::getAttributeStableEnum(const vespalib::string &name) const
{
    return _attributeContext.getAttributeStableEnum(name);
}

void RequestContext::asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const {
    _attributeContext.asyncForAttribute(name, std::move(func));
}

}
