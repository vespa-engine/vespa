// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

class MapValueUpdate : public ValueUpdate {
    FieldValue::CP _key; // The field value this update is mapping to.
                         // This is shared pointer to be able to lookup key
                         // in weighted set map.
    ValueUpdate::CP _update; //The update to apply to the value member of this.

    // Used by ValueUpdate's static factory function
    // Private because it generates an invalid object.
    friend class ValueUpdate;
    MapValueUpdate() : ValueUpdate(), _key(), _update() {}

    ACCEPT_UPDATE_VISITOR;
public:

    /**
     * The default constructor requires a value for this object's value and
     * update member.
     *
     * @param key The identifier of the field value to be updated.
     * @param update The update to map to apply to the field value of this.
     */
    MapValueUpdate(const FieldValue& key, const ValueUpdate& update);
    MapValueUpdate(const MapValueUpdate &);
    MapValueUpdate & operator = (const MapValueUpdate &);
    MapValueUpdate(MapValueUpdate &&) = default;
    MapValueUpdate & operator = (MapValueUpdate &&) = default;

    ~MapValueUpdate();

    bool operator==(const ValueUpdate& other) const override;

    /** @return The key of the field value to update. */
    const FieldValue& getKey() const { return *_key; }
    FieldValue& getKey() { return *_key; }

    /** @return The update to apply to the field value of this. */
    const ValueUpdate& getUpdate() const { return *_update; }
    ValueUpdate& getUpdate() { return *_update; }

    /**
     * Sets the identifier of the field value to update.
     *
     * @param key The field value identifier.
     * @return A pointer to this.
     */
    MapValueUpdate& setKey(const FieldValue& key) {
        _key.reset(key.clone());
        return *this;
    }

    /**
     * Sets the update to apply to the value update of this.
     *
     * @param update The value update.
     * @return A pointer to this.
     */
    MapValueUpdate& setUpdate(const ValueUpdate& update) {
        _update.reset(update.clone());
        return *this;
    }

    // ValueUpdate implementation
    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream& buffer) override;
    MapValueUpdate* clone() const override { return new MapValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(MapValueUpdate);

};

} // document

