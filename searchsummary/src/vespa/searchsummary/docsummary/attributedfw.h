// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/iattributemanager.h>
#include "docsumfieldwriter.h"

namespace search {
namespace docsummary {

class AttrDFW : public IDocsumFieldWriter
{
private:
    vespalib::string _attrName;
protected:
    const attribute::IAttributeVector & vec(const GetDocsumsState & s) const;
    virtual const vespalib::string & getAttributeName() const override { return _attrName; }
public:
    AttrDFW(const vespalib::string & attrName);
    virtual bool IsGenerated() const override { return true; }
};

}
}

