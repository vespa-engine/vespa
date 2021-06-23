// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_models.h"
#include <assert.h>

namespace proton::matching {

OnnxModels::OnnxModels()
    : _models()
{
}

OnnxModels::~OnnxModels() = default;

OnnxModels::OnnxModels(const Vector &models)
    : _models()
{
    for (const auto &model: models) {
        _models.emplace(model.name(), model);
    }
}

bool
OnnxModels::operator==(const OnnxModels &rhs) const
{
    return (_models == rhs._models);
}

const OnnxModels::Model *
OnnxModels::getModel(const vespalib::string &name) const
{
    auto itr = _models.find(name);
    if (itr != _models.end()) {
        return &itr->second;
    }
    return nullptr;
}

void
OnnxModels::configure(const ModelConfig &config, Model &model)
{
    assert(config.name == model.name());
    for (const auto &input: config.input) {
        model.input_feature(input.name, input.source);
    }
    for (const auto &output: config.output) {
        model.output_name(output.name, output.as);
    }
    model.dry_run_on_setup(config.dryRunOnSetup);
}

}
