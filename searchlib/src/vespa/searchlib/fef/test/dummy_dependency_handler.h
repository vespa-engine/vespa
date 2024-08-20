// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/eval/eval/value_type.h>
#include <map>
#include <string>
#include <vector>

namespace search::fef::test {

/**
 * A very simple blueprint dependency resolver that will keep track of
 * inputs and outputs for a single blueprint.
 **/
struct DummyDependencyHandler : public Blueprint::DependencyHandler
{
    Blueprint                             &blueprint;
    std::map<std::string,FeatureType> object_type_map;
    bool                                   accept_type_mismatch;
    std::vector<std::string>          input;
    std::vector<Blueprint::AcceptInput>    accept_input;
    std::vector<std::string>          output;
    std::vector<FeatureType>               output_type;
    std::string                       fail_msg;

    explicit DummyDependencyHandler(Blueprint &blueprint_in);
    ~DummyDependencyHandler();
    void define_object_input(const std::string &name, const vespalib::eval::ValueType &type);
    std::optional<FeatureType> resolve_input(const std::string &feature_name, Blueprint::AcceptInput accept_type) override;
    void define_output(const std::string &output_name, FeatureType type) override;
    void fail(const std::string &msg) override;
};

}
