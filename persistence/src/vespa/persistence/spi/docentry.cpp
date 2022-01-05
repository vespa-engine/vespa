// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docentry.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>
#include <cassert>

namespace storage::spi {

DocEntry::DocEntry(Timestamp t, int metaFlags, DocumentUP doc)
    : _timestamp(t),
      _metaFlags(metaFlags),
      _persistedDocumentSize(doc->serialize().size()),
      _size(_persistedDocumentSize + sizeof(DocEntry)),
      _documentId(),
      _document(std::move(doc))
{ }

DocEntry::DocEntry(Timestamp t, int metaFlags, DocumentUP doc, size_t serializedDocumentSize)
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
      _documentId(std::make_unique<DocumentId>(docId)),
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

DocEntry::~DocEntry() = default;

const DocumentId*
DocEntry::getDocumentId() const {
    return (_document ? &_document->getId() : _documentId.get());
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
    if (_documentId) {
        out << *_documentId;
    } else if (_document.get()) {
        out << "Doc(" << _document->getId() << ")";
    } else {
        out << "metadata only";
    }
    out << ")";
    return out.str();
}

std::ostream &
operator << (std::ostream & os, const DocEntry & r) {
    return os << r.toString();
}

}
