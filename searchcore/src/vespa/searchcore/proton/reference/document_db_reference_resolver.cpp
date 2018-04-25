// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_reference_resolver.h"
#include "gid_to_lid_change_listener.h"
#include "gid_to_lid_change_registrator.h"
#include "i_document_db_reference.h"
#include "i_document_db_reference_registry.h"
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/config-imported-fields.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>

using document::DataType;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::ReferenceDataType;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
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
    assert(guard->get()->getBasicType() == BasicType::REFERENCE);
    return std::dynamic_pointer_cast<ReferenceAttribute>(guard->getSP());
}


std::vector<ReferenceAttribute::SP>
getReferenceAttributes(const IAttributeManager &attrMgr)
{
    std::vector<ReferenceAttribute::SP> result;
    std::vector<AttributeGuard> attributeList;
    attrMgr.getAttributeList(attributeList);
    for (auto &guard : attributeList) {
        AttributeVector &attr = *guard;
        if (attr.getBasicType() == BasicType::REFERENCE) {
            auto refAttr = std::dynamic_pointer_cast<ReferenceAttribute>(guard.getSP());
            assert(refAttr);
            result.push_back(std::move(refAttr));
        }
    }
    return result;
}

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

IDocumentDBReference::SP
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
        IDocumentDBReference::SP targetDB = getTargetDocumentDB(attr.getName());
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
            auto reference = _registry.tryGet(docTypeName);
            if (reference) {
                registratorUP = reference->makeGidToLidChangeRegistrator(_thisDocType.getName());
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
DocumentDBReferenceResolver::createImportedAttributesRepo(const IAttributeManager &attrMgr,
                                                          const std::shared_ptr<search::IDocumentMetaStoreContext> &documentMetaStore,
                                                          bool useSearchCache)
{
    auto result = std::make_unique<ImportedAttributesRepo>();
    if (_useReferences) {
        for (const auto &attr : _importedFieldsCfg.attribute) {
            ReferenceAttribute::SP refAttr = getReferenceAttribute(attr.referencefield, attrMgr);
            auto targetDocumentDB = getTargetDocumentDB(refAttr->getName());
            auto targetAttr = targetDocumentDB->getAttribute(attr.targetfield);
            auto targetDocumentMetaStore = targetDocumentDB->getDocumentMetaStore();
            auto importedAttr = ImportedAttributeVectorFactory::create(attr.name, refAttr, documentMetaStore, targetAttr, targetDocumentMetaStore, useSearchCache);
            result->add(importedAttr->getName(), importedAttr);
        }
    }
    return result;
}

DocumentDBReferenceResolver::DocumentDBReferenceResolver(const IDocumentDBReferenceRegistry &registry,
                                                         const DocumentType &thisDocType,
                                                         const ImportedFieldsConfig &importedFieldsCfg,
                                                         const document::DocumentType &prevThisDocType,

                                                         MonitoredRefCount &refCount,
                                                         ISequencedTaskExecutor &attributeFieldWriter,
                                                         bool useReferences)
    : _registry(registry),
      _thisDocType(thisDocType),
      _importedFieldsCfg(importedFieldsCfg),
      _prevThisDocType(prevThisDocType),
      _refCount(refCount),
      _attributeFieldWriter(attributeFieldWriter),
      _useReferences(useReferences),
      _registrators()
{
}

DocumentDBReferenceResolver::~DocumentDBReferenceResolver()
{
}

ImportedAttributesRepo::UP
DocumentDBReferenceResolver::resolve(const IAttributeManager &newAttrMgr,
                                     const IAttributeManager &oldAttrMgr,
                                     const std::shared_ptr<search::IDocumentMetaStoreContext> &documentMetaStore,
                                     fastos::TimeStamp visibilityDelay)
{
    detectOldListeners(oldAttrMgr);
    if (_useReferences) {
        connectReferenceAttributesToGidMapper(newAttrMgr);
        listenToGidToLidChanges(newAttrMgr);
    }
    return createImportedAttributesRepo(newAttrMgr, documentMetaStore, (visibilityDelay > 0));
}

void
DocumentDBReferenceResolver::teardown(const IAttributeManager &oldAttrMgr)
{
    detectOldListeners(oldAttrMgr);
}

}
