// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentupdate.h"
#include "documentupdateflags.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bufferexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/xmlstream.h>

using vespalib::IllegalArgumentException;
using vespalib::IllegalStateException;
using vespalib::nbostream;
using vespalib::make_string;
using vespalib::string;
using namespace vespalib::xml;

namespace document {

namespace {

constexpr unsigned char CONTENT_HASTYPE = 0x01;

vespalib::stringref
readCStr(nbostream & stream) {
    const char * s = stream.peek();
    size_t sz = strnlen(s, stream.size());
    stream.adjustReadPos(sz+1);
    return vespalib::stringref(s, sz);
}

std::pair<const DocumentType *, DocumentId>
deserializeTypeAndId(const DocumentTypeRepo& repo, vespalib::nbostream & stream) {
    DocumentId docId(stream);

    // Read content bit vector.
    unsigned char content = 0x00;
    stream >> content;

    // Why on earth do we have this whether we have type part?
    // We need type for object to work, so just throwing exception if it's
    // not there.
    if((content & CONTENT_HASTYPE) == 0) {
        throw IllegalStateException("Missing document type", VESPA_STRLOC);
    }

    vespalib::stringref typestr = readCStr(stream);

    int16_t version = 0;
    stream >> version;
    const DocumentType *type = repo.getDocumentType(typestr);
    if (!type) {
        throw DocumentTypeNotFoundException(typestr, VESPA_STRLOC);
    }
    return std::make_pair(type, docId);
}

const DocumentType *
deserializeHeader(const DocumentTypeRepo &repo, vespalib::nbostream & stream, vespalib::stringref & documentId)
{
    size_t sz = strnlen(stream.peek(), stream.size());
    documentId = vespalib::stringref(stream.peek(), sz);
    stream.adjustReadPos(sz + 1);
    vespalib::stringref typestr = readCStr(stream);
    int16_t version = 0;
    stream >> version;
    const DocumentType * docType =  repo.getDocumentType(typestr);
    if (!docType) {
        throw DocumentTypeNotFoundException(typestr, VESPA_STRLOC);
    }
    return docType;
}

}


DocumentUpdate::DocumentUpdate(const DocumentTypeRepo & repo, const DataType &type, const DocumentId& id)
    : _documentId(id),
      _type(&type),
      _repo(&repo),
      _backing(),
      _updates(),
      _fieldPathUpdates(),
      _createIfNonExistent(false),
      _needHardReserialize(false)
{
    if (!type.getClass().inherits(DocumentType::classId)) {
        throw IllegalArgumentException("Cannot generate a document with non-document type " + type.toString() + ".", VESPA_STRLOC);
    }
    serializeHeader();
}

DocumentUpdate::DocumentUpdate()
    : _documentId(),
      _type(DataType::DOCUMENT),
      _repo(nullptr),
      _backing(),
      _updates(),
      _fieldPathUpdates(),
      _createIfNonExistent(false),
      _needHardReserialize(false)
{
}

DocumentUpdate::~DocumentUpdate() = default;

bool
DocumentUpdate::operator==(const DocumentUpdate& other) const
{
    return (_backing.size() == other._backing.size()) &&
           (memcmp(_backing.peek(), other._backing.peek(), _backing.size()) == 0);
}

const DocumentType&
DocumentUpdate::getType() const {
    return static_cast<const DocumentType &> (*_type);
}

const DocumentUpdate::FieldUpdateV &
DocumentUpdate::getUpdates() const {
    ensureDeserialized();
    return _updates;
}

const DocumentUpdate::FieldPathUpdateV &
DocumentUpdate::getFieldPathUpdates() const {
    ensureDeserialized();
    return _fieldPathUpdates;
}

void
DocumentUpdate::eagerDeserialize() const {
    ensureDeserialized();
}

void DocumentUpdate::lazyDeserialize(const DocumentTypeRepo & repo, nbostream & stream) {
    size_t start(stream.rp());
    vespalib::stringref voidId;
    deserializeHeader(repo, stream, voidId);
    deserializeBody(repo, stream);
    stream.rp(start);
}
void DocumentUpdate::ensureDeserialized() const {
    if (_updates.empty() && _fieldPathUpdates.empty()) {
        const_cast<DocumentUpdate &>(*this).lazyDeserialize(*_repo, const_cast<nbostream &>(_backing));
    }
}

DocumentUpdate&
DocumentUpdate::addUpdate(const FieldUpdate& update) {
    ensureDeserialized();
    _updates.push_back(update);
    reserialize();
    return *this;
}

DocumentUpdate&
DocumentUpdate::addFieldPathUpdate(const FieldPathUpdate::CP& update) {
    ensureDeserialized();
    _fieldPathUpdates.push_back(update);
    reserialize();
    return *this;
}

void
DocumentUpdate::setCreateIfNonExistent(bool value) {
    ensureDeserialized();
    _createIfNonExistent = value;
    reserialize();
}

bool
DocumentUpdate::getCreateIfNonExistent() const {
    ensureDeserialized();
    return _createIfNonExistent;
}

void
DocumentUpdate::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    ensureDeserialized();
    out << "DocumentUpdate(";
    if (_type) {
        _type->print(out, verbose, indent + "    ");
    } else {
        out << "No document type given";
    }
    std::string nestedIndent = indent + "  ";
    out << "\n" << nestedIndent << "CreateIfNonExistent(" << (_createIfNonExistent ? "true" : "false") << ")";

