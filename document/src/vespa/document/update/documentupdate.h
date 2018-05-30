// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::DocumentUpdate
 * @ingroup document
 *
 * @brief Holds a set of operation that may be used to update a document.
 *
 * Stores update values for fields defined in the common
 * VESPA field repository. The "key" for a document is the document id, a
 * string that must conform to the vespa URI schemes.
 *
 * The following update operations are supported: assign, append and remove.
 * Append and remove are only supported for multivalued fields (arrays and
 * weightedsets).
 *
 * Each document update has a document type, which defines what documents this
 * update may be applied to and what fields may be updated in that document
 * update.  The document type for the update is given as a pointer in the
 * document update object's constructor.<br>
 * A DocumentUpdate has a vector of DocumentUpdate::Update objects
 *
 * @see DocumentId
 * @see IdString
 * @see documentmanager.h
 */
#pragma once

#include "fieldupdate.h"
#include "fieldpathupdate.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/field.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/util/bytebuffer.h>

namespace document {

class Document;

/**
 * Class containing a document update.  In vespa 5.0, support for field
 * path updates was added, and a new serialization format was
 * introduced while keeping the old one.
 */
class DocumentUpdate final : public Printable, public XmlSerializable
{
public:
    typedef std::unique_ptr<DocumentUpdate> UP;
    typedef std::shared_ptr<DocumentUpdate> SP;
    typedef std::vector<FieldUpdate> FieldUpdateV;
    typedef std::vector<FieldPathUpdate::CP> FieldPathUpdateV;
    /**
     * Enum class containing the legal serialization version for
     * document updates. This version is not encoded in the serialized
     * document update.
     */
    enum class SerializeVersion {
        SERIALIZE_42,  // old style format, before vespa 5.0
        SERIALIZE_HEAD // new style format, since vespa 5.0
    };

    /**
     * Create old style document update, no support for field path updates.
     */
    static DocumentUpdate::UP create42(const DocumentTypeRepo&, ByteBuffer&);

    /**
     * Create new style document update, possibly with field path updates.
     */
    static DocumentUpdate::UP createHEAD(const DocumentTypeRepo&, ByteBuffer&);
	
    /**
     * The document type is not strictly needed, as we know this at applyTo()
     * time, but search does not use applyTo() code to do the update, and don't
     * know the document type of their objects, so this is supplied for
     * convinience. It also makes it possible to check updates for sanity at
     * creation time.
     *
     * @param type The document type that this update is applicable for.
     * @param id The identifier of the document that this update is created for.
     */
    DocumentUpdate(const DataType &type, const DocumentId& id);

    /**
     * Create a document update from a byte buffer containing a serialized
     * document update.
     *
     * @param repo Document type repo used to find proper document type
     * @param buffer The buffer containing the serialized document update
     * @param serializeVersion Selector between serialization formats.
     */
    DocumentUpdate(const DocumentTypeRepo &repo, ByteBuffer &buffer, SerializeVersion serializeVersion);

    DocumentUpdate(const DocumentUpdate &) = delete;
    DocumentUpdate & operator = (const DocumentUpdate &) = delete;
    ~DocumentUpdate() override;

    bool operator==(const DocumentUpdate&) const;
    bool operator!=(const DocumentUpdate & rhs) const { return ! (*this == rhs); }
	
    const DocumentId& getId() const { return _documentId; }

    /**
     * Applies this update object to the given {@link Document} object.
     *
     * @param doc The document to apply this update to. Must be of the same
     * type as this.
     */
    void applyTo(Document& doc) const;
	
    /**
     * Add a field update to this document update.
     * @return A reference to this.
     */
    DocumentUpdate& addUpdate(const FieldUpdate& update);

    /**
     * Add a fieldpath update to this document update.
     * @return A reference to this.
     */
    DocumentUpdate& addFieldPathUpdate(const FieldPathUpdate::CP& update);

    /** @return The list of updates. */
    const FieldUpdateV & getUpdates() const { return _updates; }

    /** @return The list of fieldpath updates. */
    const FieldPathUpdateV & getFieldPathUpdates() const { return _fieldPathUpdates; }

    /** @return The type of document this update is for. */
    const DocumentType& getType() const;
	
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void deserialize42(const DocumentTypeRepo&, ByteBuffer&);
    void deserializeHEAD(const DocumentTypeRepo&, ByteBuffer&);

    void serializeHEAD(vespalib::nbostream &stream) const;

    void printXml(XmlOutputStream&) const override;

    /**
     * Sets whether this update should create the document it updates if that document does not exist.
     * In this case an empty document is created before the update is applied.
     */
    void setCreateIfNonExistent(bool value) {
        _createIfNonExistent = value;
    }

    /**
     * Gets whether this update should create the document it updates if that document does not exist.
     */
    bool getCreateIfNonExistent() const {
        return _createIfNonExistent;
    }

    int serializeFlags(int size_) const;
    int16_t getVersion() const { return _version; }

private:
    DocumentId       _documentId; // The ID of the document to update.
    const DataType  *_type; // The type of document this update is for.
    FieldUpdateV     _updates; // The list of field updates.
    FieldPathUpdateV _fieldPathUpdates;
    int16_t          _version; // Serialization version
    bool             _createIfNonExistent;

    /**
     * This function exist because search relies on deserialization through
     * creating object through empty constructor and calling deserialize.
     *
     * It is hidden to prevent accidental other usage.
     */
    DocumentUpdate();

    int deserializeFlags(int sizeAndFlags);
};

} // document

