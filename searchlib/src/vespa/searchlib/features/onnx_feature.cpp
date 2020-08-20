// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_feature.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/tensor/dense/onnx_wrapper.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.onnx_feature");

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::FeatureType;
using search::fef::IIndexEnvironment;
using search::fef::IQueryEnvironment;
using search::fef::ParameterList;
using vespalib::Stash;
using vespalib::eval::ValueType;
using vespalib::make_string_short::fmt;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::tensor::OnnxWrapper;

namespace search::features {

/**
 * Feature executor that evaluates an onnx model
 */
class OnnxFeatureExecutor : public FeatureExecutor
{
private:
    const OnnxWrapper                    &_model;
    OnnxWrapper::Params                   _params;
    OnnxWrapper::Result                   _result;
    std::vector<MutableDenseTensorView>   _views;

public:
    OnnxFeatureExecutor(const OnnxWrapper &model)
        : _model(model), _params(), _result(OnnxWrapper::Result::make_empty()), _views()
    {
        _views.reserve(_model.outputs().size());
        for (const auto &output: _model.outputs()) {
            _views.emplace_back(output.make_compatible_type());
        }
    }
    bool isPure() override { return true; }
    void execute(uint32_t) override {       
        _params = OnnxWrapper::Params();
        for (size_t i = 0; i < _model.inputs().size(); ++i) {
            _params.bind(i, static_cast<const DenseTensorView&>(inputs().get_object(i).get()));
        }
        _result = _model.eval(_params);
        for (size_t i = 0; i < _model.outputs().size(); ++i) {
            _result.get(i, _views[i]);
            outputs().set_object(i, _views[i]);
        }
    }
};

OnnxBlueprint::OnnxBlueprint()
    : Blueprint("onnxModel"),
      _model(nullptr)
{
}

OnnxBlueprint::~OnnxBlueprint() = default;

bool
OnnxBlueprint::setup(const IIndexEnvironment &env,
                     const ParameterList &params)
{
    auto optimize = (env.getFeatureMotivation() == env.FeatureMotivation::VERIFY_SETUP)
                    ? OnnxWrapper::Optimize::DISABLE
                    : OnnxWrapper::Optimize::ENABLE;

    // Note: Using the fileref property with the model name as
    // fallback to get a file name. This needs to be replaced with an
    // actual file reference obtained through config when available.
    vespalib::string file_name = env.getProperties().lookup(getName(), "fileref").get(params[0].getValue());
    try {
        _model = std::make_unique<OnnxWrapper>(file_name, optimize);
    } catch (std::exception &ex) {
        return fail("Model setup failed: %s", ex.what());
    }
    for (size_t i = 0; i < _model->inputs().size(); ++i) {
        const auto &model_input = _model->inputs()[i];
        if (auto maybe_input = defineInput(fmt("rankingExpression(\"%s\")", model_input.name.c_str()), AcceptInput::OBJECT)) {
            const FeatureType &feature_input = maybe_input.value();
            assert(feature_input.is_object());
            if (!model_input.is_compatible(feature_input.type())) {
                return fail("incompatible type for input '%s': %s -> %s", model_input.name.c_str(),
                            feature_input.type().to_spec().c_str(), model_input.type_as_string().c_str());
            }
        }
    }
    for (size_t i = 0; i < _model->outputs().size(); ++i) {
        const auto &model_output = _model->outputs()[i];
        ValueType output_type = model_output.make_compatible_type();
        if (output_type.is_error()) {
            return fail("unable to make compatible type for output '%s': %s -> error",
                        model_output.name.c_str(), model_output.type_as_string().c_str());
        }
        describeOutput(model_output.name, "output from onnx model", FeatureType::object(output_type));
    }
    return true;
}

FeatureExecutor &
OnnxBlueprint::createExecutor(const IQueryEnvironment &, Stash &stash) const
{
    assert(_model);
    return stash.create<OnnxFeatureExecutor>(*_model);
}

}
