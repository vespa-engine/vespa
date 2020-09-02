// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_models.h"

namespace proton::matching {

OnnxModels::Model::Model(const vespalib::string &name_in,
                         const vespalib::string &filePath_in)
    : name(name_in),
      filePath(filePath_in)
{
}

OnnxModels::Model::~Model() = default;

bool
OnnxModels::Model::operator==(const Model &rhs) const
{
    return (name == rhs.name) &&
           (filePath == rhs.filePath);
}

OnnxModels::OnnxModels()
    : _models()
{
}

OnnxModels::~OnnxModels() = default;

OnnxModels::OnnxModels(const Vector &models)
    : _models()
{
    for (const auto &model : models) {
        _models.insert(std::make_pair(model.name, model));
    }
}

bool
OnnxModels::operator==(const OnnxModels &rhs) const
{
    return _models == rhs._models;
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

}
