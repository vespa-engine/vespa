// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_reference.h"

namespace search { struct IDocumentMetaStoreContext; }

namespace proton {

struct IAttributeManager;
class IGidToLidChangeHandler;

/*
 * Class for getting target attributes for imported
 * attributes, and for getting interface for mapping to lids
 * compatible with the target attributes.
 */
class DocumentDBReference : public IDocumentDBReference
{
    std::shared_ptr<IAttributeManager> _attrMgr;
    std::shared_ptr<const search::IDocumentMetaStoreContext> _dmsContext;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
public:
    DocumentDBReference(std::shared_ptr<IAttributeManager> attrMgr,
                        std::shared_ptr<const search::IDocumentMetaStoreContext> dmsContext,
                        std::shared_ptr<IGidToLidChangeHandler> gidToLidChangeHandler);
    virtual ~DocumentDBReference();
    std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(std::string_view name) override;
    std::shared_ptr<const search::IDocumentMetaStoreContext> getDocumentMetaStore() const override;
    std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override;
    std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const std::string &docTypeName) override;
};

} // namespace proton
