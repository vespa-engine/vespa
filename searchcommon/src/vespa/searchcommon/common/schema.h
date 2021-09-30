// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datatype.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/ptrholder.h>
#include <vector>

namespace vespalib { class asciistream; }
namespace search::index {

/**
 * Schema class used to give a high-level description of the content
 * of an index.
 **/
class Schema
{
public:
    using UP = std::unique_ptr<Schema>;
    using SP = std::shared_ptr<Schema>;
    using PH = vespalib::PtrHolder<Schema>;

    using DataType = schema::DataType;
    using CollectionType = schema::CollectionType;

    /**
     * A single field has a name, data type and collection
     * type. Various aspects (index/attribute/summary) may have
     * limitations on what types are supported in the back-end.
     **/
    class Field
    {
        vespalib::string  _name;
        DataType          _dataType;
        CollectionType    _collectionType;
        vespalib::string  _tensor_spec;

    public:
        Field(vespalib::stringref n, DataType dt) noexcept;
        Field(vespalib::stringref n, DataType dt, CollectionType ct) noexcept;
        Field(vespalib::stringref n, DataType dt, CollectionType ct, vespalib::stringref tensor_spec) noexcept;

        /**
         * Create this field based on the given config lines.
         **/
        Field(const std::vector<vespalib::string> & lines);
        Field(const Field &) noexcept;
        Field & operator = (const Field &) noexcept;
        Field(Field &&) noexcept;
        Field & operator = (Field &&) noexcept;

        virtual ~Field();

        virtual void
        write(vespalib::asciistream & os,
              vespalib::stringref prefix) const;

        const vespalib::string &getName() const { return _name; }
        DataType getDataType() const { return _dataType; }
        CollectionType getCollectionType() const { return _collectionType; }
        const vespalib::string& get_tensor_spec() const { return _tensor_spec; }

        bool matchingTypes(const Field &rhs) const {
            return getDataType() == rhs.getDataType() &&
             getCollectionType() == rhs.getCollectionType();
        }

        bool operator==(const Field &rhs) const;
        bool operator!=(const Field &rhs) const;
    };


    /**
     * A representation of an index field with extra information on
     * how the index should be generated.
     **/
    class IndexField : public Field {
    private:
        uint32_t _avgElemLen;
        // TODO: Remove when posting list format with interleaved features is made default
        bool _interleaved_features;

    public:
        IndexField(vespalib::stringref name, DataType dt) noexcept;
        IndexField(vespalib::stringref name, DataType dt, CollectionType ct) noexcept;
        IndexField(const IndexField &) noexcept;
        IndexField & operator = (const IndexField &) noexcept;
        IndexField(IndexField &&) noexcept;
        IndexField & operator = (IndexField &&) noexcept;
        /**
         * Create this index field based on the given config lines.
         **/
        IndexField(const std::vector<vespalib::string> &lines);

        IndexField &setAvgElemLen(uint32_t avgElemLen) { _avgElemLen = avgElemLen; return *this; }
        IndexField &set_interleaved_features(bool value) {
            _interleaved_features = value;
            return *this;
        }

        void write(vespalib::asciistream &os,
                   vespalib::stringref prefix) const override;

        uint32_t getAvgElemLen() const { return _avgElemLen; }
        bool use_interleaved_features() const { return _interleaved_features; }

        bool operator==(const IndexField &rhs) const;
        bool operator!=(const IndexField &rhs) const;
    };

    using AttributeField = Field;
    using SummaryField = Field;
    using ImportedAttributeField = Field;

    /**
     * A field collection has a name and a list of index field names,
     * and is a named physical view over the list of index fields.
     **/
    class FieldSet
    {
        vespalib::string _name;
        std::vector<vespalib::string> _fields;

    public:
        FieldSet(vespalib::stringref n) : _name(n), _fields() {}
        FieldSet(const FieldSet &);
        FieldSet & operator =(const FieldSet &);
        FieldSet(FieldSet &&) noexcept = default;
        FieldSet & operator =(FieldSet &&) noexcept = default;

        /**
         * Create this field collection based on the given config lines.
         **/
        FieldSet(const std::vector<vespalib::string> & lines);

        ~FieldSet();

        FieldSet &addField(vespalib::stringref fieldName) {
            _fields.push_back(fieldName);
            return *this;
        }

        const vespalib::string &getName() const { return _name; }
        const std::vector<vespalib::string> &getFields() const
        { return _fields; }

        bool operator==(const FieldSet &rhs) const;
        bool operator!=(const FieldSet &rhs) const;
    };

    static const uint32_t UNKNOWN_FIELD_ID;

private:
    std::vector<IndexField>      _indexFields;
    std::vector<AttributeField>  _attributeFields;
    std::vector<SummaryField>    _summaryFields;
    std::vector<FieldSet> _fieldSets;
    std::vector<ImportedAttributeField> _importedAttributeFields;
    using Name2IdMap = vespalib::hash_map<vespalib::string, uint32_t>;
    Name2IdMap _indexIds;
    Name2IdMap _attributeIds;
    Name2IdMap _summaryIds;
    Name2IdMap _fieldSetIds;
    Name2IdMap _importedAttributeIds;

    void writeToStream(vespalib::asciistream &os, bool saveToDisk) const;

public:
    /**
     * Create an initially empty schema
     **/
    Schema();
    Schema(const Schema & rhs);
    Schema & operator=(const Schema & rhs);
    Schema(Schema && rhs);
    Schema & operator=(Schema && rhs);
    ~Schema();

