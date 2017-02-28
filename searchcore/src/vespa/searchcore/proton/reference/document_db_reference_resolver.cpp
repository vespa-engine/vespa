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
#include "gid_to_lid_change_registrator.h"
#include "gid_to_lid_change_listener.h"

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
using search::ISequencedTaskExecutor;
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

ReferenceAttribute::SP
getReferenceAttribute(const vespalib::string &name, const IAttributeManager &attrMgr)
{
    AttributeGuard::UP guard = attrMgr.getAttribute(name);
    assert(guard.get());
    assert(guard->get().getBasicType() == BasicType::REFERENCE);
    return std::dynamic_pointer_cast<ReferenceAttribute>(guard->getSP());
}


std::vector<ReferenceAttribute::SP>
getReferenceAttributes(const IAttributeManager &attrMgr)
{
    std::vector<ReferenceAttribute::SP> result;
    std::vector<AttributeGuard> attributeList;
    attrMgr.getAttributeList(attributeList);
    for (auto &guard : attributeList) {
        AttributeVector &attr = guard.get();
        if (attr.getBasicType() == BasicType::REFERENCE) {
            auto refAttr = std::dynamic_pointer_cast<ReferenceAttribute>(guard.getSP());
            assert(refAttr);
            result.push_back(std::move(refAttr));
        }
    }
    return result;
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

GidToLidChangeRegistrator &
DocumentDBReferenceResolver::getRegistrator(const vespalib::string &docTypeName)
{
    auto &result = _registrators[docTypeName];
    if (!result) {
        result = _registry.get(docTypeName)->makeGidToLidChangeRegistrator(_thisDocType.getName());
    }
    return *result;
}

IDocumentDBReferent::SP
DocumentDBReferenceResolver::getTargetDocumentDB(const vespalib::string &refAttrName) const
{
    return _registry.get(getTargetDocTypeName(refAttrName, _thisDocType));
}

void
DocumentDBReferenceResolver::connectReferenceAttributesToGidMapper(const IAttributeManager &attrMgr)
{
    auto refAttrs(getReferenceAttributes(attrMgr));
    for (auto &attrSP : refAttrs) {
        auto &attr = *attrSP;
        IDocumentDBReferent::SP targetDB = getTargetDocumentDB(attr.getName());
        attr.setGidToLidMapperFactory(targetDB->getGidToLidMapperFactory());
    }
}

void
DocumentDBReferenceResolver::detectOldListeners(const IAttributeManager &attrMgr)
{
    auto refAttrs(getReferenceAttributes(attrMgr));
    for (auto &attrSP : refAttrs) {
        vespalib::string docTypeName = getTargetDocTypeName(attrSP->getName(), _prevThisDocType);
        auto &registratorUP = _registrators[docTypeName];
        if (!registratorUP) {
            auto referent = _registry.tryGet(docTypeName);
            if (referent) {
                registratorUP = referent->makeGidToLidChangeRegistrator(_thisDocType.getName());
            }
        }
    }
}

void
DocumentDBReferenceResolver::listenToGidToLidChanges(const IAttributeManager &attrMgr)
{
    auto refAttrs(getReferenceAttributes(attrMgr));
    for (auto &attrSP : refAttrs) {
        auto &attr = *attrSP;
        vespalib::string docTypeName = getTargetDocTypeName(attr.getName(), _thisDocType);
        GidToLidChangeRegistrator &registrator = getRegistrator(docTypeName);
        auto listener = std::make_unique<GidToLidChangeListener>(_attributeFieldWriter,
                                                                 attrSP,
                                                                 _refCount,
                                                                 attr.getName(),
                                                                 _thisDocType.getName());
        registrator.addListener(std::move(listener));
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
                                                         const ImportedFieldsConfig &importedFieldsCfg,
                                                         const document::DocumentType &prevThisDocType,

                                                         MonitoredRefCount &refCount,
                                                         ISequencedTaskExecutor &attributeFieldWriter)
    : _registry(registry),
      _thisDocType(thisDocType),
      _importedFieldsCfg(importedFieldsCfg),
      _prevThisDocType(prevThisDocType),
      _refCount(refCount),
      _attributeFieldWriter(attributeFieldWriter),
      _registrators()
{
}

DocumentDBReferenceResolver::~DocumentDBReferenceResolver()
{
}

ImportedAttributesRepo::UP
DocumentDBReferenceResolver::resolve(const IAttributeManager &newAttrMgr, const IAttributeManager &oldAttrMgr)
{
    connectReferenceAttributesToGidMapper(newAttrMgr);
    detectOldListeners(oldAttrMgr);
    listenToGidToLidChanges(newAttrMgr);
    return createImportedAttributesRepo(newAttrMgr);
}

void
DocumentDBReferenceResolver::teardown(const IAttributeManager &oldAttrMgr)
{
    detectOldListeners(oldAttrMgr);
}

}
