// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "push_task.h"
#include "push_context.h"
#include "field_inverter.h"
#include "url_field_inverter.h"

namespace search::memoryindex {

namespace {

template <typename Inverter>
void push_inverter(Inverter& inverter)
{
    inverter.applyRemoves();
    inverter.pushDocuments();
}

}


PushTask::PushTask(const PushContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, OnWriteDoneType on_write_done, std::shared_ptr<vespalib::RetainGuard> retain)
    : _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _on_write_done(on_write_done),
      _retain(std::move(retain))
{
}

PushTask::~PushTask() = default;

void
PushTask::run()
{
    for (auto field_id : _context.get_fields()) {
        push_inverter(*_inverters[field_id]);
    }
    for (auto uri_field_id : _context.get_uri_fields()) {
        push_inverter(*_uri_inverters[uri_field_id]);
    }
}

}
