// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::AssignValueUpdate
 * @ingroup document
 *
 * @brief Represents an update that specifies an assignment of a value to a
 *        field, completely overwriting the previous value.
 *
 * Note that for multi-value types, the entire collection is overwritten with a
 * new collection.
 */
#pragma once

#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/update/valueupdate.h>

namespace document {

class AssignValueUpdate : public ValueUpdate {
    FieldValue::CP _value;

    ACCEPT_UPDATE_VISITOR;
public:
    typedef std::unique_ptr<AssignValueUpdate> UP;

    AssignValueUpdate() : ValueUpdate(), _value() {}

    AssignValueUpdate(const FieldValue& value)
        : ValueUpdate(),
          _value(value.clone())
    {
    }

    virtual bool operator==(const ValueUpdate& other) const;

    /** @return The field value to assign during this update. */
    bool hasValue() const { return bool(_value); }
    const FieldValue& getValue() const { return *_value; }
    const FieldValue* getValuePtr() const { return _value.get(); }

    /**
     * Sets the field value to assign during this update.
     * @return A reference to this.
     */
    AssignValueUpdate& setValue(const FieldValue* value) {
        _value.reset(value ? value->clone() : 0);
        return *this;
    }

    // ValueUpdate implementation.
    virtual void checkCompatibility(const Field& field) const;
    virtual bool applyTo(FieldValue& value) const;
    virtual void printXml(XmlOutputStream& xos) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual void deserialize(const DocumentTypeRepo& repo,
                             const DataType& type,
                             ByteBuffer& buffer, uint16_t version);
    virtual AssignValueUpdate* clone() const
        { return new AssignValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(AssignValueUpdate);

};

} // document

