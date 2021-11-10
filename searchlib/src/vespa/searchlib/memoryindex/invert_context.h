// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bundled_fields_context.h"

namespace document {
class DataType;
class Document;
class Field;
}

namespace search::index {
class Schema;
class SchemaIndexFields;
}

namespace search::memoryindex {

class DocumentInverterContext;

/*
 * Context used by an InvertTask to invert a set of document fields
 * into corresponding field inverters or by a RemoveTask to remove
 * documents from a set of field inverters.
 *
 * It is also used by DocumentInverter::pushDocuments() to execute
 * PushTask at the proper time (i.e. when all related InvertTask /
 * RemoveTask operations have completed).
 */
class InvertContext : public BundledFieldsContext
{
    using IndexedFields = std::vector<std::unique_ptr<const document::Field>>;
    std::vector<uint32_t> _pushers;
    vespalib::string      _document_field_names;
    mutable IndexedFields _document_fields;
    mutable IndexedFields _document_uri_fields;
    mutable const document::DataType* _data_type;
public:
    void add_pusher(uint32_t pusher_id);
    InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id);
    ~InvertContext();
    InvertContext(InvertContext&&);
    const std::vector<uint32_t>& get_pushers() const noexcept { return _pushers; }
    void set_data_type(const DocumentInverterContext& doc_inv_context, const document::Document& doc) const;
    const IndexedFields& get_document_fields() const noexcept { return _document_fields; }
    const IndexedFields& get_document_uri_fields() const noexcept { return _document_uri_fields; }
};

}
