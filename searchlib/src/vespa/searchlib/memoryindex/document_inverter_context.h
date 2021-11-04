// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/schema_index_fields.h>
#include <memory>
#include <vector>

namespace document {
class DataType;
class Document;
class DocumentType;
class Field;
class FieldValue;
}

namespace vespalib { class ISequencedTaskExecutor; }

namespace search::memoryindex {

class IFieldIndexCollection;

/*
 * Class containing shared context for document inverters that changes
 * rarely (type dependent data, wiring).
 */
class DocumentInverterContext {
    using IndexedFields = std::vector<std::unique_ptr<document::Field>>;
    const index::Schema&              _schema;
    IndexedFields                     _indexed_fields;
    const document::DataType*         _data_type;
    index::SchemaIndexFields          _schema_index_fields;
    vespalib::ISequencedTaskExecutor& _invert_threads;
    vespalib::ISequencedTaskExecutor& _push_threads;
    IFieldIndexCollection&            _field_indexes;
    void add_field(const document::DocumentType& doc_type, uint32_t fieldId);
    void build_fields(const document::DocumentType& doc_type, const document::DataType* data_type);
public:
    DocumentInverterContext(const index::Schema &schema,
                            vespalib::ISequencedTaskExecutor &invert_threads,
                            vespalib::ISequencedTaskExecutor &push_threads,
                            IFieldIndexCollection& field_indexes);
    ~DocumentInverterContext();
    void set_data_type(const document::Document& doc);
    const index::Schema& get_schema() const noexcept { return _schema; }
    const index::SchemaIndexFields& get_schema_index_fields() const noexcept { return _schema_index_fields; }
    vespalib::ISequencedTaskExecutor& get_invert_threads() noexcept { return _invert_threads; }
    vespalib::ISequencedTaskExecutor& get_push_threads() noexcept { return _push_threads; }
    IFieldIndexCollection& get_field_indexes() noexcept { return _field_indexes; }
    std::unique_ptr<document::FieldValue> get_field_value(const document::Document& doc, uint32_t field_id) const;
};

}
