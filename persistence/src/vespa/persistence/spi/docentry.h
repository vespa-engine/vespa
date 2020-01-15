// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

namespace storage::spi {

enum DocumentMetaFlags {
    NONE             = 0x0,
    REMOVE_ENTRY     = 0x1
};

class DocEntry {
public:
    typedef uint32_t SizeType;
private:
    Timestamp _timestamp;
    int _metaFlags;
    SizeType _persistedDocumentSize;
    SizeType _size;
    DocumentIdUP _documentId;
    DocumentUP _document;
public:
    using UP = std::unique_ptr<DocEntry>;
    using SP = std::shared_ptr<DocEntry>;

    DocEntry(Timestamp t, int metaFlags, DocumentUP doc);

    /**
     * Constructor that can be used by providers that already know
     * the serialized size of the document, so the potentially expensive
     * call to getSerializedSize can be avoided.
     */
    DocEntry(Timestamp t, int metaFlags, DocumentUP doc, size_t serializedDocumentSize);
    DocEntry(Timestamp t, int metaFlags, const DocumentId& docId);

    DocEntry(Timestamp t, int metaFlags);
    ~DocEntry();
    DocEntry* clone() const;
    const Document* getDocument() const { return _document.get(); }
    const DocumentId* getDocumentId() const;
    DocumentUP releaseDocument();
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
    SizeType getDocumentSize() const;
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

    vespalib::string toString() const;
    bool operator==(const DocEntry& entry) const;
};

std::ostream & operator << (std::ostream & os, const DocEntry & r);

}
