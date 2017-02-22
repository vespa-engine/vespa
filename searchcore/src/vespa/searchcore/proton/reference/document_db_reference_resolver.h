// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_reference_resolver.h"

namespace document {
class DocumentType;
class DocumentTypeRepo;
}
namespace search { class IAttributeManager; }

namespace proton {

class IDocumentDBReferentRegistry;

/**
 * Class that for a given document db resolves all references to parent document dbs:
 *   1) Connects all reference attributes to gid mappers of parent document dbs.
 */
class DocumentDBReferenceResolver : public IDocumentDBReferenceResolver {
private:
    const IDocumentDBReferentRegistry &_registry;
    const document::DocumentType &_thisDocType;

    void connectReferenceAttributesToGidMapper(const search::IAttributeManager &attrMgr);

public:
    DocumentDBReferenceResolver(const IDocumentDBReferentRegistry &registry,
                                const document::DocumentType &thisDocType);

    virtual void resolve(const search::IAttributeManager &attrMgr) override;
};

}
