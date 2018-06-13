// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::AddValueUpdate
 * @ingroup document
 *
 * @brief Represents an update that specifies an addition to a field value.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace document {

class AddValueUpdate : public ValueUpdate {
    FieldValue::CP _value; // The field value to add by this update.
    int _weight; // The weight to assign to the contained value.

    // Used by ValueUpdate's static factory function
    // Private because it generates an invalid object.
    friend class ValueUpdate;
    AddValueUpdate() : ValueUpdate(), _value(0), _weight(1) {}
    ACCEPT_UPDATE_VISITOR;
public:
    typedef std::unique_ptr<AddValueUpdate> UP;

    /**
     * The default constructor requires initial values for all member variables.
     *
     * @param value The field value to add.
     * @param weight The weight for the field value.
     */
    AddValueUpdate(const FieldValue& value, int weight = 1);
    ~AddValueUpdate();

    bool operator==(const ValueUpdate& other) const override;

    /** @return the field value to add during this update. */
    const FieldValue& getValue() const { return *_value; }

    /** @return The weight to assign to the value of this. */
    int getWeight() const { return _weight; }

    /**
     * Sets the field value to add during this update.
     *
     * @param value The new field value.
     * @return A reference to this object so you can chain calls.
     */
    AddValueUpdate& setValue(const FieldValue& value) {
        _value.reset(value.clone());
        return *this;
    }

    /**
     * Sets the weight to assign to the value of this.
     *
     * @return A reference to this object so you can chain calls.
     */
    AddValueUpdate& setWeight(int weight) {
        _weight = weight;
        return *this;
    }

    // ValueUpdate implementation
    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & buffer) override;
    AddValueUpdate* clone() const override { return new AddValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(AddValueUpdate);

};

} // document
