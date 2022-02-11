// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter.h"
#include "document_inverter_context.h"
#include "i_field_index_collection.h"
#include "field_inverter.h"
#include "invert_task.h"
#include "push_task.h"
#include "remove_task.h"
#include "url_field_inverter.h"
#include <vespa/searchlib/common/schedule_sequenced_task_callback.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <cassert>

namespace search::memoryindex {

using document::Document;
using index::Schema;
using search::ScheduleSequencedTaskCallback;
using search::index::FieldLengthCalculator;
using vespalib::ISequencedTaskExecutor;
using vespalib::RetainGuard;

DocumentInverter::DocumentInverter(DocumentInverterContext& context)
    : _context(context),
      _inverters(),
      _urlInverters()
{
    auto& schema = context.get_schema();
    auto& field_indexes = context.get_field_indexes();
    for (uint32_t fieldId = 0; fieldId < schema.getNumIndexFields(); ++fieldId) {
        auto &remover(field_indexes.get_remover(fieldId));
        auto &inserter(field_indexes.get_inserter(fieldId));
        auto &calculator(field_indexes.get_calculator(fieldId));
        _inverters.push_back(std::make_unique<FieldInverter>(schema, fieldId, remover, inserter, calculator));
    }
    auto& schema_index_fields = context.get_schema_index_fields();
    for (auto &urlField : schema_index_fields._uriFields) {
        Schema::CollectionType collectionType =
            schema.getIndexField(urlField._all).getCollectionType();
        _urlInverters.push_back(std::make_unique<UrlFieldInverter>
                                (collectionType,
                                 _inverters[urlField._all].get(),
                                 _inverters[urlField._scheme].get(),
                                 _inverters[urlField._host].get(),
                                 _inverters[urlField._port].get(),
                                 _inverters[urlField._path].get(),
                                 _inverters[urlField._query].get(),
                                 _inverters[urlField._fragment].get(),
                                 _inverters[urlField._hostname].get()));
    }
}

DocumentInverter::~DocumentInverter()
{
    wait_for_zero_ref_count();
}

void
DocumentInverter::invertDocument(uint32_t docId, const Document &doc, OnWriteDoneType on_write_done)
{
    auto& invert_threads = _context.get_invert_threads();
    auto& invert_contexts = _context.get_invert_contexts();
    for (auto& invert_context : invert_contexts) {
        auto id = invert_context.get_id();
        auto task = std::make_unique<InvertTask>(_context, invert_context, _inverters, _urlInverters, docId, doc, on_write_done);
        invert_threads.executeTask(id, std::move(task));
    }
}

void
DocumentInverter::removeDocument(uint32_t docId) {
    LidVector lids;
    lids.push_back(docId);
    removeDocuments(std::move(lids));
}
void
DocumentInverter::removeDocuments(LidVector lids)
{
    auto& invert_threads = _context.get_invert_threads();
    auto& invert_contexts = _context.get_invert_contexts();
    for (auto& invert_context : invert_contexts) {
        auto id = invert_context.get_id();
        auto task = std::make_unique<RemoveTask>(invert_context, _inverters, _urlInverters, lids);
        invert_threads.executeTask(id, std::move(task));
    }
}

void
DocumentInverter::pushDocuments(OnWriteDoneType on_write_done)
{
    auto retain = std::make_shared<RetainGuard>(_ref_count);
    using PushTasks = std::vector<std::shared_ptr<ScheduleSequencedTaskCallback>>;
    PushTasks all_push_tasks;
    auto& push_threads = _context.get_push_threads();
    auto& push_contexts = _context.get_push_contexts();
    for (auto& push_context : push_contexts) {
        auto task = std::make_unique<PushTask>(push_context, _inverters, _urlInverters, on_write_done, retain);
        all_push_tasks.emplace_back(std::make_shared<ScheduleSequencedTaskCallback>(push_threads, push_context.get_id(), std::move(task)));
    }
    auto& invert_threads = _context.get_invert_threads();
    auto& invert_contexts = _context.get_invert_contexts();
    for (auto& invert_context : invert_contexts) {
        PushTasks push_tasks;
        for (auto& pusher : invert_context.get_pushers()) {
            assert(pusher < all_push_tasks.size());
            push_tasks.emplace_back(all_push_tasks[pusher]);
        }
        invert_threads.execute(invert_context.get_id(), [push_tasks(std::move(push_tasks))]() { });
    }
}

}
