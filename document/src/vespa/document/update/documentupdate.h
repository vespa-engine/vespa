// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldpathupdate.h"
#include "fieldupdate.h"

#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace document {

class ByteBuffer;
class Document;
class VespaDocumentSerializer;

/**
 * Holds the set of update operations to apply to a single document, identified
 * by its document id.
 *
 * The update is bound to a document type, which defines which fields may be
 * updated, and it may only be applied to a document of that type. The
 * operations are kept as a list of FieldUpdate (a set of ValueUpdate for one
 * field) and a list of FieldPathUpdate. If createIfNonExistent is set, an
 * empty document is created and updated when the target document is missing.
 */
class DocumentUpdate {
public:
    using UP = std::unique_ptr<DocumentUpdate>;
    using SP = std::shared_ptr<DocumentUpdate>;
    using FieldUpdateV = std::vector<FieldUpdate>;
    using FieldPathUpdateV = std::vector<std::unique_ptr<FieldPathUpdate>>;
    using XmlOutputStream = vespalib::xml::XmlOutputStream;

    /**
     * Create new style document update, possibly with field path updates.
     */
    static DocumentUpdate::UP createHEAD(const DocumentTypeRepo& repo, vespalib::nbostream& stream);
    static DocumentUpdate::UP createHEAD(const DocumentTypeRepo& repo, vespalib::nbostream&& stream);

    DocumentUpdate();
    /**
     * The document type is not strictly needed, as we know this at applyTo()
     * time, but search does not use applyTo() code to do the update, and don't
     * know the document type of their objects, so this is supplied for
     * convenience. It also makes it possible to check updates for sanity at
     * creation time.
     *
     * @param type The document type that this update is applicable for.
     * @param id The identifier of the document that this update is created for.
     */
    DocumentUpdate(const DocumentTypeRepo& repo, const DataType& type, const DocumentId& id);

    DocumentUpdate(const DocumentUpdate&) = delete;
    DocumentUpdate& operator=(const DocumentUpdate&) = delete;
    DocumentUpdate(DocumentUpdate&&) = delete;
    DocumentUpdate& operator=(DocumentUpdate&&) = delete;
    ~DocumentUpdate();

    bool operator==(const DocumentUpdate&) const;
    bool operator!=(const DocumentUpdate& rhs) const { return !(*this == rhs); }

    const DocumentId& getId() const { return _documentId; }

    /**
     * Applies this update object to the given {@link Document} object.
     *
     * @param doc The document to apply this update to. Must be of the same
     * type as this.
     */
    void applyTo(Document& doc) const;

    DocumentUpdate& addUpdate(FieldUpdate&& update);
    DocumentUpdate& addFieldPathUpdate(std::unique_ptr<FieldPathUpdate> update);

    /** @return The list of updates. */
    const FieldUpdateV& getUpdates() const;

    /** @return The list of fieldpath updates. */
    const FieldPathUpdateV& getFieldPathUpdates() const;

    void eagerDeserialize() const;

    /** @return The type of document this update is for. */
    const DocumentType& getType() const noexcept;

    // Returns pointer to repo used for underlying update, or nullptr if default-constructed
    const DocumentTypeRepo* getRepoPtr() const noexcept { return _repo; }

    void serializeHEAD(vespalib::nbostream& stream) const;

    /**
     * Sets whether this update should create the document it updates if that document does not exist.
     * In this case an empty document is created before the update is applied.
     */
    void setCreateIfNonExistent(bool value);

    /**
     * Gets whether this update should create the document it updates if that document does not exist.
     */
    bool getCreateIfNonExistent() const;

    int serializeFlags(int size_) const;

    // Only used for debugging
    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    void printXml(XmlOutputStream&) const;
    std::string toXml(const std::string& indent) const;

private:
    DocumentId              _documentId; // The ID of the document to update.
    const DataType*         _type;       // The type of document this update is for.
    const DocumentTypeRepo* _repo;
    vespalib::nbostream     _backing;
    FieldUpdateV            _updates; // The list of field updates.
    FieldPathUpdateV        _fieldPathUpdates;
    bool                    _createIfNonExistent;
    bool                    _needHardReserialize;

    int deserializeFlags(int sizeAndFlags);
    void initHEAD(const DocumentTypeRepo& repo, vespalib::nbostream& stream);
    void initHEAD(const DocumentTypeRepo& repo, vespalib::nbostream&& stream);
    void deserializeBody(const DocumentTypeRepo& repo, vespalib::nbostream& stream);
    void lazyDeserialize(const DocumentTypeRepo& repo, vespalib::nbostream& stream);
    void ensureDeserialized() const;
    void serializeHeader();
    void reserialize();
    friend VespaDocumentSerializer;
};

std::ostream& operator<<(std::ostream& out, const DocumentUpdate& update);

} // namespace document
