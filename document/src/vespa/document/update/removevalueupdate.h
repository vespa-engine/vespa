// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This class represents an update that removes a given field value from a
 * field.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

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
    RemoveValueUpdate(const FieldValue& key);
    ~RemoveValueUpdate();

    bool operator==(const ValueUpdate& other) const override;

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

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;
    RemoveValueUpdate* clone() const override { return new RemoveValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(RemoveValueUpdate);

};

}

