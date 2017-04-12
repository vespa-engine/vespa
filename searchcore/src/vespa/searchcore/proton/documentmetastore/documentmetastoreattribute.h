// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>

namespace proton {

/**
 * Abstract implementation of the IDocumentMetaStore interface
 * as an attribute vector.
 **/
class DocumentMetaStoreAttribute : public search::NotImplementedAttribute
{
protected:
    virtual void notImplemented() const override __attribute__((noinline));

public:
    DocumentMetaStoreAttribute(const vespalib::string &name=getFixedName());
    virtual ~DocumentMetaStoreAttribute();

    static const vespalib::string &getFixedName();

    // Implements IAttributeVector
    virtual size_t
    getFixedWidth() const override
    {
        return document::GlobalId::LENGTH;
    }

    virtual void onCommit() override {}
};

}

