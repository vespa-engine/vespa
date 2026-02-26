// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm25_feature.h"
#include "bm25_utils.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/vespalib/util/stash.h>

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
                                    Bm25Utils::get_inverse_document_frequency(term_field, env, *term),
                                    k1_param);
            }
        }
    }
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
        if (term.tfmd->has_ranking_data(doc_id)) {
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

fef::ParameterDescriptions
Bm25Blueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
}

bool
Bm25Blueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params)
{
    const auto& field_name = params[0].getValue();
    _field = env.getFieldByName(field_name);
    if (_field == nullptr) {
        return false;
    }
    Bm25Utils bm25_utils(getBaseName() + "(" + _field->name() + ").", env.getProperties());

    if (bm25_utils.lookup_param(Bm25Utils::k1(), _k1_param) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }
    if (bm25_utils.lookup_param(Bm25Utils::b(), _b_param) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }
    if (bm25_utils.lookup_param(Bm25Utils::average_field_length(), _avg_field_length) == Trinary::Undefined) {
        return fail(bm25_utils.last_error());
    }

    describeOutput("score", "The bm25 score for all terms searching in the given index field");
    return true;
}

namespace {

std::string
make_avg_field_length_key(const std::string& base_name, const std::string& field_name)
{
    return base_name + ".afl." + field_name;
}

double
get_average_field_length(const search::fef::IQueryEnvironment& env, const std::string& field_name)
{
    auto info = env.get_field_length_info(field_name);
    return info.get_average_field_length();
}

}

void
Bm25Blueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    std::string key = make_avg_field_length_key(getBaseName(), _field->name());
    if (store.get(key) == nullptr) {
        double avg_field_length = _avg_field_length.value_or(get_average_field_length(env, _field->name()));
        store.add(key, std::make_unique<AnyWrapper<double>>(avg_field_length));
    }
}

fef::FeatureExecutor&
Bm25Blueprint::createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const
{
    const auto* lookup_result = env.getObjectStore().get(make_avg_field_length_key(getBaseName(), _field->name()));
    double avg_field_length = lookup_result != nullptr ?
                              as_value<double>(*lookup_result) :
                              _avg_field_length.value_or(get_average_field_length(env, _field->name()));
    return stash.create<Bm25Executor>(*_field, env, avg_field_length, _k1_param, _b_param);
}

}
