// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/document/datatype/structdatatype.h>

#include <vector>
#include <map>

namespace document {

class Field;
class DocumentType;

class DocumentType : public StructuredDataType {
public:
    class FieldSet {
    public:
        typedef std::set<vespalib::string> Fields;
        FieldSet() : _name(), _fields() {}
        FieldSet(const vespalib::string & name) : _name(name), _fields() {}
        FieldSet(const vespalib::string & name, const Fields & fields) : _name(name), _fields(fields) {}
        const vespalib::string & getName() const { return _name; }
        const Fields & getFields() const { return _fields; }
        FieldSet & add(vespalib::string & field) {
            _fields.insert(field);
            return *this;
        }
    private:
        vespalib::string _name;
        Fields           _fields;
    };
    typedef std::map<vespalib::string, FieldSet> FieldSetMap;
    std::vector<const DocumentType *> _inheritedTypes;
    StructDataType::SP _ownedFields;
    const StructDataType* _fields;
    FieldSetMap _fieldSets;

public:
    typedef std::unique_ptr<DocumentType> UP;
    typedef std::shared_ptr<DocumentType> SP;

    DocumentType();
    DocumentType(const vespalib::stringref &name, int32_t id);
    DocumentType(const vespalib::stringref &name, int32_t id,
                 const StructDataType& fields);

    DocumentType(const vespalib::stringref &name);
    DocumentType(const vespalib::stringref &name,
                 const StructDataType& fields);

    ~DocumentType();

    const StructDataType& getFieldsType() const { return *_fields; }

    void addField(const Field&);

    /**
     * Add a documenttype this type inherits from. The order inherited
     * types are added decides which parent fields are used if
     * multiple parents define the same fields.
     */
    void inherit(const DocumentType &docType);

    bool isA(const DataType& other) const override;

    const std::vector<const DocumentType *> & getInheritedTypes() const { return _inheritedTypes; };

    // Implementation of StructuredDataType
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool operator==(const DataType& type) const override;
    uint32_t getFieldCount() const override {
        return _fields->getFieldCount();
    }
    const Field & getField(const vespalib::stringref & name) const override;
    const Field & getField(int fieldId) const override;
    bool hasField(const vespalib::stringref &name) const override;
    bool hasField(int fieldId) const override;
    Field::Set getFieldSet() const override;
    DocumentType* clone() const override;

    DocumentType & addFieldSet(const vespalib::string & name, const FieldSet::Fields & fields);
    const FieldSet * getFieldSet(const vespalib::string & name) const;

    DECLARE_IDENTIFIABLE(DocumentType);
};

} // document

