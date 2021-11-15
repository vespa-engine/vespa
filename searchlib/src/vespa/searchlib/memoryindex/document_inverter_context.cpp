// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter_context.h"
#include <cassert>
#include <optional>

namespace search::memoryindex {

using vespalib::ISequencedTaskExecutor;
using index::SchemaIndexFields;

namespace {

template <typename Context>
void make_contexts(const index::Schema& schema, const SchemaIndexFields& schema_index_fields, ISequencedTaskExecutor& executor, std::vector<Context>& contexts)
{
    using ExecutorId = ISequencedTaskExecutor::ExecutorId;
    using IdMapping = std::vector<std::tuple<ExecutorId, bool, uint32_t>>;
    IdMapping map;
    for (uint32_t field_id : schema_index_fields._textFields) {
        // TODO: Add bias when sharing sequenced task executor between document types
        auto& name = schema.getIndexField(field_id).getName();
        auto id = executor.getExecutorIdFromName(name);
        map.emplace_back(id, false, field_id);
    }
    uint32_t uri_field_id = 0;
    for (auto& uri_field : schema_index_fields._uriFields) {
        // TODO: Add bias when sharing sequenced task executor between document types
        auto& name = schema.getIndexField(uri_field._all).getName();
        auto id = executor.getExecutorIdFromName(name);
        map.emplace_back(id, true, uri_field_id);
        ++uri_field_id;
    }
    std::sort(map.begin(), map.end());
    std::optional<ExecutorId> prev_id;
    for (auto& entry : map) {
        if (!prev_id.has_value() || prev_id.value() != std::get<0>(entry)) {
            contexts.emplace_back(std::get<0>(entry));
            prev_id = std::get<0>(entry);
        }
        if (std::get<1>(entry)) {
            contexts.back().add_uri_field(std::get<2>(entry));
        } else {
            contexts.back().add_field(std::get<2>(entry));
        }
    }
}

void switch_to_alternate_ids(ISequencedTaskExecutor& executor, std::vector<PushContext>& contexts, uint32_t bias)
{
    for (auto& context : contexts) {
        context.set_id(executor.get_alternate_executor_id(context.get_id(), bias));
    }
}

class PusherMapping {
    std::vector<std::optional<uint32_t>> _pushers;
public:
    PusherMapping(size_t size);
    ~PusherMapping();

    void add_mapping(const std::vector<uint32_t>& fields, uint32_t pusher_id) {
        for (auto field_id : fields) {
            assert(field_id < _pushers.size());
            auto& opt_pusher = _pushers[field_id];
            assert(!opt_pusher.has_value());
            opt_pusher = pusher_id;
        }
    }

    void use_mapping(const std::vector<uint32_t>& fields, std::vector<uint32_t>& pushers) {
        for (auto field_id : fields) {
            assert(field_id < _pushers.size());
            auto& opt_pusher = _pushers[field_id];
            assert(opt_pusher.has_value());
            pushers.emplace_back(opt_pusher.value());
        }
    }
};

PusherMapping::PusherMapping(size_t size)
    : _pushers(size)
{
}

PusherMapping::~PusherMapping() = default;

/*
 * Connect contexts for inverting to contexts for pushing. If we use
 * different sequenced task executors or adds different biases to the
 * getExecutorId() argument (to enable double buffering) then contexts
 * for inverting and contexts for pushing will bundle different sets
 * of fields, preventing a 1:1 mapping.  If we use the same sequenced
 * task executor and drop double buffering then we can simplify this
 * to a 1:1 mapping.
 */
void connect_contexts(std::vector<InvertContext>& invert_contexts,
                      const std::vector<PushContext>& push_contexts,
                      uint32_t num_fields,
                      uint32_t num_uri_fields)
{
    PusherMapping field_to_pusher(num_fields);
    PusherMapping uri_field_to_pusher(num_uri_fields);
    uint32_t pusher_id = 0;
    for (auto& push_context : push_contexts) {
        field_to_pusher.add_mapping(push_context.get_fields(), pusher_id);
        uri_field_to_pusher.add_mapping(push_context.get_uri_fields(), pusher_id);
        ++pusher_id;
    }
    std::vector<uint32_t> pushers;
    for (auto& invert_context : invert_contexts) {
        pushers.clear();
        field_to_pusher.use_mapping(invert_context.get_fields(), pushers);
        uri_field_to_pusher.use_mapping(invert_context.get_uri_fields(), pushers);
        std::sort(pushers.begin(), pushers.end());
        auto last = std::unique(pushers.begin(), pushers.end());
        pushers.erase(last, pushers.end());
        for (auto pusher : pushers) {
            invert_context.add_pusher(pusher);
        }
    }
}

}

DocumentInverterContext::DocumentInverterContext(const index::Schema& schema,
                                                 ISequencedTaskExecutor &invert_threads,
                                                 ISequencedTaskExecutor &push_threads,
                                                 IFieldIndexCollection& field_indexes)
    : _schema(schema),
      _schema_index_fields(),
      _invert_threads(invert_threads),
      _push_threads(push_threads),
      _field_indexes(field_indexes),
      _invert_contexts(),
      _push_contexts()
{
    _schema_index_fields.setup(schema);
    setup_contexts();
}

DocumentInverterContext::~DocumentInverterContext() = default;

void
DocumentInverterContext::setup_contexts()
{
    make_contexts(_schema, _schema_index_fields, _invert_threads, _invert_contexts);
    make_contexts(_schema, _schema_index_fields, _push_threads, _push_contexts);
    if (&_invert_threads == &_push_threads) {
        uint32_t bias = _schema_index_fields._textFields.size() + _schema_index_fields._uriFields.size();
        switch_to_alternate_ids(_push_threads, _push_contexts, bias);
    }
    connect_contexts(_invert_contexts, _push_contexts, _schema.getNumIndexFields(), _schema_index_fields._uriFields.size());
}

}
