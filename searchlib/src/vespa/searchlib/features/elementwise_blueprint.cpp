// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementwise_blueprint.h"
#include "elementwise_bm25_blueprint.h"
#include "elementwise_utils.h"
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/searchlib/fef/featurenameparser.h>
#include <vespa/searchlib/fef/parametervalidator.h>

using vespalib::eval::CellType;

namespace search::features {

using fef::FeatureNameParser;
using fef::ParameterDescriptions;
using fef::ParameterValidator;

namespace {

class DependencyHandlerGuard {
    fef::Blueprint& _blueprint;

public:
    DependencyHandlerGuard(fef::Blueprint& blueprint, fef::Blueprint::DependencyHandler* handler);
    ~DependencyHandlerGuard();
};

DependencyHandlerGuard::DependencyHandlerGuard(fef::Blueprint& blueprint, fef::Blueprint::DependencyHandler* handler)
    : _blueprint(blueprint)
{
    if (handler != nullptr) {
        _blueprint.attach_dependency_handler(*handler);
    }
}

DependencyHandlerGuard::~DependencyHandlerGuard()
{
    _blueprint.detach_dependency_handler();
}

}

ElementwiseBlueprint::ElementwiseBlueprint()
    : ElementwiseBlueprint(make_default_nested_blueprints())
{
}

ElementwiseBlueprint::ElementwiseBlueprint(NestedBlueprints nested_blueprints)
    : fef::Blueprint(ElementwiseUtils::elementwise_feature_base_name()),
      _inner_blueprint(),
      _nested_blueprints(std::move(nested_blueprints))
{
}

ElementwiseBlueprint::~ElementwiseBlueprint() = default;

ElementwiseBlueprint::NestedBlueprints
ElementwiseBlueprint::make_default_nested_blueprints()
{
    auto nested_blueprints = std::make_shared<NestedBlueprints::element_type>();
    nested_blueprints->emplace("bm25", std::make_shared<ElementwiseBm25Blueprint>());
    return nested_blueprints;
}

void
ElementwiseBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const
{
}

std::unique_ptr<fef::Blueprint>
ElementwiseBlueprint::createInstance() const
{
    return std::make_unique<ElementwiseBlueprint>(_nested_blueprints);
}

fef::ParameterDescriptions
ElementwiseBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().
        desc().feature().string().
        desc().feature().string().string();
}

bool
ElementwiseBlueprint::setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params)
{
    const auto& feature_name = params[0].getValue();
    const auto& dim_name = params[1].getValue();
    std::optional<CellType> cell_type;
    if (params.size() > 2) {
        const auto& cell_type_name = params[2].getValue();
        cell_type = vespalib::eval::value_type::cell_type_from_name(cell_type_name);
        if (!cell_type.has_value()) {
            return fail("'%s' is not a valid tensor cell type", cell_type_name.c_str());
        }
    } else {
        cell_type.emplace(CellType::DOUBLE);
    }
    FeatureNameParser feature_name_parser(feature_name);
    if (!feature_name_parser.valid()) {
        return fail("'%s' is not a valid feature name", feature_name.c_str());
    }
    const auto& nested_feature_base_name = feature_name_parser.baseName();
    auto itr = _nested_blueprints->find(nested_feature_base_name);
    if (itr == _nested_blueprints->end()) {
        return fail("'%s' is not a feature with elementwise support",
                    nested_feature_base_name.c_str());
    }
    _inner_blueprint = itr->second->createInstance();
    DependencyHandlerGuard dependency_handler_guard(*_inner_blueprint, get_dependency_handler());
    StringVector nested_params = feature_name_parser.parameters();
    nested_params.emplace_back(dim_name);
    nested_params.emplace_back(vespalib::eval::value_type::cell_type_to_name(cell_type.value()));
    auto nested_descs = _inner_blueprint->getDescriptions();
    ParameterValidator validator(env, nested_params, nested_descs);
    auto result = validator.validate();
    if (!result.valid()) {
        return fail("The parameter list used for setting up %s for %s is not valid: %s",
                    nested_feature_base_name.c_str(), getBaseName().c_str(), result.getError().c_str());
    }
    return _inner_blueprint->setup(env, result.getParameters());
}

void
ElementwiseBlueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    _inner_blueprint->prepareSharedState(env, store);
}

fef::FeatureExecutor&
ElementwiseBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return _inner_blueprint->createExecutor(env, stash);
}

}
