// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::StructDataType
 * \ingroup datatype
 *
 * \brief A data type describing what can be contained in a struct field value.
 *
 * Describes what can be stored in a struct.
 */
#pragma once

#include <vespa/document/datatype/structureddatatype.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/document/util/compressionconfig.h>
#include <memory>

namespace document {

class StructDataType : public StructuredDataType {
public:
    typedef std::unique_ptr<StructDataType> UP;
    typedef std::shared_ptr<StructDataType> SP;

    StructDataType();
    StructDataType(const vespalib::stringref &name);
    StructDataType(const vespalib::stringref &name, int32_t id);
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
    virtual std::unique_ptr<FieldValue> createFieldValue() const;
    virtual void print(std::ostream&, bool verbose, const std::string& indent) const;
    virtual uint32_t getFieldCount() const { return _idFieldMap.size(); }

    virtual const Field& getField(const vespalib::stringref & name) const;

    /**
     * Retrieves a field based on its ID. To determine which ID to use, we also
     * need the document serialization version.
     */
    virtual const Field& getField(int32_t fieldId, int version) const;

    virtual bool hasField(const vespalib::stringref &name) const;
    virtual bool hasField(int32_t fieldId, int version) const;
    bool hasField(const Field& f) const {
        return hasField(f.getId(7), 7) || hasField(f.getId(6), 6);
    }

    virtual Field::Set getFieldSet() const;
    virtual StructDataType* clone() const;

    void setCompressionConfig(const CompressionConfig& cfg) { _compressionConfig = cfg; };
    const CompressionConfig& getCompressionConfig() const { return _compressionConfig; }

    DECLARE_IDENTIFIABLE(StructDataType);

private:
    typedef vespalib::hash_map<vespalib::string, Field::SP> StringFieldMap;
    typedef vespalib::hash_map<int32_t, Field::SP> IntFieldMap;
    StringFieldMap _nameFieldMap;
    IntFieldMap _idFieldMap;
    IntFieldMap _idFieldMapV6;

    CompressionConfig _compressionConfig;

    /** @return "" if not conflicting. Error message otherwise. */
    vespalib::string containsConflictingField(const Field& field) const;
    const Field& getFieldV6(int32_t fieldId) const __attribute__((noinline));
};

}


