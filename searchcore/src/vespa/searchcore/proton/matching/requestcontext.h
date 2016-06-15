// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>

namespace proton {

class RequestContext : public search::queryeval::IRequestContext
{
public:
    RequestContext(const vespalib::Doom & doom, search::attribute::IAttributeContext & attributeContext);
    const vespalib::Doom & getDoom() const override;
    const search::AttributeVector * getAttribute(const vespalib::string & name) const override;
    const search::AttributeVector * getAttributeStableEnum(const vespalib::string & name) const override;
private:
    const vespalib::Doom                   _doom;
    search::attribute::IAttributeContext & _attributeContext;
};

}
