// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/eval/eval/value_type.h>
#include <map>

namespace search {
namespace fef {
namespace test {

/**
 * A very simple blueprint dependency resolver that will keep track of
 * inputs and outputs for a single blueprint.
 **/
struct DummyDependencyHandler : public Blueprint::DependencyHandler
{
    Blueprint                             &blueprint;
    std::map<vespalib::string,FeatureType> object_type_map;
    bool                                   accept_type_mismatch;
    std::vector<vespalib::string>          input;
    std::vector<Blueprint::AcceptInput>    accept_input;
    std::vector<vespalib::string>          output;
    std::vector<FeatureType>               output_type;
    vespalib::string                       fail_msg;

    explicit DummyDependencyHandler(Blueprint &blueprint_in);
    ~DummyDependencyHandler();
    void define_object_input(const vespalib::string &name, const vespalib::eval::ValueType &type);
    std::optional<FeatureType> resolve_input(const vespalib::string &feature_name, Blueprint::AcceptInput accept_type) override;
    void define_output(const vespalib::string &output_name, FeatureType type) override;
    void fail(const vespalib::string &msg) override;
};

} // namespace search::fef::test
} // namespace search::fef
} // namespace search
