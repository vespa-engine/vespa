// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "document_db_reference_resolver.h"
#include "i_document_db_referent_registry.h"
#include "i_document_db_referent.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/document/datatype/referencedatatype.h>

using document::DataType;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::ReferenceDataType;
using search::attribute::BasicType;
using search::attribute::ReferenceAttribute;
using search::AttributeGuard;
using search::AttributeVector;
using search::IAttributeManager;

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

}

void
DocumentDBReferenceResolver::connectReferenceAttributesToGidMapper()
{
    std::vector<AttributeGuard> attributeList;
    _attrMgr.getAttributeList(attributeList);
    for (auto &guard : attributeList) {
        AttributeVector &attr = guard.get();
        if (attr.getBasicType() == BasicType::REFERENCE) {
            IDocumentDBReferent::SP targetDB = _registry.get(getTargetDocTypeName(attr.getName(),
                                                                                  _thisDocType));
            asReferenceAttribute(attr).setGidToLidMapperFactory(targetDB->getGidToLidMapperFactory());
        }
    }
}

DocumentDBReferenceResolver::DocumentDBReferenceResolver(const IDocumentDBReferentRegistry &registry,
                                                         const IAttributeManager &attrMgr,
                                                         const DocumentType &thisDocType)
    : _registry(registry),
      _attrMgr(attrMgr),
      _thisDocType(thisDocType)
{
}

void
DocumentDBReferenceResolver::resolve()
{
    connectReferenceAttributesToGidMapper();
}

}
