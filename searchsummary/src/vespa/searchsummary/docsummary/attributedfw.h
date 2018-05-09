// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsumfieldwriter.h"

namespace search::attribute { class IAttributeVector; }

namespace search::docsummary {

class AttrDFW : public IDocsumFieldWriter
{
private:
    vespalib::string _attrName;
protected:
    const attribute::IAttributeVector & vec(const GetDocsumsState & s) const;
    const vespalib::string & getAttributeName() const override { return _attrName; }
public:
    AttrDFW(const vespalib::string & attrName);
    bool IsGenerated() const override { return true; }
};

}

