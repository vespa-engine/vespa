// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_reference.h"

namespace search { class IDocumentMetaStoreContext; }

namespace proton {

class IAttributeManager;
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
    virtual std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(vespalib::stringref name) override;
    virtual std::shared_ptr<const search::IDocumentMetaStoreContext> getDocumentMetaStoreContext() const override;
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override;
    virtual std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const vespalib::string &docTypeName) override;
};

} // namespace proton