    for(const auto & update : _updates) {
        out << "\n" << indent << "  ";
        update.print(out, verbose, nestedIndent);
    }
    if (!_updates.empty()) {
        out << "\n" << indent;
    }
    for (const auto & update : _fieldPathUpdates) {
        out << "\n" << indent << "  ";
        update->print(out, verbose, nestedIndent);
    }
    if (!_fieldPathUpdates.empty()) {
        out << "\n" << indent;
    }
    out << ")";
}

// Apply this update to the given document.
void
DocumentUpdate::applyTo(Document& doc) const
{
    ensureDeserialized();
    const DocumentType& type = doc.getType();
    if (_type->getName() != type.getName()) {
        string err = make_string("Can not apply a \"%s\" document update to a \"%s\" document.",
                                 _type->getName().c_str(), type.getName().c_str());
        throw IllegalArgumentException(err, VESPA_STRLOC);
    }

    // Apply legacy updates
    for(const auto & update : _updates) {
        update.applyTo(doc);
    }
    TransactionGuard guard(doc);
    // Apply fieldpath updates
    for (const auto & update : _fieldPathUpdates) {
        update->applyTo(doc);
    }
}

void
DocumentUpdate::serializeHeader() {
    string id_string = _documentId.getScheme().toString();
    _backing.write(id_string.data(), id_string.size());
    _backing << static_cast<uint8_t>(0);
    _backing.write(getType().getName().c_str(), getType().getName().size() + 1);
    _backing << static_cast<uint16_t>(0); // version
    _backing << static_cast<uint32_t>(0); // Number of updates
    _backing << static_cast<uint32_t>(0); // Number of field path updates
}

void
DocumentUpdate::serializeHEAD(nbostream &stream) const
{
    VespaDocumentSerializer serializer(stream);
    serializer.writeHEAD(*this);
}

int
DocumentUpdate::serializeFlags(int size_) const
{
    DocumentUpdateFlags flags;
    flags.setCreateIfNonExistent(_createIfNonExistent);
    return flags.injectInto(size_);
}

// Deserialize the content of the given buffer into this document update.
DocumentUpdate::UP
DocumentUpdate::create42(const DocumentTypeRepo& repo, vespalib::nbostream & stream)
{
    auto update = std::make_unique<DocumentUpdate>();
    update->init42(repo, stream);
    return update;
}

DocumentUpdate::UP
DocumentUpdate::createHEAD(const DocumentTypeRepo& repo, ByteBuffer& buffer)
{
    vespalib::nbostream is(buffer.getBufferAtPos(), buffer.getRemaining());
    auto update = std::make_unique<DocumentUpdate>();
    update->initHEAD(repo, is);
    buffer.setPos(buffer.getPos() + is.rp());
    return update;
}

DocumentUpdate::UP
DocumentUpdate::createHEAD(const DocumentTypeRepo& repo, vespalib::nbostream stream)
{
    auto update = std::make_unique<DocumentUpdate>();
    update->initHEAD(repo, std::move(stream));
    return update;
}

