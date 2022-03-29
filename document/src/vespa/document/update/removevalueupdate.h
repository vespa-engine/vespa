// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This class represents an update that removes a given field value from a
 * field.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace document {

class RemoveValueUpdate final : public ValueUpdate {
    std::unique_ptr<FieldValue> _key; // The field value to remove by this update.

    ACCEPT_UPDATE_VISITOR;
    friend ValueUpdate;
    RemoveValueUpdate() : ValueUpdate(Remove), _key() {}
public:
    /**
     * The default constructor requires initial values for all member variables.
     *
     * @param value The identifier of the field value to update.
     */
    explicit RemoveValueUpdate(std::unique_ptr<FieldValue> key);
    RemoveValueUpdate(const RemoveValueUpdate &) = delete;
    RemoveValueUpdate & operator=(const RemoveValueUpdate &) = delete;
    ~RemoveValueUpdate() override;

    bool operator==(const ValueUpdate& other) const override;

    /**
     * @return The key, whose value to remove during this update. This will be
     *         an IntFieldValue for arrays.
     */
    const FieldValue& getKey() const { return *_key; }

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;
};

}

