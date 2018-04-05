// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentupdate.h"
#include "documentupdateflags.h"
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bufferexceptions.h>
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

IMPLEMENT_IDENTIFIABLE(DocumentUpdate, vespalib::Identifiable);

// Declare content bits.
static const unsigned char CONTENT_HASTYPE = 0x01;

typedef std::vector<FieldUpdate> FieldUpdateList;
typedef std::vector<FieldPathUpdate::CP> FieldPathUpdateList;

DocumentUpdate::DocumentUpdate()
    : _documentId("doc::"),
      _type(DataType::DOCUMENT),
      _updates(),
      _version(Document::getNewestSerializationVersion()),
      _createIfNonExistent(false)
{
}

DocumentUpdate::DocumentUpdate(const DataType &type, const DocumentId& id)
    : _documentId(id),
      _type(&type),
      _updates(),
      _version(Document::getNewestSerializationVersion()),
      _createIfNonExistent(false)
{
    if (!type.getClass().inherits(DocumentType::classId)) {
        throw IllegalArgumentException("Cannot generate a document with non-document type " + type.toString() + ".",
                                       VESPA_STRLOC);
    }
}

DocumentUpdate::DocumentUpdate(const DocumentTypeRepo& repo,
                               ByteBuffer& buffer,
                               SerializeVersion serializeVersion)
    : _documentId("doc::"),
      _type(DataType::DOCUMENT),
      _updates(),
      _version(Document::getNewestSerializationVersion()),
      _createIfNonExistent(false)
{
    switch (serializeVersion) {
    case SerializeVersion::SERIALIZE_HEAD:
        deserializeHEAD(repo, buffer);
        break;
    case SerializeVersion::SERIALIZE_42:
        deserialize42(repo, buffer);
        break;
    default:
        throw IllegalArgumentException("bad serializeVersion provided.", VESPA_STRLOC);
    }
}

DocumentUpdate::~DocumentUpdate() { }


bool
DocumentUpdate::operator==(const DocumentUpdate& other) const
{
    if (_documentId != other._documentId) return false;
    if (*_type != *other._type) return false;
    if (_updates.size() != other._updates.size()) return false;
    for (std::size_t i = 0, n = _updates.size(); i < n; ++i) {
        if (_updates[i] != other._updates[i]) return false;
    }
    if (_fieldPathUpdates.size() != other._fieldPathUpdates.size()) return false;
    for (std::size_t i = 0, n = _fieldPathUpdates.size(); i < n; ++i) {
        if (*_fieldPathUpdates[i] != *other._fieldPathUpdates[i]) return false;
    }
    if (_createIfNonExistent != other._createIfNonExistent) return false;
    return true;
}

bool
DocumentUpdate::affectsDocumentBody() const
{
    for(const auto & update : _updates) {
        if (!update.getField().isHeaderField()) {
            return true;
        }
    }
    for (const auto & update : _fieldPathUpdates) {
        if (update->affectsDocumentBody(*_type)) {
            return true;
        }
    }
    return false;
}

const DocumentType&
DocumentUpdate::getType() const {
    return static_cast<const DocumentType &> (*_type);
}

DocumentUpdate&
DocumentUpdate::addUpdate(const FieldUpdate& update) {
    _updates.push_back(update);
    return *this;
}

DocumentUpdate&
DocumentUpdate::addFieldPathUpdate(const FieldPathUpdate::CP& update) {
    _fieldPathUpdates.push_back(update);
    return *this;
}

DocumentUpdate*
DocumentUpdate::clone() const {
    return new DocumentUpdate(*this);
}

