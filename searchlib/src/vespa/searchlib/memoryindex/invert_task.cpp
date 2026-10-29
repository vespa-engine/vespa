// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_task.h"

#include "document_inverter_context.h"
#include "field_inverter.h"
#include "invert_context.h"
#include "url_field_inverter.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/common/document_field_extractor.h>

#include <optional>

namespace search::memoryindex {

using document::Document;
using document::Field;

namespace {

std::unique_ptr<document::FieldValue> get_field_value(const Document&                     doc,
                                                      const std::unique_ptr<const Field>& field) {
    if (field) {
        return doc.getValue(*field);
    }
    return {};
}

} // namespace

InvertTask::InvertTask(const DocumentInverterContext& inv_context, const InvertContext& context,
                       const std::vector<std::unique_ptr<FieldInverter>>&    inverters,
                       const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, uint32_t lid,
                       const document::Document& doc, const OnWriteDoneType& on_write_done)
    : _inv_context(inv_context),
      _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _doc(doc),
      _lid(lid),
      _on_write_done(on_write_done) {
}

InvertTask::~InvertTask() = default;

void InvertTask::run() {
    _context.set_data_type(_inv_context, _doc);
    auto document_field_itr = _context.get_document_fields().begin();
    // Shared by the struct field indexes, so that e.g. "sa.f1" and "sa.f2" only deserialize "sa" once.
    // The extracted string values refer to the cached complex field value owned by the extractor.
    std::optional<DocumentFieldExtractor> field_extractor;
    for (auto field_id : _context.get_fields()) {
        auto& indexed_field = *document_field_itr;
        if (indexed_field.struct_field_path) {
            if (!field_extractor.has_value()) {
                field_extractor.emplace(_doc);
            }
            _inverters[field_id]->invertField(_lid, field_extractor->getFieldValue(*indexed_field.struct_field_path),
                                              _doc);
        } else {
            _inverters[field_id]->invertField(_lid, get_field_value(_doc, indexed_field.field), _doc);
        }
        ++document_field_itr;
    }
    auto document_uri_field_itr = _context.get_document_uri_fields().begin();
    for (auto uri_field_id : _context.get_uri_fields()) {
        _uri_inverters[uri_field_id]->invertField(_lid, get_field_value(_doc, *document_uri_field_itr), _doc);
        ++document_uri_field_itr;
    }
}

} // namespace search::memoryindex
