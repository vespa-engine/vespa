// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentretriever.h"
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcore/proton/attribute/document_field_retriever.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/common/schema.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentretriever");

using document::Document;
using document::Field;
using document::FieldSet;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::PositionDataType;
using document::StructFieldValue;
using document::FieldValue;
using search::AttributeGuard;
using search::DocumentIdT;
using search::IAttributeManager;
using search::IDocumentStore;
using search::index::Schema;
using storage::spi::Timestamp;
using vespalib::geo::ZCurve;
using search::attribute::BasicType;

namespace proton {

namespace {

bool is_array_of_position_type(const document::DataType& field_type) noexcept {
    const auto* arr_type = dynamic_cast<const document::ArrayDataType*>(&field_type);
    if (!arr_type) {
        return false;
    }
    return (arr_type->getNestedType() == PositionDataType::getInstance());
}

}

FieldSetAttributeDB::FieldSetAttributeDB(const IFieldInfo & fieldInfo)
    : _fieldInfo(fieldInfo),
      _isFieldSetAttributeOnly(),
      _lock()
{}

FieldSetAttributeDB::~FieldSetAttributeDB() = default;

bool
FieldSetAttributeDB::areAllFieldsAttributes(uint64_t key, const document::Field::Set & set) const {
    std::lock_guard guard(_lock);
    auto found = _isFieldSetAttributeOnly.find(key);
    bool isAttributeOnly = true;
    if (found == _isFieldSetAttributeOnly.end()) {
        for (const Field *field : set) {
            isAttributeOnly = _fieldInfo.isFieldAttribute(*field);
            if (!isAttributeOnly) break;
        }
        _isFieldSetAttributeOnly[key] = isAttributeOnly;
    } else {
        isAttributeOnly = found->second;
    }
    return isAttributeOnly;
}

DocumentRetriever
::DocumentRetriever(const DocTypeName &docTypeName,
                    const DocumentTypeRepo &repo,
                    const Schema &schema,
                    const IDocumentMetaStoreContext &meta_store,
                    const IAttributeManager &attr_manager,
                    const IDocumentStore &doc_store)
    : DocumentRetrieverBase(docTypeName, repo, meta_store, true),
      _schema(schema),
      _attr_manager(attr_manager),
      _doc_store(doc_store),
      _possiblePositionFields(),
      _attributeFields(Field::Set::emptySet()),
      _areAllFieldsAttributes(true),
      _fieldSetAttributeInfo(*this)
{
    const DocumentType * documentType = repo.getDocumentType(docTypeName.getName());
    Field::Set fields = documentType->getFieldSet();
    int32_t positionDataTypeId = PositionDataType::getInstance().getId();
    LOG(debug, "checking document type '%s' for position fields", docTypeName.getName().c_str());
    Field::Set::Builder attrBuilder;
    for (const Field* field : fields) {
        if ((field->getDataType().getId() == positionDataTypeId) || is_array_of_position_type(field->getDataType())) {
            LOG(debug, "Field '%s' is a position field", field->getName().data());
            const vespalib::string & zcurve_name = PositionDataType::getZCurveFieldName(field->getName());
            AttributeGuard::UP attr = attr_manager.getAttribute(zcurve_name);
            if (attr && attr->valid()) {
                LOG(debug, "Field '%s' is a registered attribute field", zcurve_name.c_str());
                _possiblePositionFields.emplace_back(field, zcurve_name);
            } else {
                _areAllFieldsAttributes = false;
            }
        } else {
            const vespalib::string &name = field->getName();
            AttributeGuard::UP attr = attr_manager.getAttribute(name);
            if (attr && attr->valid()
                && !_schema.isIndexField(field->getName())
                && ((*attr)->getBasicType() != BasicType::PREDICATE)
                && ((*attr)->getBasicType() != BasicType::REFERENCE))
            {
                attrBuilder.add(field);
            } else {
                _areAllFieldsAttributes = false;
            }
        }
    }
    _attributeFields = attrBuilder.build();
}

bool
DocumentRetriever::needFetchFromDocStore(const FieldSet & fieldSet) const {
    switch (fieldSet.getType()) {
        case FieldSet::Type::NONE:
        case FieldSet::Type::DOCID:
            return false;
        case FieldSet::Type::DOCUMENT_ONLY:
        case FieldSet::Type::ALL:
            return ! _areAllFieldsAttributes;
        case FieldSet::Type::FIELD: {
            const auto & field = static_cast<const Field&>(fieldSet);
            return ! isFieldAttribute(field);
        }
        case FieldSet::Type::SET: {
            const auto &set = static_cast<const document::FieldCollection &>(fieldSet);
            return ! _fieldSetAttributeInfo.areAllFieldsAttributes(set.hash(), set.getFields());
        }
    }
    abort();
}

bool
DocumentRetriever::isFieldAttribute(const Field& field) const {
    return _attributeFields.contains(field);
}

DocumentRetriever::~DocumentRetriever() = default;

namespace {

std::unique_ptr<document::FieldValue>
positionFromZcurve(int64_t zcurve) {
    int32_t x, y;
    ZCurve::decode(zcurve, &x, &y);

    FieldValue::UP value = PositionDataType::getInstance().createFieldValue();
    auto *pos = static_cast<StructFieldValue *>(value.get());
    pos->setValue(pos->getField(PositionDataType::FIELD_X), std::make_unique<document::IntFieldValue>(x));
    pos->setValue(pos->getField(PositionDataType::FIELD_Y), std::make_unique<document::IntFieldValue>(y));
    return value;
}

std::unique_ptr<document::FieldValue>
zcurve_array_attribute_to_field_value(const document::Field& field,
                                      const search::attribute::IAttributeVector& attr,
                                      DocumentIdT lid)
{
    search::attribute::AttributeContent<int64_t> zc_elems;
    zc_elems.fill(attr, lid);
    auto new_fv = field.createValue();
    auto& new_array_fv = dynamic_cast<document::ArrayFieldValue&>(*new_fv);
    new_array_fv.reserve(zc_elems.size());
    for (int64_t zc : zc_elems) {
        new_array_fv.append(positionFromZcurve(zc));
    }
    return new_fv;
}

void
fillInPositionFields(Document &doc, DocumentIdT lid, const DocumentRetriever::PositionFields & possiblePositionFields, const IAttributeManager & attr_manager)
{
    for (const auto & it : possiblePositionFields) {
        auto attr_guard = attr_manager.getAttribute(it.second);
        auto& attr = *attr_guard;
        if (!attr->isUndefined(lid)) {
            if (attr->hasArrayType()) {
                doc.setFieldValue(*it.first, zcurve_array_attribute_to_field_value(*it.first, *attr, lid));
            } else {
                int64_t zcurve = attr->getInt(lid);
                doc.setValue(*it.first, *positionFromZcurve(zcurve));
            }
        } else {
            doc.remove(*it.first); // Don't resurrect old values from the docstore.
        }
    }
}

class PopulateVisitor : public search::IDocumentVisitor
{
public:
    PopulateVisitor(const DocumentRetriever & retriever, search::IDocumentVisitor & visitor) :
        _retriever(retriever),
        _visitor(visitor)
    { }
    void visit(uint32_t lid, document::Document::UP doc) override {
        if (doc) {
            _retriever.populate(lid, *doc);
            _visitor.visit(lid, std::move(doc));
        }
    }

