// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "params.h"
#include "simplemetrics.h"
#include <vespa/searchlib/features/queryterm.h>

namespace search::fef { class PhraseSplitterQueryEnv; }

namespace search::features::fieldmatch {

/**
 * Shared state for field match computer.
 */
class ComputerSharedState {
public:
    /**
     * Constructs a new computer shared state object.
     *
     * @param propertyNamespace The namespace used in query properties.
     * @param splitter_query_env The environment that holds all query information.
     * @param fieldInfo         The info object of the matched field.
     * @param params            The parameter object for this computer.
     */
    ComputerSharedState(const vespalib::string& propertyNamespace, const fef::PhraseSplitterQueryEnv& splitter_query_env,
                        const fef::FieldInfo& fieldInfo, const Params& params);
    ~ComputerSharedState();

    uint32_t get_field_id() const { return _field_id; }
    const Params& get_params() const { return _params; }
    bool get_use_cached_hits() const { return _use_cached_hits; }
    const QueryTermVector& get_query_terms() const { return _query_terms; }
    uint32_t get_total_term_weight() const { return _total_term_weight; }
    feature_t get_total_term_significance() const { return _total_term_significance; }
    const SimpleMetrics& get_simple_metrics() const { return _simple_metrics; }

private:

    // per query
    uint32_t                                   _field_id;
    Params                                     _params;
    bool                                       _use_cached_hits;

    QueryTermVector                            _query_terms;
    uint32_t                                   _total_term_weight;
    feature_t                                  _total_term_significance;

    // portions per docid (not used here), portions per query
    SimpleMetrics                              _simple_metrics;  // The metrics used to compute simple features.
};

}
