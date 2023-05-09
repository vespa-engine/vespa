// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "onnx_model.h"
#include <vespa/config-onnx-models.h>
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <vector>

namespace search::fef {

/**
 * Class representing a set of configured onnx models, with full path
 * for where the models are stored on disk.
 */
class OnnxModels {
public:
    using ModelConfig = vespa::config::search::core::OnnxModelsConfig::Model;
    using Model = OnnxModel;
    using Vector = std::vector<Model>;

private:
    using Map = std::map<vespalib::string, Model>;
    Map _models;

public:
    OnnxModels();
    OnnxModels(Vector models);
    OnnxModels(OnnxModels &&) noexcept;
    OnnxModels & operator=(OnnxModels &&) = delete;
    OnnxModels(const OnnxModels &) = delete;
    OnnxModels & operator =(const OnnxModels &) = delete;
    ~OnnxModels();
    bool operator==(const OnnxModels &rhs) const;
    [[nodiscard]] const Model *getModel(const vespalib::string &name) const;
    [[nodiscard]] size_t size() const { return _models.size(); }
    static void configure(const ModelConfig &config, Model &model);
};

}
