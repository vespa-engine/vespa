// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/schema_index_fields.h>
#include "invert_context.h"
#include "push_context.h"
#include <memory>
#include <vector>

namespace search::memoryindex {

class IFieldIndexCollection;

/*
 * Class containing shared context for document inverters that changes
 * rarely (type dependent data, wiring).
 */
class DocumentInverterContext {
    const index::Schema&              _schema;
    index::SchemaIndexFields          _schema_index_fields;
    vespalib::ISequencedTaskExecutor& _invert_threads;
    vespalib::ISequencedTaskExecutor& _push_threads;
    IFieldIndexCollection&            _field_indexes;
    std::vector<InvertContext>        _invert_contexts;
    std::vector<PushContext>          _push_contexts;
    void setup_contexts();
public:
    DocumentInverterContext(const index::Schema &schema,
                            vespalib::ISequencedTaskExecutor &invert_threads,
                            vespalib::ISequencedTaskExecutor &push_threads,
                            IFieldIndexCollection& field_indexes);
    ~DocumentInverterContext();
    const index::Schema& get_schema() const noexcept { return _schema; }
    const index::SchemaIndexFields& get_schema_index_fields() const noexcept { return _schema_index_fields; }
    vespalib::ISequencedTaskExecutor& get_invert_threads() noexcept { return _invert_threads; }
    vespalib::ISequencedTaskExecutor& get_push_threads() noexcept { return _push_threads; }
    IFieldIndexCollection& get_field_indexes() noexcept { return _field_indexes; }
    const std::vector<InvertContext>& get_invert_contexts() const noexcept { return _invert_contexts; }
    const std::vector<PushContext>& get_push_contexts() const noexcept { return _push_contexts; }
};

}
