// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>

namespace search::index {

class SchemaUtil {
public:

    class IndexSettings {
        schema::DataType _dataType;
        bool _error;        // Schema is bad.

    public:
        const schema::DataType & getDataType() const {
            return _dataType;
        }

        bool hasError() const { return _error; }

        IndexSettings()
            : _dataType(schema::DataType::STRING),
              _error(false)
        { }

        IndexSettings(const IndexSettings &rhs)
            : _dataType(rhs._dataType),
              _error(rhs._error)
        { }

        IndexSettings(schema::DataType dataType,
                      bool error)
            : _dataType(dataType),
              _error(error)
        { }

        IndexSettings & operator=(const IndexSettings &rhs) {
            IndexSettings tmp(rhs);
            swap(tmp);
            return *this;
        }

        void swap(IndexSettings &rhs) {
            std::swap(_dataType, rhs._dataType);
            std::swap(_error, rhs._error);
        }
    };

    class IndexIterator {
        const Schema &_schema;
        uint32_t _index;

    public:
        IndexIterator(const Schema &schema)
            : _schema(schema),
              _index(0u)
        { }

        IndexIterator(const Schema &schema, uint32_t index)
            : _schema(schema),
              _index(index)
        { }

        IndexIterator(const Schema &schema, const IndexIterator &rhs)
            : _schema(schema),
              _index(Schema::UNKNOWN_FIELD_ID)
        {
            const vespalib::string &name = rhs.getName();
            _index = schema.getIndexFieldId(name);
        }

        const Schema & getSchema() const {
            return _schema;
        }

        uint32_t getIndex() const {
            return _index;
        }

        const vespalib::string &getName() const {
            return _schema.getIndexField(_index).getName();
        }

        bool use_interleaved_features() const {
            return _schema.getIndexField(_index).use_interleaved_features();
        }

        IndexIterator &operator++() {
            if (_index < _schema.getNumIndexFields()) {
                ++_index;
            }
            return *this;
        }

        bool isValid() const {
            return _index < _schema.getNumIndexFields();
        }

        IndexSettings getIndexSettings() const {
            return SchemaUtil::getIndexSettings(_schema, _index);
        }

        /**
         * Return if old schema has at least one usable input field
         * with matching data type.
         *
         * @param oldSchema old schema, present in an input index
         */
        bool hasOldFields(const Schema &oldSchema) const;

        /**
         * Return if fields in old schema matches fields in new
         * schema, allowing for slightly faster fusion operations.
         * Field collections must have same set of fields which must
         * also match between new and old schema.
         *
         * @param oldSchema old schema, present in an input index
         */
        bool hasMatchingOldFields(const Schema &oldSchema) const;

        bool has_matching_use_interleaved_features(const Schema &oldSchema) const;
    };

    static IndexSettings getIndexSettings(const Schema &schema, const uint32_t index);


    static bool validateIndexFieldType(schema::DataType dataType) {
        switch (dataType) {
        case schema::DataType::STRING:
        case schema::DataType::INT32:
            return true;
        default:
            ;
        }
        return false;
    }

    static bool validateIndexField(const Schema::IndexField &field);
    static bool addIndexField(Schema &schema, const Schema::IndexField &field);
    static bool validateSchema(const Schema &schema);
    static bool getIndexIds(const Schema &schema, schema::DataType dataType, std::vector<uint32_t> &indexes);
};

}
