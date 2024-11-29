// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm25_feature.h"
#include "utils.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <algorithm>
#include <cmath>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".features.bm25_feature");

namespace search::features {

using fef::AnyWrapper;
using fef::Blueprint;
using fef::DocumentFrequency;
using fef::FeatureExecutor;
using fef::FeatureNameBuilder;
using fef::FieldInfo;
using fef::FieldType;
using fef::ITermData;
using fef::ITermFieldData;
using fef::MatchDataDetails;
using fef::objectstore::as_value;
using vespalib::Trinary;

namespace {

double
get_inverse_document_frequency(const ITermFieldData& term_field,
                               const fef::IQueryEnvironment& env,
                               const ITermData& term)

{
    auto doc_freq = util::lookup_document_frequency(env, term);
    if (doc_freq.has_value()) {
        return Bm25Executor::calculate_inverse_document_frequency(doc_freq.value());
    }
    double fallback = Bm25Executor::calculate_inverse_document_frequency(term_field.get_doc_freq());
    return util::lookupSignificance(env, term, fallback);
}

}

Bm25Executor::Bm25Executor(const fef::FieldInfo& field,
                           const fef::IQueryEnvironment& env,
                           double avg_field_length,
                           double k1_param,
                           double b_param)
    : FeatureExecutor(),
      _terms(),
      _avg_field_length(avg_field_length),
      _k1_mul_b(k1_param * b_param),
      _k1_mul_one_minus_b(k1_param * (1 - b_param))
{
    for (size_t i = 0; i < env.getNumTerms(); ++i) {
        const ITermData* term = env.getTerm(i);
        for (size_t j = 0; j < term->numFields(); ++j) {
            const ITermFieldData& term_field = term->field(j);
            if (field.id() == term_field.getFieldId()) {
                _terms.emplace_back(term_field.getHandle(MatchDataDetails::Interleaved),
                                    get_inverse_document_frequency(term_field, env, *term),
                                    k1_param);
            }
        }
    }
}

double
Bm25Executor::calculate_inverse_document_frequency(DocumentFrequency doc_freq) noexcept
{
    double frequency = doc_freq.frequency;
    double count = doc_freq.count;
    count = std::max(1.0, count);
    frequency = std::min(std::max(1.0, frequency), count);
    return std::log(1 + ((count - frequency + 0.5) / (frequency + 0.5)));
}

void
Bm25Executor::handle_bind_match_data(const fef::MatchData& match_data)
{
    for (auto& term : _terms) {
        term.tfmd = match_data.resolveTermField(term.handle);
    }
}

void
Bm25Executor::execute(uint32_t doc_id)
{
    feature_t score = 0;
    for (const auto& term : _terms) {
        if (term.tfmd->getDocId() == doc_id) {
            auto raw_num_occs = term.tfmd->getNumOccs();
            if (raw_num_occs == 0) {
                // Interleaved features are missing. Assume 1 occurrence and average field length.
                score += term.degraded_score;
            } else {
                feature_t num_occs = raw_num_occs;
                feature_t norm_field_length = ((feature_t) term.tfmd->getFieldLength()) / _avg_field_length;
                feature_t numerator = num_occs * term.idf_mul_k1_plus_one;
                feature_t denominator = num_occs + (_k1_mul_one_minus_b + _k1_mul_b * norm_field_length);

                score += numerator / denominator;
            }
        }
    }
    outputs().set_number(0, score);
}

Trinary
Bm25Blueprint::lookup_param(const fef::Properties& props, const std::string& param, double& result) const
{
    std::string key = getBaseName() + "(" + _field->name() + ")." + param;
    auto value = props.lookup(key);
    if (value.found()) {
        try {
            result = std::stod(value.get());
            return Trinary::True;
        } catch (const std::invalid_argument& ex) {
            LOG(warning, "Not able to convert rank property '%s': '%s' to a double value",
                key.c_str(), value.get().c_str());
            return Trinary::Undefined;
        }
    }
    return Trinary::False;
}

Trinary
Bm25Blueprint::lookup_param(const fef::Properties& props, const std::string& param, std::optional<double>& result) const
{
    double tmp_result;
    auto lres = lookup_param(props, param, tmp_result);
    if (lres == Trinary::True) {
        result = tmp_result;
    }
    return lres;
}

double constexpr default_k1_param = 1.2;
double constexpr default_b_param = 0.75;

Bm25Blueprint::Bm25Blueprint()
    : Blueprint("bm25"),
      _field(nullptr),
      _k1_param(default_k1_param),
      _b_param(default_b_param),
      _avg_field_length()
{
}

void
Bm25Blueprint::visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const
{
    for (uint32_t i = 0; i < env.getNumFields(); ++i) {
        const auto* field = env.getField(i);
        if (field->type() == FieldType::INDEX) {
            FeatureNameBuilder fnb;
            fnb.baseName(getBaseName()).parameter(field->name());
            visitor.visitDumpFeature(fnb.buildName());
        }
    }
}

fef::Blueprint::UP
Bm25Blueprint::createInstance() const
{
    return std::make_unique<Bm25Blueprint>();
}

bool
Bm25Blueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params)
{
    const auto& field_name = params[0].getValue();
    _field = env.getFieldByName(field_name);

    if (lookup_param(env.getProperties(), "k1", _k1_param) == Trinary::Undefined) {
        return false;
    }
    if (lookup_param(env.getProperties(), "b", _b_param) == Trinary::Undefined) {
        return false;
    }
    if (lookup_param(env.getProperties(), "averageFieldLength", _avg_field_length) == Trinary::Undefined) {
        return false;
    }

    describeOutput("score", "The bm25 score for all terms searching in the given index field");
    return (_field != nullptr);
}

namespace {

std::string
make_avg_field_length_key(const std::string& base_name, const std::string& field_name)
{
    return base_name + ".afl." + field_name;
}

}

void
Bm25Blueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    std::string key = make_avg_field_length_key(getBaseName(), _field->name());
    if (store.get(key) == nullptr) {
        double avg_field_length = _avg_field_length.value_or(env.get_average_field_length(_field->name()));
        store.add(key, std::make_unique<AnyWrapper<double>>(avg_field_length));
    }
}

fef::FeatureExecutor&
Bm25Blueprint::createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const
{
    const auto* lookup_result = env.getObjectStore().get(make_avg_field_length_key(getBaseName(), _field->name()));
    double avg_field_length = lookup_result != nullptr ?
                              as_value<double>(*lookup_result) :
                              _avg_field_length.value_or(env.get_average_field_length(_field->name()));
    return stash.create<Bm25Executor>(*_field, env, avg_field_length, _k1_param, _b_param);
}

}
