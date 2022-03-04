// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::StructDataType
 * \ingroup datatype
 *
 * \brief A data type describing what can be contained in a struct field value.
 *
 * Describes what can be stored in a struct.
 */
#pragma once

#include "structureddatatype.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace document {

class StructDataType final : public StructuredDataType {
public:
    using UP = std::unique_ptr<StructDataType>;
    using SP = std::shared_ptr<StructDataType>;

    StructDataType(vespalib::stringref name);
    StructDataType(vespalib::stringref name, int32_t id);
    StructDataType(const StructDataType & rhs); // TODO avoid using this
    StructDataType & operator=(const StructDataType & rhs) = delete;
    ~StructDataType();

    /**
     * @throws vespalib::IllegalArgumentException if field conflicts with
     *                                            already existing field.
     */
    void addField(const Field& field);

    /**
     * Similar to addField(field), but does not throw exceptions on errors.
     * Fields that can be added are, and the other ones are skipped. Skipped
     * fields will logs a warning informing about the conflict.
     *
     * This is typically called from DocumentType::inherit() to add the fields
     * that does not conflict with existing fields.
     */
    void addInheritedField(const Field& field);

    // Implementation of StructuredDataType
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    uint32_t getFieldCount() const noexcept override { return _idFieldMap.size(); }

    const Field& getField(vespalib::stringref name) const override;

    /**
     * Retrieves a field based on its ID. To determine which ID to use, we also
     * need the document serialization version.
     */
    const Field& getField(int32_t fieldId) const override;

    bool hasField(vespalib::stringref name) const noexcept override;
    bool hasField(int32_t fieldId) const noexcept override;
    bool hasField(const Field& f) const noexcept {
        return hasField(f.getId());
    }

    Field::Set getFieldSet() const override;
private:
    using StringFieldMap = vespalib::hash_map<vespalib::string, Field::SP>;
    using IntFieldMap = vespalib::hash_map<int32_t, Field::SP>;
    StringFieldMap    _nameFieldMap;
    IntFieldMap       _idFieldMap;

    /** @return "" if not conflicting. Error message otherwise. */
    vespalib::string containsConflictingField(const Field& field) const;
};

}


