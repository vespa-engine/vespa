// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docentry.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>

namespace storage::spi {

namespace {

class DocEntryWithId final : public DocEntry {
public:
    DocEntryWithId(Timestamp t, int metaFlags, const DocumentId &docId);
    ~DocEntryWithId();
    vespalib::string toString() const override;
    const DocumentId* getDocumentId() const override { return _documentId.get(); }
private:
    SizeType getOwnSize() const override{ return sizeof(DocEntryWithId); }
    DocumentIdUP _documentId;
};

class DocEntryWithDoc final : public DocEntry {
public:
    DocEntryWithDoc(Timestamp t, int metaFlags, DocumentUP doc);

    /**
     * Constructor that can be used by providers that already know
     * the serialized size of the document, so the potentially expensive
     * call to getSerializedSize can be avoided. This value shall be the size of the document _before_
     * any field filtering is performed.
     */
    DocEntryWithDoc(Timestamp t, int metaFlags, DocumentUP doc, size_t serializedDocumentSize);
    ~DocEntryWithDoc();
    vespalib::string toString() const override;
    const Document* getDocument() const override { return _document.get(); }
    const DocumentId* getDocumentId() const override { return &_document->getId(); }
    DocumentUP releaseDocument() override { return std::move(_document); }
private:
    SizeType getOwnSize() const override { return sizeof(DocEntryWithDoc); }
    DocumentUP _document;
};

DocEntryWithDoc::DocEntryWithDoc(Timestamp t, int metaFlags, DocumentUP doc)
    : DocEntry(t, metaFlags, doc->serialize().size()),
      _document(std::move(doc))
{ }

DocEntryWithDoc::DocEntryWithDoc(Timestamp t, int metaFlags, DocumentUP doc, size_t serializedDocumentSize)
    : DocEntry(t, metaFlags, serializedDocumentSize),
      _document(std::move(doc))
{ }

DocEntryWithId::DocEntryWithId(Timestamp t, int metaFlags, const DocumentId& docId)
    : DocEntry(t, metaFlags, docId.getSerializedSize()),
      _documentId(std::make_unique<DocumentId>(docId))
{ }

DocEntryWithId::~DocEntryWithId() = default;
DocEntryWithDoc::~DocEntryWithDoc() = default;

vespalib::string
DocEntryWithId::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << getTimestamp() << ", " << getFlags() << ", " << *_documentId << ")";
    return out.str();
}

vespalib::string
DocEntryWithDoc::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << getTimestamp() << ", " << getFlags() << ", ";
    if (_document.get()) {
        out << "Doc(" << _document->getId() << ")";
    } else {
        out << "metadata only";
    }
    out << ")";
    return out.str();
}

}

DocEntry::UP
DocEntry::create(Timestamp t, int metaFlags) {
    return std::make_unique<DocEntry>(t, metaFlags);
}
DocEntry::UP
DocEntry::create(Timestamp t, int metaFlags, const DocumentId &docId) {
    return std::make_unique<DocEntryWithId>(t, metaFlags, docId);
}
DocEntry::UP
DocEntry::create(Timestamp t, int metaFlags, DocumentUP doc) {
    return std::make_unique<DocEntryWithDoc>(t, metaFlags, std::move(doc));
}
DocEntry::UP
DocEntry::create(Timestamp t, int metaFlags, DocumentUP doc, SizeType serializedDocumentSize) {
    return std::make_unique<DocEntryWithDoc>(t, metaFlags, std::move(doc), serializedDocumentSize);
}

DocEntry::~DocEntry() = default;

DocumentUP
DocEntry::releaseDocument() {
    return {};
}

vespalib::string
DocEntry::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << _timestamp << ", " << _metaFlags << ", metadata only)";
    return out.str();
}

std::ostream &
operator << (std::ostream & os, const DocEntry & r) {
    return os << r.toString();
}

}
