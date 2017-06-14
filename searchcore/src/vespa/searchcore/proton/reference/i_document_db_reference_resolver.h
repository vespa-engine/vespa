// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace search { class IAttributeManager; }

namespace proton {

class ImportedAttributesRepo;

/**
 * Interface used by a given document db to resolve all references to parent document dbs.
 */
struct IDocumentDBReferenceResolver {
    virtual ~IDocumentDBReferenceResolver() {}
    virtual std::unique_ptr<ImportedAttributesRepo> resolve(const search::IAttributeManager &newAttrMgr, const search::IAttributeManager &oldAttrMgr) = 0;
    virtual void teardown(const search::IAttributeManager &oldAttrMgr) = 0;
};

}
