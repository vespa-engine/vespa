// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/document/util/printable.h>

namespace vespalib { class nbostream; }

namespace document {

class DocumentType;

class DocumentId : public Printable
{
public:
    typedef std::unique_ptr<DocumentId> UP;

    DocumentId();
    DocumentId(vespalib::nbostream & os);
    explicit DocumentId(const IdString& id);
    /**
     * Parse the given document identifier given as string, and create an
     * identifier object from it.
     *
     * @throws IdParseException If the identifier given is invalid.
     */
    explicit DocumentId(vespalib::stringref id);

    void set(vespalib::stringref id);

    /**
       Hides the printable toString() for effiency reasons.
    */
    vespalib::string toString() const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    bool operator==(const DocumentId& other) const { return *_id == *other._id; }
    bool operator!=(const DocumentId& other) const { return ! (*_id == *other._id); }

    const IdString& getScheme() const { return *_id; }
    bool hasDocType() const { return _id->hasDocType(); }
    vespalib::string getDocType() const { return _id->getDocType(); }

    const GlobalId& getGlobalId() const {
        if (!_globalId.first) { calculateGlobalId(); }
        return _globalId.second;
    }

    DocumentId* clone() const { return new DocumentId(*this); }
    virtual size_t getSerializedSize() const;
    void swap(DocumentId & rhs);
private:
    mutable std::pair<bool, GlobalId> _globalId;
    vespalib::CloneablePtr<IdString> _id;

    void calculateGlobalId() const;
};

} // document

