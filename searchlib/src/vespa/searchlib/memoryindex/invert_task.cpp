// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_task.h"
#include "document_inverter_context.h"
#include "field_inverter.h"
#include "invert_context.h"
#include "url_field_inverter.h"

namespace search::memoryindex {

InvertTask::InvertTask(const DocumentInverterContext& inv_context, const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, uint32_t lid, const document::Document& doc)
    : _inv_context(inv_context),
      _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _field_values(),
      _uri_field_values(),
      _lid(lid)
{
    _field_values.reserve(_context.get_fields().size());
    _uri_field_values.reserve(_context.get_uri_fields().size());
    for (uint32_t field_id : _context.get_fields()) {
        _field_values.emplace_back(_inv_context.get_field_value(doc, field_id));
    }
    const auto& schema_index_fields = _inv_context.get_schema_index_fields();
    for (uint32_t uri_field_id : _context.get_uri_fields()) {
        uint32_t field_id = schema_index_fields._uriFields[uri_field_id]._all;
        _uri_field_values.emplace_back(_inv_context.get_field_value(doc, field_id));
    }
}

InvertTask::~InvertTask() = default;

void
InvertTask::run()
{
    assert(_field_values.size() == _context.get_fields().size());
    assert(_uri_field_values.size() == _context.get_uri_fields().size());
    auto fv_itr = _field_values.begin();
    for (auto field_id : _context.get_fields()) {
        _inverters[field_id]->invertField(_lid, *fv_itr);
        ++fv_itr;
    }
    auto uri_fv_itr = _uri_field_values.begin();
    for (auto uri_field_id : _context.get_uri_fields()) {
        _uri_inverters[uri_field_id]->invertField(_lid, *uri_fv_itr);
        ++uri_fv_itr;
    }
}

}
