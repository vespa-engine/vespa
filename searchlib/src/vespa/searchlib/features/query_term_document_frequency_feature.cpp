// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_document_frequency_feature.h"

#include "constant_tensor_executor.h"
#include "utils.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/vespalib/util/stash.h>

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::FeatureType;
using search::fef::IDumpFeatureVisitor;
using search::fef::IIndexEnvironment;
using search::fef::IQueryEnvironment;
using search::fef::ParameterList;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;

namespace search::features {

QueryTermDocumentFrequencyBlueprint::QueryTermDocumentFrequencyBlueprint()
    : Blueprint("queryTermDocumentFrequency"),
      _field_id(0),
      _value_type(ValueType::from_spec("tensor(term{})")) {
}

void QueryTermDocumentFrequencyBlueprint::visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const {
}

bool QueryTermDocumentFrequencyBlueprint::setup(const IIndexEnvironment&, const ParameterList& params) {
    _field_id = params[0].asField()->id();
    describeOutput("out",
                   "The document frequency BM25 would use for each query term in this field, including "
                   "query-provided overrides, as a tensor(term{}) with query-term indexes as labels. "
                   "In streaming search, non-overridden values are 0.",
                   FeatureType::object(_value_type));
    return true;
}

Blueprint::UP QueryTermDocumentFrequencyBlueprint::createInstance() const {
    return std::make_unique<QueryTermDocumentFrequencyBlueprint>();
}

FeatureExecutor& QueryTermDocumentFrequencyBlueprint::createExecutor(const IQueryEnvironment& env,
                                                                     vespalib::Stash&         stash) const {
    std::vector<std::pair<std::string, double>> cells;
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        const fef::ITermData* term = env.getTerm(i);
        if (term == nullptr) {
            continue;
        }
        const fef::ITermFieldData* term_field = term->lookupField(_field_id);
        if (term_field == nullptr) {
            continue;
        }
        auto   override_freq = util::lookup_document_frequency(env, *term);
        double frequency = override_freq.has_value() ? static_cast<double>(override_freq.value().frequency)
                                                     : static_cast<double>(term_field->get_doc_freq().frequency);
        cells.emplace_back(std::to_string(i), frequency);
    }
    if (cells.empty()) {
        return ConstantTensorExecutor::createEmpty(_value_type, stash);
    }
    auto                          factory = FastValueBuilderFactory::get();
    auto                          builder = factory.create_value_builder<double>(_value_type, 1, 1, cells.size());
    std::vector<std::string_view> addr_ref;
    for (const auto& cell : cells) {
        addr_ref.clear();
        addr_ref.push_back(cell.first);
        auto cell_array = builder->add_subspace(addr_ref);
        cell_array[0] = cell.second;
    }
    return ConstantTensorExecutor::create(builder->build(std::move(builder)), stash);
}

} // namespace search::features
