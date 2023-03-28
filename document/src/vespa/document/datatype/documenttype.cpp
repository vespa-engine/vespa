// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documenttype.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>
#include <ostream>

LOG_SETUP(".document.datatype.document");

using vespalib::IllegalArgumentException;
using vespalib::make_string;
using vespalib::stringref;


namespace document {

namespace {
FieldCollection build_field_collection(const std::set<vespalib::string> &fields,
                                       const DocumentType &doc_type)
{
    Field::Set::Builder builder;
    for (const auto & field_name : fields) {
        if (doc_type.hasField(field_name)) {
            builder.add(&doc_type.getField(field_name));
        }
    }
    return FieldCollection(doc_type, builder.build());
}
} // namespace <unnamed>

DocumentType::FieldSet::FieldSet(const vespalib::string & name, Fields fields,
                                 const DocumentType & doc_type)
    : _name(name),
      _fields(fields),
      _field_collection(build_field_collection(fields, doc_type))
{}

DocumentType::DocumentType(stringref name, int32_t id)
    : StructuredDataType(name, id),
      _inheritedTypes(),
      _ownedFields(std::make_shared<StructDataType>(name + ".header")),
      _fields(_ownedFields.get()),
      _fieldSets(),
      _imported_field_names()
{
    if (name != "document") {
        _inheritedTypes.push_back(DataType::DOCUMENT);
    }
}

DocumentType::DocumentType(stringref name, int32_t id, const StructDataType& fields)
    : StructuredDataType(name, id),
      _inheritedTypes(),
      _fields(&fields),
      _fieldSets(),
      _imported_field_names()
{
    if (name != "document") {
        _inheritedTypes.push_back(DataType::DOCUMENT);
    }
}

DocumentType::DocumentType(stringref name)
    : StructuredDataType(name),
      _inheritedTypes(),
      _ownedFields(std::make_shared<StructDataType>(name + ".header")),
      _fields(_ownedFields.get()),
      _fieldSets(),
      _imported_field_names()
{
    if (name != "document") {
        _inheritedTypes.emplace_back(DataType::DOCUMENT);
    }
}

DocumentType::DocumentType(stringref name, const StructDataType& fields)
    : StructuredDataType(name),
      _inheritedTypes(),
      _fields(&fields),
      _fieldSets(),
      _imported_field_names()
{
    if (name != "document") {
        _inheritedTypes.emplace_back(DataType::DOCUMENT);
    }
}

DocumentType & DocumentType::operator=(const DocumentType &) = default;
DocumentType::DocumentType(const DocumentType &) = default;
DocumentType::~DocumentType() = default;

DocumentType &
DocumentType::addFieldSet(const vespalib::string & name, FieldSet::Fields fields)
{
    _fieldSets.emplace(name, FieldSet(name, std::move(fields), *this));
    return *this;
}

const DocumentType::FieldSet *
DocumentType::getFieldSet(const vespalib::string & name) const
{
    auto it = _fieldSets.find(name);
    return (it != _fieldSets.end()) ? & it->second : nullptr;
}

void
DocumentType::addField(const Field& field)
{
    if (_fields->hasField(field.getName())) {
        throw IllegalArgumentException( "A field already exists with name " + field.getName(), VESPA_STRLOC);
    } else if (_fields->hasField(field)) {
        throw IllegalArgumentException(make_string("A field already exists with id %i.", field.getId()), VESPA_STRLOC);
    } else if (!_ownedFields.get()) {
        throw vespalib::IllegalStateException(make_string(
                        "Cannot add field %s to a DocumentType that does not "
                        "own its fields.", field.getName().data()), VESPA_STRLOC);
    }
    _ownedFields->addField(field);
}

void
DocumentType::inherit(const DocumentType &docType) {
    if (docType.getName() == "document") {
        return;
    }
    if (docType.isA(*this)) {
        throw IllegalArgumentException(
                "Document type " + docType.toString() + " already inherits type "
                + toString() + ". Cannot add cyclic dependencies.", VESPA_STRLOC);
    }
    // If we already inherits this type, there is no point in adding it again.
    if (isA(docType)) {
        // If we already directly inherits it, complain
        for (const auto* inherited : _inheritedTypes) {
            if (inherited->equals(docType)) {
                throw IllegalArgumentException(
                        "DocumentType " + getName() + " already inherits "
                        "document type " + docType.getName(), VESPA_STRLOC);
            }
        }
        // Indirectly already inheriting it is oki, as this can happen
        // due to inherited documents inheriting the same type.
        LOG(info, "Document type %s inherits document type %s from multiple "
                  "types.", getName().c_str(), docType.getName().c_str());
        return;
    }
    // Add non-conflicting types.
    Field::Set fs = docType._fields->getFieldSet();
    for (const auto* field : fs) {
        if (!_ownedFields.get()) {
            _ownedFields = std::make_shared<StructDataType>(*_fields);
            _fields = _ownedFields.get();
        }
        _ownedFields->addInheritedField(*field);
    }
    // If we inherit default document type Document.0, remove that if adding
    // another parent, as that has to also inherit Document
    if ((_inheritedTypes.size() == 1) && _inheritedTypes[0]->equals(*DataType::DOCUMENT)) {
        _inheritedTypes.clear();
    }
    _inheritedTypes.push_back(&docType);
}

bool
DocumentType::isA(const DataType& other) const
{
    for (const DocumentType * docType : _inheritedTypes) {
        if (docType->isA(other)) return true;
    }
    return equals(other);
}

FieldValue::UP
DocumentType::createFieldValue() const
{
    return Document::make_without_repo(*this, DocumentId("id::" + getName() + "::"));
}

void
DocumentType::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "DocumentType(" << getName();
    if (verbose) {
        out << ", id " << getId();
    }
    out << ")";
    if (verbose) {
        if (!_inheritedTypes.empty()) {
            auto it = _inheritedTypes.begin();
            out << "\n" << indent << "    : ";
            (*it)->print(out, false, "");
            while (++it != _inheritedTypes.end()) {
                out << ",\n" << indent << "      ";
                (*it)->print(out, false, "");
            }
        }
        out << " {\n" << indent << "  ";
        _fields->print(out, verbose, indent + "  ");
        out << "\n" << indent << "}";
    }
}

bool
DocumentType::equals(const DataType& other) const noexcept
{
    if (&other == this) return true;
    if ( ! DataType::equals(other)) return false;
    const auto* o(dynamic_cast<const DocumentType*>(&other));
    if (o == nullptr) return false;
    if ( ! _fields->equals(*o->_fields)) return false;
    if (_inheritedTypes.size() != o->_inheritedTypes.size()) return false;
    auto it1 = _inheritedTypes.begin();
    auto it2 = o->_inheritedTypes.begin();
    while (it1 != _inheritedTypes.end()) {
        if ( ! (*it1)->equals( **it2)) return false;
        ++it1;
        ++it2;
    }
    // TODO imported fields? like in the Java impl, field sets are not considered either... :I
    return true;
}

const Field&
DocumentType::getField(stringref name) const
{
    return _fields->getField(name);
}

const Field&
DocumentType::getField(int fieldId) const
{
    return _fields->getField(fieldId);
}

Field::Set
DocumentType::getFieldSet() const
{
    return _fields->getFieldSet();
}

bool
DocumentType::has_imported_field_name(const vespalib::string& name) const noexcept {
    return (_imported_field_names.find(name) != _imported_field_names.end());
}

void
DocumentType::add_imported_field_name(const vespalib::string& name) {
    _imported_field_names.insert(name);
}

} // document
