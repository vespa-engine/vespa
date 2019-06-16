// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intrinsic_blueprint_adapter.h"
#include <vespa/searchlib/fef/fef.h>

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::FeatureNameBuilder;
using search::fef::FeatureType;

namespace search::features::rankingexpression {

namespace {

bool is_valid(const FeatureType *type) {
    if (type == nullptr) {
        return false;
    }
    if (!type->is_object()) {
        return true;
    }
    return !type->type().is_error();
}

struct IntrinsicBlueprint : IntrinsicExpression {
    Blueprint::UP blueprint;
    FeatureType type;
    IntrinsicBlueprint(Blueprint::UP blueprint_in, const FeatureType &type_in)
        : blueprint(std::move(blueprint_in)), type(type_in) {}
    vespalib::string describe_self() const override { return blueprint->getName(); }
    const FeatureType &result_type() const override { return type; }
    void prepare_shared_state(const QueryEnv & env, fef::IObjectStore & store) const override {
        blueprint->prepareSharedState(env, store);
    }
    FeatureExecutor &create_executor(const QueryEnv &env, vespalib::Stash &stash) const override {
        return blueprint->createExecutor(env, stash);
    }
};

struct ResultTypeExtractor : Blueprint::DependencyHandler {
    std::unique_ptr<FeatureType> result_type;
    bool too_much;
    ResultTypeExtractor() : result_type(), too_much(false) {}
    const FeatureType &resolve_input(const vespalib::string &, Blueprint::AcceptInput) override {
        too_much = true;
        return FeatureType::number();
    }
    void define_output(const vespalib::string &, const FeatureType &type) override {
        too_much = (too_much || bool(result_type));
        result_type = std::make_unique<FeatureType>(type);
    }
    bool valid() const { return (is_valid(result_type.get()) && !too_much); }
    const FeatureType &get() const { return *result_type; }
};

} // namespace search::features::rankingexpression::<unnamed>

IntrinsicExpression::UP
IntrinsicBlueprintAdapter::try_create(const search::fef::Blueprint &proto,
                                      const search::fef::IIndexEnvironment &env,
                                      const std::vector<vespalib::string> &params)
{
    FeatureNameBuilder name_builder;
    ResultTypeExtractor result_type;
    Blueprint::UP blueprint = proto.createInstance();
    name_builder.baseName(blueprint->getBaseName());
    for (const auto &param: params) {
        name_builder.parameter(param);
    }
    blueprint->setName(name_builder.buildName());
    blueprint->attach_dependency_handler(result_type);
    if (!blueprint->setup(env, params) || !result_type.valid()) {
        return IntrinsicExpression::UP(nullptr);
    }
    blueprint->detach_dependency_handler();
    return std::make_unique<IntrinsicBlueprint>(std::move(blueprint), result_type.get());
}

} // namespace search::features::rankingexpression