void
DocumentUpdate::print(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
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
DocumentUpdate::serialize42(nbostream &stream) const
{
    VespaDocumentSerializer serializer(stream);
    serializer.write42(*this);
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

namespace {
    std::pair<const DocumentType *, DocumentId>
    deserializeTypeAndId(const DocumentTypeRepo& repo, ByteBuffer& buffer) {
        nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
        DocumentId docId(stream);
        buffer.incPos(stream.rp());

        // Read content bit vector.
        unsigned char content = 0x00;
        buffer.getByte(content);

        // Why on earth do we have this whether we have type part?
        // We need type for object to work, so just throwing exception if it's
        // not there.
        if((content & CONTENT_HASTYPE) == 0) {
            throw IllegalStateException("Missing document type", VESPA_STRLOC);
        }

        vespalib::stringref typestr = buffer.getBufferAtPos();
        buffer.incPos(typestr.length() + 1);

        int16_t version = 0;
        buffer.getShortNetwork(version);
        const DocumentType *type = repo.getDocumentType(typestr);
        if (!type) {
            throw DocumentTypeNotFoundException(typestr, VESPA_STRLOC);
        }
        return std::make_pair(type, docId);
    }
}

// Deserialize the content of the given buffer into this document update.
DocumentUpdate::UP
DocumentUpdate::create42(const DocumentTypeRepo& repo, ByteBuffer& buffer)
{
    return std::make_unique<DocumentUpdate>(repo, buffer,
                                            SerializeVersion::SERIALIZE_42);
}

DocumentUpdate::UP
DocumentUpdate::createHEAD(const DocumentTypeRepo& repo, ByteBuffer& buffer)
{
    return std::make_unique<DocumentUpdate>(repo, buffer,
                                            SerializeVersion::SERIALIZE_HEAD);
}

void
DocumentUpdate::deserialize42(const DocumentTypeRepo& repo, ByteBuffer& buffer)
{
    int pos = buffer.getPos();
    try{
        buffer.getShortNetwork(_version);

        std::pair<const DocumentType *, DocumentId> typeAndId(deserializeTypeAndId(repo, buffer));
        _type = typeAndId.first;
        _documentId = typeAndId.second;
        // Read field updates, if any.
        if(buffer.getRemaining() > 0) {
            int sizeAndFlags = 0;
            buffer.getIntNetwork(sizeAndFlags);
            int numUpdates = deserializeFlags(sizeAndFlags);
            _updates.reserve(numUpdates);
            for (int i = 0; i < numUpdates; i++) {
                _updates.emplace_back(repo, *typeAndId.first, buffer, _version);
            }
        }
    } catch (const DeserializeException &) {
        buffer.setPos(pos);
        throw;
    } catch (const BufferOutOfBoundsException &) {
        buffer.setPos(pos);
        throw;
    }
}

void
DocumentUpdate::deserializeHEAD(const DocumentTypeRepo &repo, ByteBuffer &buffer)
{
    int pos = buffer.getPos();
    try {
        nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
        _documentId = DocumentId(stream);
        buffer.incPos(stream.rp());

        vespalib::stringref typestr = buffer.getBufferAtPos();
        buffer.incPos(typestr.length() + 1);

        int16_t version = 0;
        buffer.getShortNetwork(version);
        const DocumentType *docType = repo.getDocumentType(typestr);
        if (!docType) {
            throw DocumentTypeNotFoundException(typestr, VESPA_STRLOC);
        }
        _type = docType;

        // Read field updates, if any.
        if (buffer.getRemaining() > 0) {
            int numUpdates = 0;
            buffer.getIntNetwork(numUpdates);
            _updates.reserve(numUpdates);
            for (int i = 0; i < numUpdates; i++) {
                _updates.emplace_back(repo, *docType, buffer, 8);
            }
        }
        // Read fieldpath updates, if any
        int sizeAndFlags = 0;
        buffer.getIntNetwork(sizeAndFlags);
        int numUpdates = deserializeFlags(sizeAndFlags);
        _fieldPathUpdates.reserve(numUpdates);
        for (int i = 0; i < numUpdates; ++i) {
            _fieldPathUpdates.emplace_back(FieldPathUpdate::createInstance(repo, *_type, buffer, 8).release());
        }
    } catch (const DeserializeException &) {
        buffer.setPos(pos);
        throw;
    } catch (const BufferOutOfBoundsException &) {
        buffer.setPos(pos);
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
DocumentUpdate::onDeserialize42(const DocumentTypeRepo &repo,
                                ByteBuffer& buffer)
{
    deserialize42(repo, buffer);
}

void
DocumentUpdate::printXml(XmlOutputStream& xos) const
{
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

}
