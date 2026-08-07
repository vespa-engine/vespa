// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm25_for_labels_feature.h"

#include "constant_tensor_executor.h"
#include "utils.h"

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/match_data_details.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/searchlib/tensor/fast_value_view.h>
#include <vespa/vespalib/util/stash.h>

#include <algorithm>

namespace search::features {

using fef::AnyWrapper;
using fef::Blueprint;
using fef::FeatureExecutor;
using fef::FeatureType;
using fef::ITermData;
using fef::ITermFieldData;
using fef::MatchDataDetails;
using fef::objectstore::as_value;
using search::tensor::FastValueView;
using vespalib::Trinary;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

// keep in sync with default_k1_param / default_b_param in bm25_feature.cpp
double constexpr default_k1_param = 1.2;
double constexpr default_b_param = 0.75;

// The tuning properties and the shared average field length are the ones of bm25(<field>).
std::string bm25_feature_base_name("bm25");

// keep in sync with make_avg_field_length_key in bm25_feature.cpp, which is the other writer of this key
std::string make_avg_field_length_key(const std::string& base_name, const std::string& field_name) {
    return base_name + ".afl." + field_name;
}

double get_average_field_length(const fef::IQueryEnvironment& env, const std::string& field_name) {
    auto info = env.get_field_length_info(field_name);
    return info.get_average_field_length();
}

// keep in sync with Bm25Executor::add_term_fields in bm25_feature.cpp
std::vector<Bm25Utils::QueryTerm> collect_field_terms(const std::vector<const ITermData*>& terms, uint32_t field_id,
                                                      const fef::IQueryEnvironment& env, double k1_param) {
    std::vector<Bm25Utils::QueryTerm> field_terms;
    for (const ITermData* term : terms) {
        for (size_t j = 0; j < term->numFields(); ++j) {
            const ITermFieldData& term_field = term->field(j);
            if (field_id == term_field.getFieldId()) {
                field_terms.emplace_back(term_field.getHandle(MatchDataDetails::Interleaved),
                                         Bm25Utils::get_inverse_document_frequency(term_field, env, *term), k1_param);
            }
        }
    }
    return field_terms;
}

} // namespace

Bm25ForLabelsExecutor::Bm25ForLabelsExecutor(std::vector<std::pair<std::string, std::vector<QueryTerm>>> labeled_terms,
                                             double avg_field_length, double k1_param, double b_param,
                                             const Value& empty_output)
    : FeatureExecutor(),
      _terms_per_label(),
      _avg_field_length(avg_field_length),
      _k1_mul_b(k1_param * b_param),
      _k1_mul_one_minus_b(k1_param * (1 - b_param)),
      _labels(),
      _view_labels(),
      _view_cells(),
      _empty_output(empty_output),
      _output() {
    _terms_per_label.reserve(labeled_terms.size());
    _labels.reserve(labeled_terms.size());
    for (auto& labeled_term : labeled_terms) {
        // Labels are resolved once here, not per document, since every document uses the same labels.
        _labels.add(labeled_term.first);
        _terms_per_label.push_back(std::move(labeled_term.second));
    }
    _view_labels.reserve(labeled_terms.size());
    _view_cells.reserve(labeled_terms.size());
}

Bm25ForLabelsExecutor::~Bm25ForLabelsExecutor() = default;

// keep in sync with the per term scoring in Bm25Executor::execute in bm25_feature.cpp
feature_t Bm25ForLabelsExecutor::term_score(const QueryTerm& term, uint32_t doc_id) const {
    if (!term.tfmd->has_ranking_data(doc_id)) {
        return 0.0;
    }
    auto raw_num_occs = term.tfmd->getNumOccs();
    if (raw_num_occs == 0) {
        // Interleaved features are missing. Assume 1 occurrence and average field length.
        return term.degraded_score;
    }
    feature_t num_occs = raw_num_occs;
    feature_t norm_field_length = ((feature_t)term.tfmd->getFieldLength()) / _avg_field_length;
    feature_t numerator = num_occs * term.idf_mul_k1_plus_one;
    feature_t denominator = num_occs + (_k1_mul_one_minus_b + _k1_mul_b * norm_field_length);
    return numerator / denominator;
}

