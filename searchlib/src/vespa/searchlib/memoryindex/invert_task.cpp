// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_task.h"
#include "document_inverter_context.h"
#include "field_inverter.h"
#include "invert_context.h"
#include "url_field_inverter.h"
#include <vespa/document/fieldvalue/document.h>

namespace search::memoryindex {

using document::Document;
using document::Field;

namespace {

std::unique_ptr<document::FieldValue>
get_field_value(const Document& doc, const std::unique_ptr<const Field>& field)
{
    if (field) {
        return doc.getValue(*field);
    }
    return {};
}

}

InvertTask::InvertTask(const DocumentInverterContext& inv_context, const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, uint32_t lid, const document::Document& doc, OnWriteDoneType on_write_done)
    : _inv_context(inv_context),
      _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _doc(doc),
      _lid(lid),
      _on_write_done(on_write_done)
{
}

InvertTask::~InvertTask() = default;

void
InvertTask::run()
{
    _context.set_data_type(_inv_context, _doc);
    auto document_field_itr = _context.get_document_fields().begin();
    for (auto field_id : _context.get_fields()) {
        _inverters[field_id]->invertField(_lid, get_field_value(_doc, *document_field_itr));
        ++document_field_itr;
    }
    auto document_uri_field_itr = _context.get_document_uri_fields().begin();
    for (auto uri_field_id : _context.get_uri_fields()) {
        _uri_inverters[uri_field_id]->invertField(_lid, get_field_value(_doc, *document_uri_field_itr));
        ++document_uri_field_itr;
    }
}

}