    /**
     * Load this schema from the file with the given name.
     *
     * @param fileName the name of the file.
     * @return true if the schema could be loaded.
     **/
    bool
    loadFromFile(const vespalib::string & fileName);

    /**
     * Save this schema to the file with the given name.
     *
     * @param fileName the name of the file.
     * @return true if the schema could be saved.
     **/
    bool
    saveToFile(const vespalib::string & fileName) const;

    vespalib::string toString() const;

    /**
     * Add an index field to this schema
     *
     * @param field the field to add
     **/
    Schema &
    addIndexField(const IndexField &field);

    // Only used by tests.
    Schema &
    addUriIndexFields(const IndexField &field);

    /**
     * Add an attribute field to this schema
     *
     * @param field the field to add
     **/
    Schema &
    addAttributeField(const AttributeField &field);

    /**
     * Add a summary field to this schema
     *
     * @param field the field to add
     **/
    Schema &
    addSummaryField(const SummaryField &field);

    /**
     * Add a field set to this schema.
     *
     * @param collection the field set to add.
     **/
    Schema &
    addFieldSet(const FieldSet &collection);

    Schema &addImportedAttributeField(const ImportedAttributeField &field);

    /**
     * Obtain the number of index fields in this schema.
     *
     * @return number of fields
     **/
    uint32_t getNumIndexFields() const { return _indexFields.size(); }

    /**
     * Obtain the number of attribute fields in this schema.
     *
     * @return number of fields
     **/
    uint32_t getNumAttributeFields() const { return _attributeFields.size(); }

    /**
     * Obtain the number of summary fields in this schema.
     *
     * @return number of fields
     **/
    uint32_t getNumSummaryFields() const { return _summaryFields.size(); }

    /**
     * Obtain the number of field sets in this schema.
     *
     * @return number of field sets.
     **/
    uint32_t getNumFieldSets() const { return _fieldSets.size(); }

    size_t getNumImportedAttributeFields() const { return _importedAttributeFields.size(); }

    /**
     * Get information about a specific index field using the given fieldId.
     *
     * @return the field
     * @param idx an index in the range [0, size - 1].
     **/
    const IndexField &
    getIndexField(uint32_t fieldId) const
    {
        return _indexFields[fieldId];
    }

    /**
     * Returns const view of the index fields.
     */
    const std::vector<IndexField> &getIndexFields() const {
        return _indexFields;
    }

    /**
     * Get the field id for the index field with the given name.
     *
     * @return the field id or UNKNOWN_FIELD_ID if not found.
     * @param name the name of the field.
     **/
    uint32_t getIndexFieldId(vespalib::stringref name) const;

    /**
     * Check if a field is an index
     *
     * @return true if field is an index field.
     * @param name the name of the field.
     **/
    bool isIndexField(vespalib::stringref name) const;

    /**
     * Check if a field is a summary field
     *
     * @return true if field is an summary field.
     * @param name the name of the field.
     **/
    bool isSummaryField(vespalib::stringref name) const;

    /**
     * Check if a field is a attribute field
     *
     * @return true if field is an attribute field.
     * @param name the name of the field.
     **/
    bool isAttributeField(vespalib::stringref name) const;

    /**
     * Get information about a specific attribute field using the given fieldId.
     *
     * @return the field
     * @param idx an index in the range [0, size - 1].
     **/
    const AttributeField &
    getAttributeField(uint32_t fieldId) const
    {
        return _attributeFields[fieldId];
    }

    /**
     * Returns const view of the attribute fields.
     */
    const std::vector<AttributeField> &getAttributeFields() const {
        return _attributeFields;
    }

    /**
     * Get the field id for the attribute field with the given name.
     *
     * @return the field id or UNKNOWN_FIELD_ID if not found.
     * @param name the name of the field.
     **/
    uint32_t getAttributeFieldId(vespalib::stringref name) const;

    /**
     * Get information about a specific summary field using the given fieldId.
     *
     * @return the field
     * @param idx an index in the range [0, size - 1]
     **/
    const SummaryField &
    getSummaryField(uint32_t fieldId) const
    {
        return _summaryFields[fieldId];
    }

    /**
     * Returns const view of the summary fields.
     */
    const std::vector<SummaryField> &getSummaryFields() const {
        return _summaryFields;
    }

    /**
     * Get the field id for the summary field with the given name.
     *
     * @return the field id or UNKNOWN_FIELD_ID if not found.
     * @param name the name of the field.
     **/
    uint32_t getSummaryFieldId(vespalib::stringref name) const;

    /**
     * Get information about a specific field set
     *
     * @return the field set.
     * @param idx an index in the range [0, size - 1].
     **/
    const FieldSet &
    getFieldSet(uint32_t idx) const
    {
        return _fieldSets[idx];
    }

    /**
     * Get the field id for the field set with the given name.
     *
     * @return the field id or UNKNOWN_FIELD_ID if not found.
     * @param name the name of the field set.
     **/
    uint32_t
    getFieldSetId(vespalib::stringref name) const;

    const std::vector<ImportedAttributeField> &getImportedAttributeFields() const {
        return _importedAttributeFields;
    }

    void swap(Schema &rhs);
    void clear();

    static Schema::UP intersect(const Schema &lhs, const Schema &rhs);
    static Schema::UP make_union(const Schema &lhs, const Schema &rhs);
    static Schema::UP set_difference(const Schema &lhs, const Schema &rhs);

    bool operator==(const Schema &rhs) const;
    bool operator!=(const Schema &rhs) const;

    bool empty() const;
};

}
