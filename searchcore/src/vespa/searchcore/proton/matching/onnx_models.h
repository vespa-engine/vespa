// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/fef/onnx_model.h>
#include <vespa/searchcore/config/config-onnx-models.h>
#include <map>
#include <vector>

namespace proton::matching {

/**
 * Class representing a set of configured onnx models, with full path
 * for where the models are stored on disk.
 */
class OnnxModels {
public:
    using ModelConfig = vespa::config::search::core::OnnxModelsConfig::Model;
    using Model = search::fef::OnnxModel;
    using Vector = std::vector<Model>;

private:
    using Map = std::map<vespalib::string, Model>;
    Map _models;

public:
    using SP = std::shared_ptr<OnnxModels>;
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
