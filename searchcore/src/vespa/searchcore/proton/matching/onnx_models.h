// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <vector>

namespace proton::matching {

/**
 * Class representing a set of configured onnx models, with full path
 * for where the models are stored on disk.
 */
class OnnxModels {
public:
    struct Model {
        vespalib::string name;
        vespalib::string filePath;

        Model(const vespalib::string &name_in,
              const vespalib::string &filePath_in);
        ~Model();
        bool operator==(const Model &rhs) const;
    };

    using Vector = std::vector<Model>;

private:
    using Map = std::map<vespalib::string, Model>;
    Map _models;

public:
    using SP = std::shared_ptr<OnnxModels>;
    OnnxModels();
    OnnxModels(const Vector &models);
    ~OnnxModels();
    bool operator==(const OnnxModels &rhs) const;
    const Model *getModel(const vespalib::string &name) const;
    size_t size() const { return _models.size(); }
};

}