void
DocumentUpdate::init42(const DocumentTypeRepo & repo, vespalib::nbostream & stream)
{
    _repo = &repo;
    deserialize42(repo, stream);
    reserialize();
}
void
DocumentUpdate::initHEAD(const DocumentTypeRepo & repo, vespalib::nbostream && stream)
{
    _repo = &repo;
    _backing = std::move(stream);
    size_t startPos = _backing.rp();
    vespalib::stringref docId;
    _type = deserializeHeader(repo, _backing, docId);
    _documentId.set(docId);
    _backing.rp(startPos);
}

void
DocumentUpdate::initHEAD(const DocumentTypeRepo & repo, vespalib::nbostream & stream)
{
    size_t startPos = stream.rp();
    vespalib::stringref docId;
    _type = deserializeHeader(repo, stream, docId);
    _documentId.set(docId);
    deserializeBody(repo, stream);
    size_t sz = stream.rp() - startPos;
    _backing = nbostream(stream.peek() - sz, sz);
}

void
DocumentUpdate::deserialize42(const DocumentTypeRepo& repo, vespalib::nbostream & stream)
{
    size_t pos = stream.rp();
    try{
        int16_t version(0);
        stream >> version;
        std::pair<const DocumentType *, DocumentId> typeAndId(deserializeTypeAndId(repo, stream));
        _type = typeAndId.first;
        _documentId = typeAndId.second;
        // Read field updates, if any.
        if (! stream.empty()) {
            int32_t sizeAndFlags = 0;
            stream >> sizeAndFlags;
            int numUpdates = deserializeFlags(sizeAndFlags);
            _updates.reserve(numUpdates);
            for (int i = 0; i < numUpdates; i++) {
                _updates.emplace_back(repo, *typeAndId.first, stream);
            }
        }
    } catch (const DeserializeException &) {
        stream.rp(pos);
        throw;
    } catch (const BufferOutOfBoundsException &) {
        stream.rp(pos);
        throw;
    }
}

void
DocumentUpdate::deserializeBody(const DocumentTypeRepo &repo, vespalib::nbostream &stream)
{
    _updates.clear();
    _fieldPathUpdates.clear();
    size_t pos = stream.rp();
    try {
        // Read field updates, if any.
        if ( ! stream.empty() ) {
            int32_t numUpdates = 0;
            stream >> numUpdates;
            _updates.reserve(numUpdates);
            for (int i = 0; i < numUpdates; i++) {
                _updates.emplace_back(repo, *_type, stream);
            }
        }
        // Read fieldpath updates, if any
        int32_t sizeAndFlags = 0;
        stream >> sizeAndFlags;
        int numUpdates = deserializeFlags(sizeAndFlags);
        _fieldPathUpdates.reserve(numUpdates);
        for (int i = 0; i < numUpdates; ++i) {
            _fieldPathUpdates.emplace_back(FieldPathUpdate::createInstance(repo, *_type, stream).release());
        }
    } catch (const DeserializeException &) {
        stream.rp(pos);
        throw;
    } catch (const BufferOutOfBoundsException &) {
        stream.rp(pos);
        throw;
    }
}

int
DocumentUpdate::deserializeFlags(int sizeAndFlags)
{
    _createIfNonExistent = DocumentUpdateFlags::extractFlags(sizeAndFlags).getCreateIfNonExistent();
    return DocumentUpdateFlags::extractValue(sizeAndFlags);
}

void
DocumentUpdate::printXml(XmlOutputStream& xos) const
{
    ensureDeserialized();
    xos << XmlTag("document")
        << XmlAttribute("type", _type->getName())
        << XmlAttribute("id", getId().toString());
    for(const auto & update : _updates) {
        xos << XmlTag("alter") << XmlAttribute("field", update.getField().getName());
        update.printXml(xos);
        xos << XmlEndTag();
    }
    xos << XmlEndTag();
}

void
DocumentUpdate::reserialize()
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    _needHardReserialize = true;
    serializer.writeHEAD(*this);
    _backing = std::move(stream);
    _needHardReserialize = false;
}

}
