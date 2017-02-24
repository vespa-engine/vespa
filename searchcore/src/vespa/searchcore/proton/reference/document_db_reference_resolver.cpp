// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "document_db_reference_resolver.h"
#include "i_document_db_referent_registry.h"
#include "i_document_db_referent.h"
#include <vespa/config-imported-fields.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchcommon/attribute/iattributevector.h>

using document::DataType;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::ReferenceDataType;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::ReferenceAttribute;
using search::AttributeGuard;
using search::AttributeVector;
using search::IAttributeManager;
using search::NotImplementedAttribute;
using vespa::config::search::ImportedFieldsConfig;

namespace proton {

namespace {

vespalib::string
getTargetDocTypeName(const vespalib::string &attrName,
                     const DocumentType &thisDocType)
{
    const DataType &attrType = thisDocType.getField(attrName).getDataType();
    const ReferenceDataType *refType = dynamic_cast<const ReferenceDataType *>(&attrType);
    assert(refType != nullptr);
    return refType->getTargetType().getName();
}

ReferenceAttribute &
asReferenceAttribute(AttributeVector &attr)
{
    ReferenceAttribute *result = dynamic_cast<ReferenceAttribute *>(&attr);
    assert(result != nullptr);
    return *result;
}

ReferenceAttribute::SP
getReferenceAttribute(const vespalib::string &name, const IAttributeManager &attrMgr)
{
    AttributeGuard::UP guard = attrMgr.getAttribute(name);
    assert(guard.get());
    assert(guard->get()->getBasicType() == BasicType::REFERENCE);
    return std::dynamic_pointer_cast<ReferenceAttribute>(guard->getSP());
}

}

ImportedAttributeVector::ImportedAttributeVector(vespalib::stringref name,
                                                 ReferenceAttribute::SP refAttr,
                                                 IAttributeVector::SP targetAttr)
    : NotImplementedAttribute(name, Config(targetAttr->getBasicType(), targetAttr->getCollectionType())),
      _refAttr(std::move(refAttr)),
      _targetAttr(std::move(targetAttr))
{
}

IDocumentDBReferent::SP
DocumentDBReferenceResolver::getTargetDocumentDB(const vespalib::string &refAttrName) const
{
    return _registry.get(getTargetDocTypeName(refAttrName, _thisDocType));
}

void
DocumentDBReferenceResolver::connectReferenceAttributesToGidMapper(const IAttributeManager &attrMgr)
{
    std::vector<AttributeGuard> attributeList;
    attrMgr.getAttributeList(attributeList);
    for (auto &guard : attributeList) {
        AttributeVector &attr = *guard;
        if (attr.getBasicType() == BasicType::REFERENCE) {
            IDocumentDBReferent::SP targetDB = getTargetDocumentDB(attr.getName());
            asReferenceAttribute(attr).setGidToLidMapperFactory(targetDB->getGidToLidMapperFactory());
        }
    }
}

ImportedAttributesRepo::UP
DocumentDBReferenceResolver::createImportedAttributesRepo(const IAttributeManager &attrMgr)
{
    auto result = std::make_unique<ImportedAttributesRepo>();
    for (const auto &attr : _importedFieldsCfg.attribute) {
        ReferenceAttribute::SP refAttr = getReferenceAttribute(attr.referencefield, attrMgr);
        IAttributeVector::SP targetAttr = getTargetDocumentDB(refAttr->getName())->getAttribute(attr.targetfield);
        IAttributeVector::SP importedAttr = std::make_shared<ImportedAttributeVector>(attr.name, refAttr, targetAttr);
        result->add(importedAttr->getName(), importedAttr);
    }
    return result;
}

DocumentDBReferenceResolver::DocumentDBReferenceResolver(const IDocumentDBReferentRegistry &registry,
                                                         const DocumentType &thisDocType,
                                                         const ImportedFieldsConfig &importedFieldsCfg)
    : _registry(registry),
      _thisDocType(thisDocType),
      _importedFieldsCfg(importedFieldsCfg)
{
}

ImportedAttributesRepo::UP
DocumentDBReferenceResolver::resolve(const IAttributeManager &attrMgr)
{
    connectReferenceAttributesToGidMapper(attrMgr);
    return createImportedAttributesRepo(attrMgr);
}

}
