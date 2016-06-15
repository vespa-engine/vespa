// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "requestcontext.h"

namespace proton {

RequestContext::RequestContext(const vespalib::Doom & doom, search::attribute::IAttributeContext & attributeContext) :
    _doom(doom),
    _attributeContext(attributeContext)
{ }

const vespalib::Doom &
RequestContext::getDoom() const
{
    return _doom;
}

const search::AttributeVector *
RequestContext::getAttribute(const vespalib::string & name) const
{
    const search::attribute::IAttributeVector * iav = _attributeContext.getAttribute(name);
    return dynamic_cast<const search::AttributeVector *>(iav);
}

const search::AttributeVector *
RequestContext::getAttributeStableEnum(const vespalib::string & name) const
{
    const search::attribute::IAttributeVector * iav = _attributeContext.getAttributeStableEnum(name);
    return dynamic_cast<const search::AttributeVector *>(iav);
}

}
