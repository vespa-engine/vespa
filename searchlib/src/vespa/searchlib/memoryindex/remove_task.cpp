// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remove_task.h"
#include "document_inverter_context.h"
#include "field_inverter.h"
#include "invert_context.h"
#include "url_field_inverter.h"

namespace search::memoryindex {

namespace {

template <typename Inverter>
void remove_documents(Inverter& inverter, const std::vector<uint32_t>& lids)
{
    for (auto lid : lids) {
        inverter.removeDocument(lid);
    }
}

}

RemoveTask::RemoveTask(const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, const std::vector<uint32_t>& lids)
    : _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _lids(lids)
{
}

RemoveTask::~RemoveTask() = default;

void
RemoveTask::run()
{
    for (auto field_id : _context.get_fields()) {
        remove_documents(*_inverters[field_id], _lids);
    }
    for (auto uri_field_id : _context.get_uri_fields()) {
        remove_documents(*_uri_inverters[uri_field_id], _lids);
    }
}

}
