// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_referent.h"

namespace search
{

class IAttributeManager;

}

namespace proton {

class DocumentMetaStore;
class IGidToLidChangeHandler;

/*
 * Class for getting target attributes for imported
 * attributes, and for getting interface for mapping to lids
 * compatible with the target attributes.
 */
class DocumentDBReferent : public IDocumentDBReferent
{
    std::shared_ptr<search::IAttributeManager> _attrMgr;
    std::shared_ptr<DocumentMetaStore> _dms;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
public:
    DocumentDBReferent(std::shared_ptr<search::IAttributeManager> attrMgr,
                       std::shared_ptr<DocumentMetaStore> dms,
                       std::shared_ptr<IGidToLidChangeHandler> gidToLidChangeHandler);
    virtual ~DocumentDBReferent();
    virtual std::shared_ptr<search::AttributeVector> getAttribute(vespalib::stringref name) override;
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override;
    virtual std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const vespalib::string &docTypeName) override;
};

} // namespace proton
