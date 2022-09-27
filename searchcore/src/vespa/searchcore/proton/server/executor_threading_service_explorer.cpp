// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_explorer.h"
#include "executor_explorer_utils.h"
#include "executorthreadingservice.h"
#include <vespa/vespalib/data/slime/cursor.h>

namespace proton {

using explorer::convert_executor_to_slime;

ExecutorThreadingServiceExplorer::ExecutorThreadingServiceExplorer(searchcorespi::index::IThreadingService& service)
    : _service(service)
{
}

ExecutorThreadingServiceExplorer::~ExecutorThreadingServiceExplorer() = default;

void
ExecutorThreadingServiceExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    if (full) {
        convert_executor_to_slime(&_service.master(), object.setObject("master"));
        convert_executor_to_slime(&_service.index(), object.setObject("index"));
        convert_executor_to_slime(&_service.summary(), object.setObject("summary"));
        convert_executor_to_slime(&_service.indexFieldInverter(), object.setObject("index_field_inverter"));
        convert_executor_to_slime(&_service.indexFieldWriter(), object.setObject("index_field_writer"));
        convert_executor_to_slime(&_service.attributeFieldWriter(), object.setObject("attribute_field_writer"));
    }
}

}

