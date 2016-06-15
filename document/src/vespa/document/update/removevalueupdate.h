// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This class represents an update that removes a given field value from a
 * field.
 */
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/update/valueupdate.h>

namespace document {

class RemoveValueUpdate : public ValueUpdate {
    FieldValue::CP _key; // The field value to remove by this update.

    RemoveValueUpdate() : ValueUpdate(), _key() {}
    ACCEPT_UPDATE_VISITOR;

public:
    typedef std::unique_ptr<RemoveValueUpdate> UP;

    /**
     * The default constructor requires initial values for all member variables.
     *
     * @param value The identifier of the field value to update.
     */
    RemoveValueUpdate(const FieldValue& key)
        : ValueUpdate(),
          _key(key.clone()) {}

    virtual bool operator==(const ValueUpdate& other) const;

    /**
     * @return The key, whose value to remove during this update. This will be
     *         an IntFieldValue for arrays.
     */
    const FieldValue& getKey() const { return *_key; }

    /**
     * Sets the field value to remove during this update.
     *
     * @param The new field value.
     * @return A pointer to this.
     */
    RemoveValueUpdate& setKey(const FieldValue& key) {
        _key.reset(key.clone());
        return *this;
    }

    // ValueUpdate implementation
    virtual void checkCompatibility(const Field& field) const;
    virtual bool applyTo(FieldValue& value) const;
    virtual void printXml(XmlOutputStream& xos) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual void deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t version);
    virtual RemoveValueUpdate* clone() const
        { return new RemoveValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(RemoveValueUpdate);

};

} // document

