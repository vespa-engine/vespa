// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docentry.h"
#include <vespa/document/fieldvalue/document.h>
#include <sstream>
#include <cassert>

namespace storage {
namespace spi {

DocEntry::DocEntry(Timestamp t, int metaFlags, DocumentUP doc)
    : _timestamp(t),
      _metaFlags(metaFlags),
      _persistedDocumentSize(doc->getSerializedSize()),
      _size(_persistedDocumentSize + sizeof(DocEntry)),
      _documentId(),
      _document(std::move(doc))
{ }

DocEntry::DocEntry(Timestamp t,
         int metaFlags,
         DocumentUP doc,
         size_t serializedDocumentSize)
    : _timestamp(t),
      _metaFlags(metaFlags),
      _persistedDocumentSize(serializedDocumentSize),
      _size(_persistedDocumentSize + sizeof(DocEntry)),
      _documentId(),
      _document(std::move(doc))
{ }

DocEntry::DocEntry(Timestamp t, int metaFlags, const DocumentId& docId)
    : _timestamp(t),
      _metaFlags(metaFlags),
      _persistedDocumentSize(docId.getSerializedSize()),
      _size(_persistedDocumentSize + sizeof(DocEntry)),
      _documentId(new DocumentId(docId)),
      _document()
{ }

DocEntry::DocEntry(Timestamp t, int metaFlags)
    : _timestamp(t),
      _metaFlags(metaFlags),
      _persistedDocumentSize(0),
      _size(sizeof(DocEntry)),
      _documentId(),
      _document()
{ }

DocEntry::~DocEntry() { }

DocEntry* 
DocEntry::clone() const {
    DocEntry* ret;
    if (_documentId.get() != 0) {
        ret = new DocEntry(_timestamp, _metaFlags, *_documentId);
        ret->setPersistedDocumentSize(_persistedDocumentSize);
    } else if (_document.get()) {
        ret = new DocEntry(_timestamp, _metaFlags,
                           DocumentUP(new Document(*_document)),
                           _persistedDocumentSize);
    } else {
        ret = new DocEntry(_timestamp, _metaFlags);
        ret->setPersistedDocumentSize(_persistedDocumentSize);
    }
    return ret;
}

const DocumentId*
DocEntry::getDocumentId() const {
    return (_document.get() != 0 ? &_document->getId()
                                 : _documentId.get());
}

DocumentUP
DocEntry::releaseDocument() {
    return std::move(_document);
}

DocEntry::SizeType
DocEntry::getDocumentSize() const
{
    assert(_size >= sizeof(DocEntry));
    return _size - sizeof(DocEntry);
}

vespalib::string
DocEntry::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << _timestamp << ", " << _metaFlags << ", ";
    if (_documentId.get() != 0) {
        out << _documentId->toString();
    } else if (_document.get()) {
        out << "Doc(" << _document->getId().toString() << ")";
    } else {
        out << "metadata only";
    }
    out << ")";
    return out.str();
}

void
DocEntry::prettyPrint(std::ostream& out) const
{
    std::string flags;
    if (_metaFlags == REMOVE_ENTRY) {
        flags = " (remove)";
    }

    out << "DocEntry(Timestamp: " << _timestamp
        << ", size " << getPersistedDocumentSize() << ", ";
    if (_documentId.get() != 0) {
        out << _documentId->toString();
    } else if (_document.get()) {
        out << "Doc(" << _document->getId().toString() << ")";
    } else {
        out << "metadata only";
    }
    out << flags << ")";
}

std::ostream &
operator << (std::ostream & os, const DocEntry & r) {
    return os << r.toString();
}

bool
DocEntry::operator==(const DocEntry& entry) const {
    if (_timestamp != entry._timestamp) {
        return false;
    }

    if (_metaFlags != entry._metaFlags) {
        return false;
    }

    if (_documentId.get()) {
        if (!entry._documentId.get()) {
            return false;
        }

        if (*_documentId != *entry._documentId) {
            return false;
        }
    } else {
        if (entry._documentId.get()) {
            return false;
        }
    }

    if (_document.get()) {
        if (!entry._document.get()) {
            return false;
        }

        if (*_document != *entry._document) {
            return false;
        }
    } else {
        if (entry._document.get()) {
            return false;
        }
    }
    if (_persistedDocumentSize != entry._persistedDocumentSize) {
        return false;
    }

    return true;
}

} // spi
} // storage


