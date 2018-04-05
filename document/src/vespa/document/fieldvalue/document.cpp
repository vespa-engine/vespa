// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <sstream>
#include <limits>

using vespalib::nbostream;
using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using namespace vespalib::xml;

namespace document {
namespace {

bool isLegalVersion(uint16_t version) {
    return (6 <= version) && (version <= 8);
}

void documentTypeError(const vespalib::stringref & name) __attribute__((noinline));
void throwTypeMismatch(vespalib::stringref type, vespalib::stringref docidType) __attribute__((noinline));

void documentTypeError(const vespalib::stringref & name) {
    throw IllegalArgumentException(make_string("Cannot generate a document with non-document type %s.", name.c_str()), VESPA_STRLOC);
}

void throwTypeMismatch(vespalib::stringref type, vespalib::stringref docidType) {
    throw IllegalArgumentException(make_string("Trying to create a document with type %s that don't match the id (type %s).",
                                               type.c_str(), docidType.c_str()),
                                   VESPA_STRLOC);
}

const DataType &verifyDocumentType(const DataType *type) {
    if (!type) {
        documentTypeError("null");
    } else if ( ! type->getClass().inherits(DocumentType::classId)) {
        documentTypeError(type->toString());
    }
    return *type;
}
}  // namespace

IMPLEMENT_IDENTIFIABLE_ABSTRACT(Document, StructuredFieldValue);

Document::Document()
    : StructuredFieldValue(*DataType::DOCUMENT),
      _id(),
      _fields(getType().getFieldsType()),
      _lastModified(0)
{
    _fields.setDocumentType(getType());
}

Document::Document(const Document& other)
    : StructuredFieldValue(other),
      _id(other._id),
      _fields(other._fields),
      _lastModified(other._lastModified)
{
}

Document::Document(const DataType &type, const DocumentId& documentId)
    : StructuredFieldValue(verifyDocumentType(&type)),
      _id(documentId),
      _fields(getType().getFieldsType()),
      _lastModified(0)
{
    _fields.setDocumentType(getType());
    if (documentId.hasDocType() && documentId.getDocType() != type.getName()) {
        throwTypeMismatch(type.getName(), documentId.getDocType());
    }
}

Document::Document(const DataType &type, DocumentId& documentId, bool iWillAllowSwap)
    : StructuredFieldValue(verifyDocumentType(&type)),
      _id(),
      _fields(getType().getFieldsType()),
      _lastModified(0)
{
    (void) iWillAllowSwap;
    _fields.setDocumentType(getType());
    if (documentId.hasDocType() && (documentId.getDocType() != type.getName())) {
        throwTypeMismatch(type.getName(), documentId.getDocType());
    }
    _id.swap(documentId);
}

Document::Document(const DocumentTypeRepo& repo, ByteBuffer& buffer, const DataType *anticipatedType)
    : StructuredFieldValue(anticipatedType ?  verifyDocumentType(anticipatedType) : *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _lastModified(0)
{
    deserialize(repo, buffer);
}

void Document::setRepo(const DocumentTypeRepo& repo)
{
    _fields.setRepo(repo);
}

Document::Document(const DocumentTypeRepo& repo, vespalib::nbostream & is, const DataType *anticipatedType)
    : StructuredFieldValue(anticipatedType ?  verifyDocumentType(anticipatedType) : *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _lastModified(0)
{
    deserialize(repo, is);
}

Document::Document(const DocumentTypeRepo& repo, ByteBuffer& buffer, bool includeContent, const DataType *anticipatedType)
    : StructuredFieldValue(anticipatedType ?  verifyDocumentType(anticipatedType) : *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _lastModified(0)
{
    if (!includeContent) {
        const DocumentType *newDocType = deserializeDocHeaderAndType(repo, buffer, _id, static_cast<const DocumentType*>(anticipatedType));
        if (newDocType) {
            setType(*newDocType);
        }
    } else {
        deserialize(repo, buffer);
    }
}


Document::Document(const DocumentTypeRepo& repo, ByteBuffer& header, ByteBuffer& body, const DataType *anticipatedType)
    : StructuredFieldValue(anticipatedType ?  verifyDocumentType(anticipatedType) : *DataType::DOCUMENT),
      _id(),
      _fields(static_cast<const DocumentType &>(getType()).getFieldsType()),
      _lastModified(0)
{
    deserializeHeader(repo, header);
    deserializeBody(repo, body);
}

Document::~Document() {
}

void
Document::swap(Document & rhs)
{
    StructuredFieldValue::swap(rhs);
    _fields.swap(rhs._fields);
    _id.swap(rhs._id);
    std::swap(_lastModified, rhs._lastModified);
}

const DocumentType&
Document::getType() const {
    return static_cast<const DocumentType &>(StructuredFieldValue::getType());
}

Document& Document::operator=(const Document& doc)
{
    StructuredFieldValue::operator=(doc);
    _id = doc._id;
    _fields = doc._fields;
    _lastModified = doc._lastModified;
    return *this;
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

bool
Document::hasChanged() const
{
    return _fields.hasChanged();
}

DocumentId
Document::getIdFromSerialized(ByteBuffer& buf)
{
    int position = buf.getPos();
    DocumentId retVal;

    deserializeDocHeader(buf, retVal);
    buf.setPos(position);

    return retVal;
}

const DocumentType *
Document::getDocTypeFromSerialized(const DocumentTypeRepo& repo, ByteBuffer& buf)
{
    int position = buf.getPos();
    DocumentId retVal;

    const DocumentType *docType(deserializeDocHeaderAndType(repo, buf, retVal, NULL));
    buf.setPos(position);

    return docType;
}

FieldValue&
Document::assign(const FieldValue& value)
{
    /// \todo TODO (was warning):  This type checking doesnt work with the way assign is used.
//    if (*value.getDataType() == *_type) {
    const Document& other(dynamic_cast<const Document&>(value));
    return operator=(other);
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
    const Document& doc(static_cast<const Document&>(other));
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

uint32_t
Document::calculateChecksum() const
{
    vespalib::string docId(_id.toString());
    const vespalib::string & typeName(getType().getName());
    uint16_t typeVersion(0);  // Hardcode version 0 (version not supported)

    vespalib::crc_32_type calculator;
    calculator.process_bytes(docId.c_str(), docId.size());
    calculator.process_bytes(typeName.c_str(), typeName.size());
    calculator.process_bytes(&typeVersion, sizeof(typeVersion));
    return calculator.checksum() ^ _fields.calculateChecksum();
}

const DocumentType *
Document::deserializeDocHeaderAndType(
        const DocumentTypeRepo& repo, ByteBuffer& buffer, DocumentId& id,
        const DocumentType * docType)
{
    deserializeDocHeader(buffer, id);

    vespalib::stringref docTypeName(buffer.getBufferAtPos());
    buffer.incPos(docTypeName.size() + 1); // Skip 0-byte too
    {
        int16_t docTypeVersion;  // version not supported anymore
        buffer.getShortNetwork(docTypeVersion);
    }
    const DocumentType *docTypeNew = 0;

    if (! ((docType != NULL) && (docType->getName() == docTypeName))) {
        docTypeNew = repo.getDocumentType(docTypeName);
        if (!docTypeNew) {
            throw DocumentTypeNotFoundException(docTypeName, VESPA_STRLOC);
        }
    }
    return docTypeNew;
}

namespace {
void versionError(uint16_t version) __attribute__((noinline));
void mainDocumentError(int64_t len) __attribute__((noinline));
void notEnoughDocumentError(int32_t len, int64_t remaining) __attribute__((noinline));

void versionError(uint16_t version) {
    throw DeserializeException(make_string( "Unrecognized serialization version %d", version), VESPA_STRLOC);
}

void mainDocumentError(int64_t len) {
    throw DeserializeException(make_string(
            "Document lengths past %i is not supported. Corrupt data said length is %" PRId64 " bytes",
            std::numeric_limits<int>::max(), len), VESPA_STRLOC);
}

void notEnoughDocumentError(int32_t len, int64_t remaining) {
    throw DeserializeException(make_string( "Buffer said document length is %i bytes, but only %li bytes remain in buffer", len, remaining));
}

}

void
Document::deserializeDocHeader(ByteBuffer& buffer, DocumentId& id) {
    int16_t version;
    int32_t len;
    buffer.getShortNetwork(version);

    if ( ! isLegalVersion(version) ) {
        versionError(version);
    } else if (version < 7) {
        int64_t tmpLen = 0;
        buffer.getInt2_4_8Bytes(tmpLen);
        if (tmpLen > std::numeric_limits<int>::max()) {
            mainDocumentError(tmpLen);
        } else {
            len = static_cast<int32_t>(tmpLen)
                - ByteBuffer::getSerializedSize2_4_8Bytes(tmpLen)
                - sizeof(uint16_t);
        }
    } else {
        buffer.getIntNetwork(len);
    }

    if (len > (long)buffer.getRemaining()) {
        notEnoughDocumentError(len, buffer.getRemaining());
    } else {
        nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
        id = DocumentId(stream);
        buffer.incPos(stream.rp());
        unsigned char contentByte;
        buffer.getByte(contentByte);
    }
}

void Document::serializeHeader(ByteBuffer& buffer) const {
    nbostream stream;
    serializeHeader(stream);
    buffer.putBytes(stream.peek(), stream.size());
}

void Document::serializeHeader(nbostream& stream) const {
    VespaDocumentSerializer serializer(stream);
    serializer.write(*this, WITHOUT_BODY);
}

void Document::serializeBody(ByteBuffer& buffer) const {
    nbostream stream;
    serializeBody(stream);
    buffer.putBytes(stream.peek(), stream.size());
}

bool Document::hasBodyField() const {
    for (document::StructuredFieldValue::const_iterator it(getFields().begin()), mt(getFields().end());
         it != mt;
         ++it)
    {
        if ( ! it.field().isHeaderField() ) {
            return true;
        }
    }
    return false;
}

void Document::serializeBody(nbostream& stream) const {
    if (hasBodyField()) {
        VespaDocumentSerializer serializer(stream);
        serializer.write(_fields, BodyFields());
    }
}

void Document::deserialize(const DocumentTypeRepo& repo, vespalib::nbostream & os) {
    VespaDocumentDeserializer deserializer(repo, os, 0);
    try {
        deserializer.read(*this);
    } catch (const IllegalStateException &e) {
        throw DeserializeException(vespalib::string("Buffer out of bounds: ") + e.what());
    }
}

void Document::deserialize(const DocumentTypeRepo& repo, ByteBuffer& data) {
    nbostream stream(data.getBufferAtPos(), data.getRemaining());
    deserialize(repo, stream);
    data.incPos(data.getRemaining() - stream.size());
}

void Document::deserialize(const DocumentTypeRepo& repo, ByteBuffer& header, ByteBuffer& body) {
    deserializeHeader(repo, header);
    deserializeBody(repo, body);
}

void Document::deserializeHeader(const DocumentTypeRepo& repo,
                           ByteBuffer& header) {
    nbostream stream(header.getBufferAtPos(), header.getRemaining());
    VespaDocumentDeserializer deserializer(repo, stream, 0);
    deserializer.read(*this);
    header.incPos(header.getRemaining() - stream.size());
}

void Document::deserializeBody(const DocumentTypeRepo& repo, ByteBuffer& body) {
    nbostream body_stream(body.getBufferAtPos(), body.getRemaining());
    VespaDocumentDeserializer
        body_deserializer(repo, body_stream, getFields().getVersion());
    body_deserializer.readStructNoReset(getFields());
    body.incPos(body.getRemaining() - body_stream.size());
}

size_t
Document::getSerializedSize() const
{
    // Temporary non-optimal (but guaranteed correct) implementation.
    return serialize()->getLength();
}

StructuredFieldValue::StructuredIterator::UP
Document::getIterator(const Field* first) const
{
    return _fields.getIterator(first);
}

} // document
