// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_bm25_blueprint.h"
#include "bm25_utils.h"
#include "elementwise_bm25_executor.h"
#include "elementwise_blueprint.h"
#include "elementwise_utils.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/objectstore.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

using fef::AnyWrapper;
using fef::Blueprint;
using fef::FeatureExecutor;
using fef::FeatureNameBuilder;
using fef::FeatureType;
using fef::FieldType;
using fef::IQueryEnvironment;
using fef::objectstore::as_value;
using vespalib::Trinary;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;


namespace {

double constexpr default_k1_param = 1.2;
double constexpr default_b_param = 0.75;
std::string bm25_feature_base_name("bm25");

std::string
make_avg_element_length_key(const std::string& base_name, const std::string& field_name)
{
    return base_name + ".ael." + field_name;
}

double
get_average_element_length(const IQueryEnvironment& env, const std::string& field_name)
{
    auto info = env.get_field_length_info(field_name);
    return info.get_average_element_length();
}

}

ElementwiseBm25Blueprint::ElementwiseBm25Blueprint()
    : fef::Blueprint("elementwiseBm25"),
      _field(nullptr),
      _k1_param(default_k1_param),
      _b_param(default_b_param),
      _avg_element_length(),
      _output_tensor_type(ValueType::error_type())
{
}

ElementwiseBm25Blueprint::~ElementwiseBm25Blueprint() = default;

void
ElementwiseBm25Blueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const
{
}

std::unique_ptr<fef::Blueprint>
ElementwiseBm25Blueprint::createInstance() const
{
    return std::make_unique<ElementwiseBm25Blueprint>();
}

fef::ParameterDescriptions
ElementwiseBm25Blueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY).string().string();
}

bool
ElementwiseBm25Blueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params)
{
    _field = params[0].asField();
    auto elementwise_feature_name = ElementwiseUtils::feature_name(bm25_feature_base_name, params);
    Bm25Utils bm25_utils( elementwise_feature_name +".", env.getProperties());

    if (bm25_utils.lookup_param(Bm25Utils::k1(), _k1_param) == Trinary::Undefined) {
        return false;
    }
    if (bm25_utils.lookup_param(Bm25Utils::b(), _b_param) == Trinary::Undefined) {
        return false;
    }
    if (bm25_utils.lookup_param(Bm25Utils::average_element_length(), _avg_element_length) == Trinary::Undefined) {
        return false;
    }
    auto fail_message = ElementwiseUtils::build_output_tensor_type(_output_tensor_type, params[1].getValue(),
                                                                   params[2].getValue());
    if (fail_message.has_value()) {
        return fail("%s", fail_message.value().c_str());
    }
    _empty_output = vespalib::eval::value_from_spec(_output_tensor_type.to_spec(), FastValueBuilderFactory::get());
    FeatureType output_type = FeatureType::object(_output_tensor_type);
    describeOutput("score", "The elementwise bm25 score for all terms searching in the given index field", output_type);
    return true;
}

void
ElementwiseBm25Blueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    std::string key = make_avg_element_length_key(bm25_feature_base_name, _field->name());
    if (store.get(key) == nullptr) {
        double avg_element_length = _avg_element_length.value_or(get_average_element_length(env, _field->name()));
        store.add(key, std::make_unique<AnyWrapper<double>>(avg_element_length));
    }
}

fef::FeatureExecutor&
ElementwiseBm25Blueprint::createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const
{
    const auto* lookup_result = env.getObjectStore().get(make_avg_element_length_key(bm25_feature_base_name, _field->name()));
    double avg_element_length = lookup_result != nullptr ?
                              as_value<double>(*lookup_result) :
                              _avg_element_length.value_or(get_average_element_length(env, _field->name()));
    return stash.create<ElementwiseBm25Executor>(*_field, env, avg_element_length, _k1_param, _b_param, *_empty_output);
}

}
