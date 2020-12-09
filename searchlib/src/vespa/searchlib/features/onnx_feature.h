// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/eval/onnx/onnx_wrapper.h>

namespace search::features {

/**
 * Blueprint for the ranking feature used to evaluate an onnx model.
 **/
class OnnxBlueprint : public fef::Blueprint {
private:
    using Onnx = vespalib::eval::Onnx;
    std::unique_ptr<Onnx> _model;
    Onnx::WireInfo _wire_info;
public:
    OnnxBlueprint();
    ~OnnxBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const override {}
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new OnnxBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
