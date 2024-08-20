// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <optional>
#include <string>

namespace search::fef {

/**
 * Class containing configuration for a single onnx model setup. This
 * class is used both by the IIndexEnvironment api as well as the
 * OnnxModels config adapter.
 **/
class OnnxModel {
private:
    std::string _name;
    std::string _file_path;
    std::map<std::string,std::string> _input_features;
    std::map<std::string,std::string> _output_names;
    bool _dry_run_on_setup;

public:
    OnnxModel(const std::string &name_in,
              const std::string &file_path_in);
    OnnxModel(OnnxModel &&) noexcept;
    OnnxModel & operator=(OnnxModel &&) noexcept;
    OnnxModel(const OnnxModel &) = delete;
    OnnxModel & operator =(const OnnxModel &) = delete;
    ~OnnxModel();

    const std::string &name() const { return _name; }
    const std::string &file_path() const { return _file_path; }
    OnnxModel &input_feature(const std::string &model_input_name, const std::string &input_feature);
    OnnxModel &output_name(const std::string &model_output_name, const std::string &output_name);
    OnnxModel &dry_run_on_setup(bool value);
    std::optional<std::string> input_feature(const std::string &model_input_name) const;
    std::optional<std::string> output_name(const std::string &model_output_name) const;
    bool dry_run_on_setup() const { return _dry_run_on_setup; }
    bool operator==(const OnnxModel &rhs) const;
    const std::map<std::string,std::string> &inspect_input_features() const { return _input_features; }
    const std::map<std::string,std::string> &inspect_output_names() const { return _output_names; }
};

}
