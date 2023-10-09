// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_dependency_handler.h"

namespace search {
namespace fef {
namespace test {

DummyDependencyHandler::DummyDependencyHandler(Blueprint &blueprint_in)
    : blueprint(blueprint_in),
      object_type_map(),
      accept_type_mismatch(false),
      input(),
      accept_input(),
      output(),
      output_type(),
      fail_msg()
{
    blueprint.attach_dependency_handler(*this);
}

DummyDependencyHandler::~DummyDependencyHandler()
{
    blueprint.detach_dependency_handler();
}

void
DummyDependencyHandler::define_object_input(const vespalib::string &name, const vespalib::eval::ValueType &type)
{
    object_type_map.emplace(name, FeatureType::object(type));
}

std::optional<FeatureType>
DummyDependencyHandler::resolve_input(const vespalib::string &feature_name, Blueprint::AcceptInput accept_type)
{
    input.push_back(feature_name);
    accept_input.push_back(accept_type);
    auto pos = object_type_map.find(feature_name);
    if (pos == object_type_map.end()) {
        if (accept_type == Blueprint::AcceptInput::OBJECT) {
            accept_type_mismatch = true;
            return std::nullopt;
        }
        return FeatureType::number();
    }
    if (accept_type == Blueprint::AcceptInput::NUMBER) {
        accept_type_mismatch = true;
        return std::nullopt;
    }
    return pos->second;
}

void
DummyDependencyHandler::define_output(const vespalib::string &output_name, FeatureType type)
{
    output.push_back(output_name);
    output_type.push_back(std::move(type));
}

void
DummyDependencyHandler::fail(const vespalib::string &msg)
{
    fail_msg = msg;
}

} // namespace search::fef::test
} // namespace search::fef
} // namespace search
