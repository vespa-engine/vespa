// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docentry.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <sstream>

namespace storage::spi {

namespace {

class DocEntryWithId final : public DocEntry {
public:
    DocEntryWithId(Timestamp t, DocumentMetaFlags metaFlags, const DocumentId &docId);
    ~DocEntryWithId();
    vespalib::string toString() const override;
    const DocumentId* getDocumentId() const override { return & _documentId; }
    vespalib::stringref getDocumentType() const override { return _documentId.getDocType(); }
    GlobalId getGid() const override { return _documentId.getGlobalId(); }
private:
    DocumentId _documentId;
};

class DocEntryWithTypeAndGid final : public DocEntry {
public:
    DocEntryWithTypeAndGid(Timestamp t, DocumentMetaFlags metaFlags, vespalib::stringref docType, GlobalId gid);
    ~DocEntryWithTypeAndGid();
    vespalib::string toString() const override;
    vespalib::stringref getDocumentType() const override { return _type; }
    GlobalId getGid() const override { return _gid; }
private:
    vespalib::string _type;
    GlobalId         _gid;
};

class DocEntryWithDoc final : public DocEntry {
public:
    DocEntryWithDoc(Timestamp t, DocumentUP doc);

    /**
     * Constructor that can be used by providers that already know
     * the serialized size of the document, so the potentially expensive
     * call to getSerializedSize can be avoided. This value shall be the size of the document _before_
     * any field filtering is performed.
     */
    DocEntryWithDoc(Timestamp t, DocumentUP doc, size_t serializedDocumentSize);
    ~DocEntryWithDoc();
    vespalib::string toString() const override;
    const Document* getDocument() const override { return _document.get(); }
    const DocumentId* getDocumentId() const override { return &_document->getId(); }
    DocumentUP releaseDocument() override { return std::move(_document); }
    vespalib::stringref getDocumentType() const override { return _document->getId().getDocType(); }
    GlobalId getGid() const override { return _document->getId().getGlobalId(); }
private:
    DocumentUP _document;
};

DocEntryWithDoc::DocEntryWithDoc(Timestamp t, DocumentUP doc)
    : DocEntry(t, DocumentMetaFlags::NONE, doc->serialize().size()),
      _document(std::move(doc))
{ }

DocEntryWithDoc::DocEntryWithDoc(Timestamp t, DocumentUP doc, size_t serializedDocumentSize)
    : DocEntry(t, DocumentMetaFlags::NONE, serializedDocumentSize),
      _document(std::move(doc))
{ }

DocEntryWithId::DocEntryWithId(Timestamp t, DocumentMetaFlags metaFlags, const DocumentId& docId)
    : DocEntry(t, metaFlags, docId.getSerializedSize()),
      _documentId(docId)
{ }

DocEntryWithTypeAndGid::DocEntryWithTypeAndGid(Timestamp t, DocumentMetaFlags metaFlags, vespalib::stringref docType, GlobalId gid)
    : DocEntry(t, metaFlags, docType.size() + sizeof(gid)),
      _type(docType),
      _gid(gid)
{ }

DocEntryWithTypeAndGid::~DocEntryWithTypeAndGid() = default;
DocEntryWithId::~DocEntryWithId() = default;
DocEntryWithDoc::~DocEntryWithDoc() = default;

vespalib::string
DocEntryWithId::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << getTimestamp() << ", " << int(getFlags()) << ", " << _documentId << ")";
    return out.str();
}

vespalib::string
DocEntryWithTypeAndGid::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << getTimestamp() << ", " << int(getFlags()) << ", " << _type << ", " << _gid << ")";
    return out.str();
}

vespalib::string
DocEntryWithDoc::toString() const
{
    std::ostringstream out;
    out << "DocEntry(" << getTimestamp() << ", " << int(getFlags()) << ", ";
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
DocEntry::create(Timestamp t, DocumentMetaFlags metaFlags) {
    return UP(new DocEntry(t, metaFlags));
}
DocEntry::UP
DocEntry::create(Timestamp t, DocumentMetaFlags metaFlags, const DocumentId &docId) {
    return std::make_unique<DocEntryWithId>(t, metaFlags, docId);
}
DocEntry::UP
DocEntry::create(Timestamp t, DocumentMetaFlags metaFlags, vespalib::stringref docType, GlobalId gid) {
    return std::make_unique<DocEntryWithTypeAndGid>(t, metaFlags, docType, gid);
}
DocEntry::UP
DocEntry::create(Timestamp t, DocumentUP doc) {
    return std::make_unique<DocEntryWithDoc>(t, std::move(doc));
}
DocEntry::UP
DocEntry::create(Timestamp t, DocumentUP doc, SizeType serializedDocumentSize) {
    return std::make_unique<DocEntryWithDoc>(t, std::move(doc), serializedDocumentSize);
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
    out << "DocEntry(" << _timestamp << ", " << int(_metaFlags) << ", metadata only)";
    return out.str();
}

std::ostream &
operator << (std::ostream & os, const DocEntry & r) {
    return os << r.toString();
}

}
