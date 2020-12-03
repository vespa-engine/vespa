// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
public:
    DocumentMetaStoreAttribute(const vespalib::string &name=getFixedName());
    ~DocumentMetaStoreAttribute() override;

    static const vespalib::string &getFixedName();

    // Implements IAttributeVector
    size_t getFixedWidth() const override {
        return document::GlobalId::LENGTH;
    }

    void onCommit() override {}
};

}

