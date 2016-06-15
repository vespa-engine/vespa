// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::ClearValueUpdate
 * @ingroup document
 *
 * @brief Represents an update that clears the content of a field value.
 */
#pragma once

#include <vespa/document/update/valueupdate.h>

namespace document {

class ClearValueUpdate : public ValueUpdate {
    ACCEPT_UPDATE_VISITOR;
public:
    typedef std::unique_ptr<ClearValueUpdate> UP;

    ClearValueUpdate() : ValueUpdate() {}

    ClearValueUpdate(const ClearValueUpdate& update) : ValueUpdate(update) {}

    virtual bool operator==(const ValueUpdate& other) const;

    // ValueUpdate implementation
    virtual void checkCompatibility(const Field& field) const;
    virtual bool applyTo(FieldValue& value) const;
    virtual void printXml(XmlOutputStream& xos) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual void deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t version);
    virtual ClearValueUpdate* clone() const
        { return new ClearValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(ClearValueUpdate);
};

} // document

