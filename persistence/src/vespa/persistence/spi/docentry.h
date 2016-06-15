// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::DocEntry
 * \ingroup spi
 *
 * \brief Represents a document with metadata stored.
 *
 * To do merge, all SPI implementations need some common metadata. To do iterate
 * efficiently, we also want options to only return metadata or similar. Thus
 * we need a class to contain all generic parts stored by all SPI
 * implementations.
 */

#pragma once

#include <persistence/spi/types.h>

namespace storage {
namespace spi {

enum DocumentMetaFlags {
    NONE             = 0x0,
    REMOVE_ENTRY     = 0x1
};

class DocEntry : public document::Printable {
public:
    typedef uint32_t SizeType;
private:
    Timestamp _timestamp;
    int _metaFlags;
    SizeType _persistedDocumentSize;
    SizeType _size;
    DocumentId::UP _documentId;
    Document::UP _document;
public:
    typedef vespalib::LinkedPtr<DocEntry> LP;
    typedef std::unique_ptr<DocEntry> UP;

    DocEntry(Timestamp t, int metaFlags, Document::UP doc)
        : _timestamp(t),
          _metaFlags(metaFlags),
          _persistedDocumentSize(doc->getSerializedSize()),
          _size(_persistedDocumentSize + sizeof(DocEntry)),
          _documentId(),
          _document(std::move(doc))
    {
    }

    /**
     * Constructor that can be used by providers that already know
     * the serialized size of the document, so the potentially expensive
     * call to getSerializedSize can be avoided.
     */
    DocEntry(Timestamp t,
             int metaFlags,
             Document::UP doc,
             size_t serializedDocumentSize)
        : _timestamp(t),
          _metaFlags(metaFlags),
          _persistedDocumentSize(serializedDocumentSize),
          _size(_persistedDocumentSize + sizeof(DocEntry)),
          _documentId(),
          _document(std::move(doc))
    {
    }

    DocEntry(Timestamp t, int metaFlags, const DocumentId& docId)
        : _timestamp(t),
          _metaFlags(metaFlags),
          _persistedDocumentSize(docId.getSerializedSize()),
          _size(_persistedDocumentSize + sizeof(DocEntry)),
          _documentId(new DocumentId(docId)),
          _document()
    {
    }

    DocEntry(Timestamp t, int metaFlags)
        : _timestamp(t),
          _metaFlags(metaFlags),
          _persistedDocumentSize(0),
          _size(sizeof(DocEntry)),
          _documentId(),
          _document()
    {
    }

    DocEntry* clone() const {
        DocEntry* ret;
        if (_documentId.get() != 0) {
            ret = new DocEntry(_timestamp, _metaFlags, *_documentId);
            ret->setPersistedDocumentSize(_persistedDocumentSize);
        } else if (_document.get()) {
            ret = new DocEntry(_timestamp, _metaFlags,
                               Document::UP(new Document(*_document)),
                               _persistedDocumentSize);
        } else {
            ret = new DocEntry(_timestamp, _metaFlags);
            ret->setPersistedDocumentSize(_persistedDocumentSize);
        }
        return ret;
    }

    const Document* getDocument() const { return _document.get(); }
    const DocumentId* getDocumentId() const {
        return (_document.get() != 0 ? &_document->getId()
                                     : _documentId.get());
    }
    Document::UP releaseDocument() { return std::move(_document); }
    bool isRemove() const { return (_metaFlags & REMOVE_ENTRY); }
    Timestamp getTimestamp() const { return _timestamp; }

    int getFlags() const { return _metaFlags; }
    void setFlags(int flags) { _metaFlags = flags; }
    /**
     * @return In-memory size of this doc entry, including document instance.
     *     In essence: serialized size of document + sizeof(DocEntry).
     */
    SizeType getSize() const { return _size; }
    /**
     * If entry contains a document, returns its serialized size.
     * If entry contains a document id, returns the serialized size of
     * the id alone.
     * Otherwise (i.e. metadata only), returns zero.
     */
    SizeType getDocumentSize() const
    {
        assert(_size >= sizeof(DocEntry));
        return _size - sizeof(DocEntry);
    }
    /**
     * Return size of document as it exists in persisted form. By default
     * this will return the serialized size of the entry's document instance,
     * but for persistence providers that are able to provide this information
     * efficiently, this value can be set explicitly to provide better statistical
     * tracking for e.g. visiting operations in the service layer.
     * If explicitly set, this value shall be the size of the document _before_
     * any field filtering is performed.
     */
    SizeType getPersistedDocumentSize() const { return _persistedDocumentSize; }
    /**
     * Set persisted size of document. Optional.
     * @see getPersistedDocumentSize
     */
    void setPersistedDocumentSize(SizeType persistedDocumentSize) {
        _persistedDocumentSize = persistedDocumentSize;
    }

    void print(std::ostream& out, bool, const std::string&) const
    {
        out << "DocEntry(" << _timestamp << ", "
            << _metaFlags << ", ";
        if (_documentId.get() != 0) {
            out << *_documentId;
        } else if (_document.get()) {
            out << "Doc(" << _document->getId() << ")";
        } else {
            out << "metadata only";
        }
        out << ")";
    }

    void prettyPrint(std::ostream& out) const
    {
        std::string flags;
        if (_metaFlags == REMOVE_ENTRY) {
            flags = " (remove)";
        }

        out << "DocEntry(Timestamp: " << _timestamp
            << ", size " << getPersistedDocumentSize() << ", ";
        if (_documentId.get() != 0) {
            out << *_documentId;
        } else if (_document.get()) {
            out << "Doc(" << _document->getId() << ")";
        } else {
            out << "metadata only";
        }
        out << flags << ")";
    }

    bool operator==(const DocEntry& entry) const {
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
};

} // spi
} // storage


