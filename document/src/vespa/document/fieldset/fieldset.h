// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace document {

class Document;
class DocumentTypeRepo;

/**
 * FieldSet class. Represents a subset of fields in a document type.
 * Note that the document id is counted as a field in this context,
 * but referenced by the special name "[id]"
 */
class FieldSet
{
public:
    enum class Type {
        FIELD,
        SET,
        ALL,
        NONE,
        DOCID,
        DOCUMENT_ONLY
    };

    using SP = std::shared_ptr<FieldSet>;
    using UP = std::unique_ptr<FieldSet>;

    virtual ~FieldSet() = default;

    /**
     * @return Return true if all the fields in "fields" are contained in
     * this field set.
     */
    virtual bool contains(const FieldSet& fields) const = 0;

    /**
     * @return Returns the type of field set this is.
     */
    virtual Type getType() const = 0;

    /**
     * Copy all fields from src into dest that are contained within the
     * given field set. If any copied field pre-exists in dest, it will
     * be overwritten.
     * NOTE: causes each field to be explicitly copied so thus not very
     * efficient. Prefer using stripFields for cases where a document
     * needs to only contain fields matching a given field set and can
     * readily be modified in-place.
     */
    static void copyFields(Document& dest, const Document& src, const FieldSet& fields);

    /**
     * Creates a copy of document src containing only the fields given by
     * the fieldset. Document type and identifier remain the same.
     * See comment for copyFields() for performance notes.
     * @return The new, (partially) copied document instance.
     */
    static std::unique_ptr<Document> createDocumentSubsetCopy(const DocumentTypeRepo& type_repo, const Document& src, const FieldSet& fields);

    /**
     * Strip all fields _except_ the ones that are contained within the
     * fieldsToKeep fieldset. Modifies original document in-place.
     */
    static void stripFields(Document& doc, const FieldSet& fieldsToKeep);
};

}

