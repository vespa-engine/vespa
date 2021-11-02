// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter_context.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.document_inverter_context");

namespace search::memoryindex {

using document::DataType;
using document::Document;
using document::DocumentType;
using document::Field;
using vespalib::ISequencedTaskExecutor;

void
DocumentInverterContext::add_field(const DocumentType& doc_type, uint32_t fieldId)
{
    assert(fieldId < _indexed_fields.size());
    std::unique_ptr<Field> fp;
    if ( ! doc_type.hasField(_schema.getIndexField(fieldId).getName())) {
        LOG(error,
            "Mismatch between documentdefinition and schema. "
            "No field named '%s' from schema in document type '%s'",
            _schema.getIndexField(fieldId).getName().c_str(),
            doc_type.getName().c_str());
    } else {
        fp = std::make_unique<Field>(doc_type.getField(_schema.getIndexField(fieldId).getName()));
    }
    _indexed_fields[fieldId] = std::move(fp);
}

void
DocumentInverterContext::build_fields(const DocumentType& doc_type, const DataType *data_type)
{
    _indexed_fields.clear();
    _indexed_fields.resize(_schema.getNumIndexFields());
    for (const auto & fi : _schema_index_fields._textFields) {
        add_field(doc_type, fi);
    }
    for (const auto & fi : _schema_index_fields._uriFields) {
        add_field(doc_type, fi._all);
    }
    _data_type = data_type;
}

DocumentInverterContext::DocumentInverterContext(const index::Schema& schema,
                                                 ISequencedTaskExecutor &invert_threads,
                                                 ISequencedTaskExecutor &push_threads,
                                                 IFieldIndexCollection& field_indexes)
    : _schema(schema),
      _indexed_fields(),
      _data_type(nullptr),
      _schema_index_fields(),
      _invert_threads(invert_threads),
      _push_threads(push_threads),
      _field_indexes(field_indexes)
{
    _schema_index_fields.setup(schema);
}

DocumentInverterContext::~DocumentInverterContext() = default;

void
DocumentInverterContext::set_data_type(const Document& doc)
{
    const DataType *data_type(doc.getDataType());
    if (_indexed_fields.empty() || _data_type != data_type) {
        build_fields(doc.getType(), data_type);
    }
}

std::unique_ptr<document::FieldValue>
DocumentInverterContext::get_field_value(const Document& doc, uint32_t field_id) const
{
    const Field *const field(_indexed_fields[field_id].get());
    if (field != nullptr) {
        return doc.getValue(*field);
    }
    return {};
}

}