    bool allowVisitCaching() const override {
        return _visitor.allowVisitCaching();
    }

private:
    const DocumentRetriever  & _retriever;
    search::IDocumentVisitor & _visitor;
};

}  // namespace

Document::UP
DocumentRetriever::getFullDocument(DocumentIdT lid) const
{
    Document::UP doc = _doc_store.read(lid, getDocumentTypeRepo());
    if (doc) {
        populate(lid, *doc);
    }
    return doc;
}

Document::UP
DocumentRetriever::getPartialDocument(search::DocumentIdT lid, const document::DocumentId & docId, const FieldSet & fieldSet) const {
    Document::UP doc;
    if (needFetchFromDocStore(fieldSet)) {
        doc = _doc_store.read(lid, getDocumentTypeRepo());
        if (doc) {
            populate(lid, *doc);
            FieldSet::stripFields(*doc, fieldSet);
        }
    } else {
        doc = std::make_unique<Document>(getDocumentType(), docId);
        switch (fieldSet.getType()) {
            case FieldSet::Type::ALL:
                populate(lid, *doc);
                break;
            case FieldSet::Type::FIELD: {
                const auto & field = static_cast<const Field&>(fieldSet);
                populate(lid, *doc, Field::Set::Builder().add(&field).build());
                break;
            }
            case FieldSet::Type::SET: {
                const auto &set = static_cast<const document::FieldCollection &>(fieldSet);
                populate(lid, *doc, set.getFields());
                break;
            }
            case FieldSet::Type::DOCUMENT_ONLY: {
                const auto * actual = getDocumentType().getFieldSet(document::DocumentOnly::NAME);
                if (actual != nullptr) {
                    const auto &set = actual->asCollection();
                    populate(lid, *doc, set.getFields());
                }
                break;
            }
            case FieldSet::Type::NONE:
            case FieldSet::Type::DOCID:
                break;
        }
        doc->setRepo(getDocumentTypeRepo());
    }
    return doc;
}

void
DocumentRetriever::visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const
{
    PopulateVisitor populater(*this, visitor);
    _doc_store.visit(lids, getDocumentTypeRepo(), populater);
}

void
DocumentRetriever::populate(DocumentIdT lid, Document & doc) const {
    populate(lid, doc, _attributeFields);
}

void
DocumentRetriever::populate(DocumentIdT lid, Document & doc, const Field::Set & attributeFields) const
{
    for (const Field* field : attributeFields) {
        AttributeGuard::UP attr = _attr_manager.getAttribute(field->getName());
        if (lid < (*attr)->getCommittedDocIdLimit()) {
            DocumentFieldRetriever::populate(lid, doc, *field, **attr);
        } else {
            doc.remove(*field);
        }
    }
    fillInPositionFields(doc, lid, _possiblePositionFields, _attr_manager);
}

const IAttributeManager *
DocumentRetriever::getAttrMgr() const
{
    return &_attr_manager;
}


}  // namespace proton
