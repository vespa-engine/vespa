// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document.h"
#include "structuredcache.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>
#include <sstream>

using vespalib::nbostream;
using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using namespace vespalib::xml;

namespace document {
namespace {

void documentTypeError(vespalib::stringref name) __attribute__((noinline));
void throwTypeMismatch(vespalib::stringref type, vespalib::stringref docidType) __attribute__((noinline));

void documentTypeError(vespalib::stringref name) {
    throw IllegalArgumentException(make_string("Cannot generate a document with non-document type %s.",
                                               vespalib::string(name).c_str()), VESPA_STRLOC);
}

void throwTypeMismatch(vespalib::stringref type, vespalib::stringref docidType) {
    throw IllegalArgumentException(make_string("Trying to create a document with type %s that don't match the id (type %s).",
                                               vespalib::string(type).c_str(), vespalib::string(docidType).c_str()),
                                   VESPA_STRLOC);
}

}  // namespace

const DataType &
Document::verifyDocumentType(const DataType *type) {
    if (!type) {
        documentTypeError("null");
    } else if ( ! type->isDocument()) {
        documentTypeError(type->toString());
    }
    return *type;
}

void
Document::verifyIdAndType(const DocumentId & id, const DataType *type) {
    verifyDocumentType(type);
    if (id.hasDocType() && (id.getDocType() != type->getName())) {
        throwTypeMismatch(type->getName(), id.getDocType());
    }
}

void
Document::setType(const DataType & type) {
    StructuredFieldValue::setType(type);
    _fields.setType(getType().getFieldsType());
}

Document::Document()
    : StructuredFieldValue(Type::DOCUMENT, *DataType::DOCUMENT),
      _id(),
      _fields(getType().getFieldsType()),
      _backingBuffer(),
      _lastModified(0)
{
    _fields.setDocumentType(getType());
}

Document::Document(const Document& rhs)
    : StructuredFieldValue(rhs),
      _id(rhs._id),
      _fields(rhs._fields),
      _backingBuffer(),
      _lastModified(rhs._lastModified)
{}

Document::Document(const DataType &type, DocumentId documentId)
    : StructuredFieldValue(Type::DOCUMENT, verifyDocumentType(&type)),
      _id(std::move(documentId)),
      _fields(getType().getFieldsType()),
      _backingBuffer(),
      _lastModified(0)
{
    _fields.setDocumentType(getType());
    if (_id.hasDocType() && (_id.getDocType() != type.getName())) {
        throwTypeMismatch(type.getName(), _id.getDocType());
    }
}

Document::UP
Document::make_without_repo(const DataType& type, DocumentId id)
{
    // Must use new as the constructor is private.
    return Document::UP(new Document(type, id));
}

Document::Document(const DocumentTypeRepo& repo, const DataType &type, DocumentId documentId)
    : StructuredFieldValue(Type::DOCUMENT, verifyDocumentType(&type)),
      _id(std::move(documentId)),
      _fields(repo, getType().getFieldsType()),
      _backingBuffer(),
      _lastModified(0)
{
    _fields.setDocumentType(getType());
    if (_id.hasDocType() && (_id.getDocType() != type.getName())) {
        throwTypeMismatch(type.getName(), _id.getDocType());
    }
}

void Document::setRepo(const DocumentTypeRepo& repo)
{
    _fields.setRepo(repo);
}

Document::Document(const DocumentTypeRepo& repo, vespalib::nbostream & is)
    : StructuredFieldValue(Type::DOCUMENT, *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _backingBuffer(),
      _lastModified(0)
{
    deserialize(repo, is);
}

Document::Document(const DocumentTypeRepo& repo, vespalib::DataBuffer && backingBuffer)
    : StructuredFieldValue(Type::DOCUMENT, *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _backingBuffer(),
      _lastModified(0)
{
    if (backingBuffer.referencesExternalData()) {
        vespalib::nbostream is(backingBuffer.getData(), backingBuffer.getDataLen());
        deserialize(repo, is);
    } else {
        vespalib::nbostream_longlivedbuf is(backingBuffer.getData(), backingBuffer.getDataLen());
        deserialize(repo, is);
        _backingBuffer = std::make_unique<vespalib::DataBuffer>(std::move(backingBuffer));
    }
}

Document::Document(Document &&) noexcept = default;
Document::~Document() noexcept = default;

Document &
Document::operator =(Document &&rhs) noexcept {
    assert( ! _cache && ! rhs._cache);
    _id = std::move(rhs._id);
    _fields = std::move(rhs._fields);
    _backingBuffer = std::move(rhs._backingBuffer);
    _lastModified = rhs._lastModified;
    StructuredFieldValue::operator=(std::move(rhs));
    return *this;
}

Document &
Document::operator =(const Document &rhs) {
    if (this == &rhs) return *this;
    assert( ! _cache && ! rhs._cache);
    _id = rhs._id;
    _fields = rhs._fields;
    _lastModified = rhs._lastModified;
    StructuredFieldValue::operator=(rhs);
    _backingBuffer.reset();
    return *this;
}

const DocumentType&
Document::getType() const {
    return static_cast<const DocumentType &>(StructuredFieldValue::getType());
}

void
Document::clear()
{
    _fields.clear();
}

void
Document::setFieldValue(const Field& field, FieldValue::UP data)
{
    _fields.setFieldValue(field, std::move(data));
}

FieldValue&
Document::assign(const FieldValue& value)
{
    /// \todo TODO (was warning):  This type checking doesnt work with the way assign is used.
//    if (*value.getDataType() == *_type) {
    auto & other(dynamic_cast<const Document&>(value));
    *this = Document(other);
    return *this;
//    }
//    return FieldValue::assign(value); // Generates exception
}

int
Document::compare(const FieldValue& other) const
{
    int diff = StructuredFieldValue::compare(other);
    if (diff != 0) {
        return diff;
    }
    auto & doc(static_cast<const Document&>(other));
    vespalib::string id1 = _id.toString();
    vespalib::string id2 = doc._id.toString();
    if (id1 != id2) {
        return (id1 < id2 ? -1 : 1);
    }
    return _fields.compare(doc._fields);
}

void
Document::print(std::ostream& out, bool verbose,
                const std::string& indent) const
{
    if (!verbose) {
        out << "Document(" << getId() << ", " << getType() << ")";
    } else {
        out << "Document(" << getId() << "\n" << indent << "  ";
        getType().print(out, true, indent + "  ");
        for (const_iterator it = begin(); it != end(); ++it) {
            out << "\n" << indent << "  " << it.field().getName() << ": ";
            getValue(it.field())->print(out, true, indent + "  ");
        }
        out << "\n" << indent << ")";
    }
}

void
Document::printXml(XmlOutputStream& xos) const
{
    xos << XmlTag("document")
        << XmlAttribute("documenttype", getType().getName())
        << XmlAttribute("documentid", getId().toString());
    if (_lastModified != 0) {
        xos << XmlAttribute("lastmodifiedtime", _lastModified);
    }
    _fields.printXml(xos);
    xos << XmlEndTag();
}

std::string
Document::toXml(const std::string& indent) const
{
    std::ostringstream ost;
    XmlOutputStream xos(ost, indent);
    printXml(xos);
    return ost.str();
}

void Document::serializeHeader(nbostream& stream) const {
    VespaDocumentSerializer serializer(stream);
    serializer.write(*this);
}

void Document::deserialize(const DocumentTypeRepo& repo, vespalib::nbostream & os) {
    VespaDocumentDeserializer deserializer(repo, os, 0);
    try {
        deserializer.read(*this);
    } catch (const IllegalStateException &e) {
        throw DeserializeException(vespalib::string("Buffer out of bounds: ") + e.what());
    }
}

void Document::deserialize(const DocumentTypeRepo& repo, vespalib::nbostream & header, vespalib::nbostream & body) {
    deserializeHeader(repo, header);
    deserializeBody(repo, body);
}

void Document::deserializeHeader(const DocumentTypeRepo& repo, vespalib::nbostream & stream) {
    VespaDocumentDeserializer deserializer(repo, stream, 0);
    deserializer.read(*this);
}

void Document::deserializeBody(const DocumentTypeRepo& repo, vespalib::nbostream & stream) {
    VespaDocumentDeserializer deserializer(repo, stream, getFields().getVersion());
    deserializer.readStructNoReset(getFields());
}

StructuredFieldValue::StructuredIterator::UP
Document::getIterator(const Field* first) const
{
    return _fields.getIterator(first);
}

void
Document::beginTransaction() {
    _cache = std::make_unique<StructuredCache>();
}
void
Document::commitTransaction() {
    for (auto & e : *_cache) {
        if (e.second.status == fieldvalue::ModificationStatus::REMOVED) {
            removeFieldValue(e.first);
        } else if (e.second.status == fieldvalue::ModificationStatus::MODIFIED) {
            setFieldValue(e.first, std::move(e.second.value));
        }
    }
    _cache.reset();
}

} // document