void Bm25ForLabelsExecutor::handle_bind_match_data(const fef::MatchData& match_data) {
    for (auto& terms : _terms_per_label) {
        for (auto& term : terms) {
            term.tfmd = match_data.resolveTermField(term.handle);
        }
    }
}

void Bm25ForLabelsExecutor::execute(uint32_t doc_id) {
    _view_labels.clear();
    _view_cells.clear();
    const auto& labels = _labels.view();
    for (uint32_t i = 0, m = _terms_per_label.size(); i < m; ++i) {
        double score = 0;
        for (const auto& term : _terms_per_label[i]) {
            score += term_score(term, doc_id);
        }
        if (score != 0.0) {
            _view_labels.push_back(labels[i]);
            _view_cells.push_back(score);
        }
    }
    if (_view_cells.empty()) {
        outputs().set_object(0, _empty_output);
        return;
    }
    _output = std::make_unique<FastValueView>(_empty_output.type(), _view_labels, TypedCells(_view_cells), 1,
                                              _view_cells.size());
    outputs().set_object(0, *_output);
}

Bm25ForLabelsBlueprint::Bm25ForLabelsBlueprint()
    : Blueprint("bm25_for_labels"),
      _field(nullptr),
      _k1_param(default_k1_param),
      _b_param(default_b_param),
      _avg_field_length(),
      _value_type(ValueType::from_spec("tensor(label{})")),
      _empty_output() {
}

Bm25ForLabelsBlueprint::~Bm25ForLabelsBlueprint() = default;

void Bm25ForLabelsBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const {
}

Blueprint::UP Bm25ForLabelsBlueprint::createInstance() const {
    return std::make_unique<Bm25ForLabelsBlueprint>();
}

fef::ParameterDescriptions Bm25ForLabelsBlueprint::getDescriptions() const {
    return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
}

bool Bm25ForLabelsBlueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) {
    _field = params[0].asField();
    Bm25Utils bm25_utils(bm25_feature_base_name + "(" + _field->name() + ").", env.getProperties());
    if (bm25_utils.lookup_param(Bm25Utils::k1(), _k1_param) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }
    if (bm25_utils.lookup_param(Bm25Utils::b(), _b_param) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }
    if (bm25_utils.lookup_param(Bm25Utils::average_field_length(), _avg_field_length) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }
    _empty_output = vespalib::eval::value_from_spec(_value_type.to_spec(), FastValueBuilderFactory::get());
    describeOutput("score",
                   "The bm25 score in the given index field for the terms carrying each query item label, "
                   "as a tensor(label{}) with the labels as cell labels",
                   FeatureType::object(_value_type));
    return true;
}

void Bm25ForLabelsBlueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const {
    std::string key = make_avg_field_length_key(bm25_feature_base_name, _field->name());
    if (store.get(key) == nullptr) {
        double avg_field_length = _avg_field_length.value_or(get_average_field_length(env, _field->name()));
        store.add(key, std::make_unique<AnyWrapper<double>>(avg_field_length));
    }
}

FeatureExecutor& Bm25ForLabelsBlueprint::createExecutor(const fef::IQueryEnvironment& env,
                                                        vespalib::Stash&              stash) const {
    const auto* lookup_result =
        env.getObjectStore().get(make_avg_field_length_key(bm25_feature_base_name, _field->name()));
    double avg_field_length = lookup_result != nullptr
                                  ? as_value<double>(*lookup_result)
                                  : _avg_field_length.value_or(get_average_field_length(env, _field->name()));
    std::vector<std::pair<std::string, std::vector<Bm25Utils::QueryTerm>>> labeled_terms;
    for (auto& labeled_term : util::getTermsByAllLabels(env)) {
        auto field_terms = collect_field_terms(labeled_term.second, _field->id(), env, _k1_param);
        if (!field_terms.empty()) {
            labeled_terms.emplace_back(labeled_term.first, std::move(field_terms));
        }
    }
    if (labeled_terms.empty()) {
        return stash.create<ConstantTensorRefExecutor>(*_empty_output);
    }
    return stash.create<Bm25ForLabelsExecutor>(std::move(labeled_terms), avg_field_length, _k1_param, _b_param,
                                               *_empty_output);
}

} // namespace search::features
