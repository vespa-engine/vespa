// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_models.h"
#include <cassert>

namespace proton::matching {

OnnxModels::OnnxModels() = default;
OnnxModels::OnnxModels(OnnxModels &&) noexcept = default;
OnnxModels::~OnnxModels() = default;

OnnxModels::OnnxModels(Vector models)
    : _models()
{
    for (auto &model: models) {
        _models.emplace(model.name(), std::move(model));
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
