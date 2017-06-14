// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/common/filespecification.h>

namespace storage {

namespace memfile {

class Environment;

class MemFileIOInterface : public Types {
public:
    virtual ~MemFileIOInterface() {}

    typedef std::unique_ptr<MemFileIOInterface> UP;

    /**
     * Deserializes the data in the given location (must already be read from disk),
     * into a document object. If the data is not already read from disk, returns NULL.
     */
    virtual Document::UP getDocumentHeader(
            const document::DocumentTypeRepo&,
            DataLocation loc) const = 0;

    virtual document::DocumentId getDocumentId(DataLocation loc) const = 0;

    /**
     * Deserializes the given document's body part with the data in the given data
     * location.
     */
    virtual void readBody(
            const document::DocumentTypeRepo&,
            DataLocation loc,
            Document& doc) const = 0;

    virtual DataLocation addDocumentIdOnlyHeader(
            const DocumentId&,
            const document::DocumentTypeRepo&) = 0;

    virtual DataLocation addHeader(const Document& doc) = 0;

    virtual DataLocation addBody(const Document& doc) = 0;

    virtual void clear(DocumentPart part) = 0;

    virtual bool verifyConsistent() const = 0;

    virtual void move(const FileSpecification& target) = 0;

    virtual DataLocation copyCache(const MemFileIOInterface& source,
                                   DocumentPart part,
                                   DataLocation loc) = 0;

    virtual void ensureCached(Environment& env,
                              DocumentPart part,
                              const std::vector<DataLocation>& locations) = 0;

    virtual bool isCached(DataLocation loc, DocumentPart part) const = 0;

    virtual bool isPersisted(DataLocation loc, DocumentPart part) const = 0;

    virtual uint32_t getSerializedSize(DocumentPart part,
                                       DataLocation loc) const = 0;

    virtual void close() = 0;

    virtual size_t getCachedSize(DocumentPart part) const = 0;

    void clear() {
        clear(HEADER);
        clear(BODY);
    }
};

}

}

