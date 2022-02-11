// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eval_onnx.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.test.eval_onnx");

namespace vespalib::eval::test {

std::vector<TensorSpec> eval_onnx(const Onnx &model, const std::vector<TensorSpec> &params) {
    if (params.size() != model.inputs().size()) {
        LOG(error, "model with %zu inputs run with %zu parameters", model.inputs().size(), params.size());
        return {}; // wrong number of parameters
    }
    Onnx::WirePlanner planner;
    for (size_t i = 0; i < model.inputs().size(); ++i) {
        if (!planner.bind_input_type(ValueType::from_spec(params[i].type()), model.inputs()[i])) {
            LOG(error, "unable to bind input type: %s -> %s", params[i].type().c_str(), model.inputs()[i].type_as_string().c_str());
            return {}; // inconsistent input types
        }
    }
    planner.prepare_output_types(model);
    for (size_t i = 0; i < model.outputs().size(); ++i) {
        if (planner.make_output_type(model.outputs()[i]).is_error()) {
            LOG(error, "unable to make output type: %s -> error", model.outputs()[i].type_as_string().c_str());
            return {}; // unable to infer/probe output type
        }
    }
    planner.prepare_output_types(model);
    auto wire_info = planner.get_wire_info(model);
    try {
        Onnx::EvalContext context(model, wire_info);
        std::vector<Value::UP> inputs;
        for (const auto &param: params) {
            inputs.push_back(value_from_spec(param, FastValueBuilderFactory::get()));
        }
        for (size_t i = 0; i < model.inputs().size(); ++i) {
            context.bind_param(i, *inputs[i]);
        }
        context.eval();
        std::vector<TensorSpec> results;
        for (size_t i = 0; i < model.outputs().size(); ++i) {
            results.push_back(spec_from_value(context.get_result(i)));
        }
        return results;
    } catch (const Ort::Exception &ex) {
        LOG(error, "model run failed: %s", ex.what());
        return {}; // evaluation failed
    }
}

} // namespace
