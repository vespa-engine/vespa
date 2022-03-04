// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DocumentType
 * \ingroup datatype
 *
 * \brief A class describing what can be contained in a document of this type.
 *
 * A document type can inherit other document types. All document types inherit
 * "document" type.
 */

#pragma once

#include "structdatatype.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <map>
#include <set>

namespace document {

class Field;
class DocumentType;

class DocumentType final : public StructuredDataType {
public:
    class FieldSet {
    public:
        using Fields = std::set<vespalib::string>;
        FieldSet(const vespalib::string & name, Fields fields,
                 const DocumentType & doc_type);

        FieldSet(const FieldSet&) = default;
        FieldSet(FieldSet&&) noexcept = default;
        FieldSet& operator=(const FieldSet&) = default;
        FieldSet& operator=(FieldSet&&) noexcept = default;

        const vespalib::string & getName() const noexcept { return _name; }
        const Fields & getFields() const noexcept { return _fields; }
        const FieldCollection & asCollection() const { return _field_collection; }
    private:
        vespalib::string _name;
        Fields           _fields;
        FieldCollection  _field_collection;
    };
private:
    using FieldSetMap = std::map<vespalib::string, FieldSet>;
    using ImportedFieldNames = vespalib::hash_set<vespalib::string>;

    std::vector<const DocumentType *> _inheritedTypes;
    StructDataType::SP                _ownedFields;
    const StructDataType*             _fields;
    FieldSetMap                       _fieldSets;
    ImportedFieldNames                _imported_field_names;

public:
    using UP = std::unique_ptr<DocumentType>;
    using SP = std::shared_ptr<DocumentType>;

    DocumentType(vespalib::stringref name, int32_t id);
    DocumentType(vespalib::stringref name, int32_t id, const StructDataType& fields);

    explicit DocumentType(vespalib::stringref name);
    DocumentType(vespalib::stringref name, const StructDataType& fields);
    DocumentType(const DocumentType &);   // TODO remove usage
    DocumentType & operator = (const DocumentType &);  // TODO remove usage

    ~DocumentType() override;

    const StructDataType& getFieldsType() const noexcept { return *_fields; }

    void addField(const Field&);

    /**
     * Add a documenttype this type inherits from. The order inherited
     * types are added decides which parent fields are used if
     * multiple parents define the same fields.
     */
    void inherit(const DocumentType &docType);

    bool isA(const DataType& other) const override;
    virtual bool isDocument() const noexcept override { return true; }

    const std::vector<const DocumentType *> & getInheritedTypes() const { return _inheritedTypes; };

    // Implementation of StructuredDataType
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool equals(const DataType& type) const noexcept override;
    uint32_t getFieldCount() const noexcept override {
        return _fields->getFieldCount();
    }
    const Field & getField(vespalib::stringref name) const override;
    const Field & getField(int fieldId) const override;
    bool hasField(vespalib::stringref name) const noexcept override {
        return _fields->hasField(name);
    }
    bool hasField(int fieldId) const noexcept override {
        return _fields->hasField(fieldId);
    }
    Field::Set getFieldSet() const override;

    DocumentType & addFieldSet(const vespalib::string & name, FieldSet::Fields fields);
    const FieldSet * getFieldSet(const vespalib::string & name) const;
    const FieldSetMap & getFieldSets() const { return _fieldSets; }

    const ImportedFieldNames& imported_field_names() const noexcept {
        return _imported_field_names;
    }
    bool has_imported_field_name(const vespalib::string& name) const noexcept;
    // Ideally the type would be immutable, but this is how it's built today.
    void add_imported_field_name(const vespalib::string& name);
};

} // document

