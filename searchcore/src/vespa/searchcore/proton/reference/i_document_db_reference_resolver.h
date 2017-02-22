// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search { class IAttributeManager; }

namespace proton {

/**
 * Interface used by a given document db to resolve all references to parent document dbs.
 */
struct IDocumentDBReferenceResolver {
    virtual ~IDocumentDBReferenceResolver() {}
    virtual void resolve(const search::IAttributeManager &attrMgr) = 0;
};

}
