// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>

namespace proton {

class RequestContext : public search::queryeval::IRequestContext
{
public:
    using IAttributeContext = search::attribute::IAttributeContext;
    using Doom = vespalib::Doom;
    RequestContext(const Doom & softDoom, IAttributeContext & attributeContext);
    const Doom & getSoftDoom() const override { return _softDoom; }
    const search::attribute::IAttributeVector *getAttribute(const vespalib::string &name) const override;
    const search::attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override;
private:
    const Doom          _softDoom;
    IAttributeContext & _attributeContext;
};

}
