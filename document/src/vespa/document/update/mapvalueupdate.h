// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::MapValueUpdate
 * @ingroup document
 *
 * @brief This class is intended to map a value update to a part of a collection
 *        field value.
 *
 * Note that the key must be an IntFieldValue in case of an array. For a
 * weighted set it must match the nested type of the weighted set.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace document {

class MapValueUpdate final : public ValueUpdate {
public:

    /**
     * The default constructor requires a value for this object's value and
     * update member.
     *
     * @param key The identifier of the field value to be updated.
     * @param update The update to map to apply to the field value of this.
     */
    MapValueUpdate(std::unique_ptr<FieldValue> key, std::unique_ptr<ValueUpdate> update);
    MapValueUpdate(const MapValueUpdate &) = delete;
    MapValueUpdate & operator = (const MapValueUpdate &) = delete;
    MapValueUpdate(MapValueUpdate &&) = default;
    MapValueUpdate & operator = (MapValueUpdate &&) = default;

    ~MapValueUpdate() override;

    bool operator==(const ValueUpdate& other) const override;

    const FieldValue& getKey() const { return *_key; }
    FieldValue& getKey() { return *_key; }

    const ValueUpdate& getUpdate() const { return *_update; }
    ValueUpdate& getUpdate() { return *_update; }

    /**
     * Sets the update to apply to the value update of this.
     *
     * @param update The value update.
     * @return A pointer to this.
     */
    MapValueUpdate& setUpdate(std::unique_ptr<ValueUpdate> update) {
        _update = std::move(update);
        return *this;
    }

    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;

private:
    std::unique_ptr<FieldValue> _key; // The field value this update is mapping to.
    std::unique_ptr<ValueUpdate> _update; //The update to apply to the value member of this.

    // Used by ValueUpdate's static factory function
    // Private because it generates an invalid object.
    friend class ValueUpdate;
    MapValueUpdate() : ValueUpdate(Map), _key(), _update() {}

    ACCEPT_UPDATE_VISITOR;
};

}

