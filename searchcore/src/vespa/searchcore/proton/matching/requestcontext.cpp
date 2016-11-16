// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "requestcontext.h"

namespace proton {

using search::attribute::IAttributeVector;

RequestContext::RequestContext(const Doom & softDoom, IAttributeContext & attributeContext) :
    _softDoom(softDoom),
    _attributeContext(attributeContext)
{ }

const search::AttributeVector *
RequestContext::getAttribute(const vespalib::string & name) const
{
    const IAttributeVector * iav = _attributeContext.getAttribute(name);
    return dynamic_cast<const search::AttributeVector *>(iav);
}

const search::AttributeVector *
RequestContext::getAttributeStableEnum(const vespalib::string & name) const
{
    const IAttributeVector * iav = _attributeContext.getAttributeStableEnum(name);
    return dynamic_cast<const search::AttributeVector *>(iav);
}

}
