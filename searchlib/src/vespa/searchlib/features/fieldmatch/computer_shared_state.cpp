// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "computer_shared_state.h"
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/phrase_splitter_query_env.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/locale/c.h>
#include <set>

using namespace search::fef;

namespace search::features::fieldmatch {

ComputerSharedState::ComputerSharedState(const vespalib::string& propertyNamespace, const PhraseSplitterQueryEnv& splitter_query_env,
                   const FieldInfo& fieldInfo, const Params& params)
    : _field_id(fieldInfo.id()),
      _params(params),
      _use_cached_hits(true),
      _query_terms(),
      _total_term_weight(0),
      _total_term_significance(0.0f),
      _simple_metrics(_params)
{
    // Store term data for all terms searching in this field
    for (uint32_t i = 0; i < splitter_query_env.getNumTerms(); ++i) {
        QueryTerm qt = QueryTermFactory::create(splitter_query_env, i, true);
        _total_term_weight += qt.termData()->getWeight().percent();
        _total_term_significance += qt.significance();
        _simple_metrics.addQueryTerm(qt.termData()->getWeight().percent());
        const ITermFieldData *field = qt.termData()->lookupField(_field_id);
        if (field != nullptr) {
            qt.fieldHandle(field->getHandle());
            _query_terms.push_back(qt);
            _simple_metrics.addSearchedTerm(qt.termData()->getWeight().percent());
        }
    }

    _total_term_weight = atoi(splitter_query_env.getProperties().lookup(propertyNamespace, "totalTermWeight").
                              get(vespalib::make_string("%d", _total_term_weight)).c_str());
    _total_term_significance = vespalib::locale::c::atof(splitter_query_env.getProperties().lookup(propertyNamespace, "totalTermSignificance").
                                                         get(vespalib::make_string("%f", _total_term_significance)).c_str());
    if (splitter_query_env.getProperties().lookup(propertyNamespace, "totalTermWeight").found()) {
        _simple_metrics.setTotalWeightInQuery(_total_term_weight);
    }
}

ComputerSharedState::~ComputerSharedState() = default;

}
