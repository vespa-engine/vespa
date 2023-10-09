// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DocumentId
 * \ingroup base
 *
 * \brief Class describing a document identifier.
 *
 * The document identifier is an URI set by the user. The URI must conform
 * to one of the accepted document identifier schemes.
 *
 * The IdString class represent such a scheme. See the various implementations
 * of IdString to see legal schemes.
 *
 * This class contains the identifier parsed into pieces. Thus, get() functions
 * are cheap to call.
 *
 * It's up to the users to ensure that they use unique document identifiers.
 */

#pragma once

#include "idstring.h"
#include "globalid.h"

namespace vespalib { class nbostream; }

namespace document {

class DocumentType;

class DocumentId
{
public:
    using UP = std::unique_ptr<DocumentId>;

    DocumentId();
    DocumentId(vespalib::nbostream & os);
    DocumentId(DocumentId && rhs) noexcept = default;
    DocumentId & operator = (DocumentId && rhs) noexcept = default;
    DocumentId(const DocumentId & rhs);
    DocumentId & operator = (const DocumentId & rhs);
    ~DocumentId() noexcept ;
    /**
     * Parse the given document identifier given as string, and create an
     * identifier object from it.
     *
     * Precondition: `id` MUST be null-terminated.
     *
     * @throws IdParseException If the identifier given is invalid.
     */
    explicit DocumentId(vespalib::stringref id);

    /**
     * Precondition: `id` MUST be null-terminated.
     */
    void set(vespalib::stringref id);

    /**
       Hides the printable toString() for effiency reasons.
    */
    vespalib::string toString() const;

    bool operator==(const DocumentId& other) const { return _id == other._id; }
    bool operator!=(const DocumentId& other) const { return ! (_id == other._id); }

    const IdString& getScheme() const { return _id; }
    bool hasDocType() const { return _id.hasDocType(); }
    vespalib::stringref getDocType() const { return _id.getDocType(); }

    const GlobalId& getGlobalId() const {
        if (!_globalId.first) { calculateGlobalId(); }
        return _globalId.second;
    }

    size_t getSerializedSize() const;
private:
    mutable std::pair<bool, GlobalId> _globalId;
    IdString _id;

    void calculateGlobalId() const;
};

std::ostream & operator << (std::ostream & os, const DocumentId & id);

} // document

