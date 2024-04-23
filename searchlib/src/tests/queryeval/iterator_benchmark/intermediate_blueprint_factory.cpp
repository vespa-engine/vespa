// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprint_factory.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <iomanip>
#include <sstream>

namespace search::queryeval::test {

template <typename BlueprintType>
char
IntermediateBlueprintFactory<BlueprintType>::child_name(void* blueprint) const
{
    auto itr = _child_names.find(blueprint);
    if (itr != _child_names.end()) {
        return itr->second;
    }
    return '?';
}

template <typename BlueprintType>
IntermediateBlueprintFactory<BlueprintType>::IntermediateBlueprintFactory(vespalib::stringref name)
    : _name(name),
      _children(),
      _child_names()
{
}

template <typename BlueprintType>
IntermediateBlueprintFactory<BlueprintType>::~IntermediateBlueprintFactory() = default;

template <typename BlueprintType>
std::unique_ptr<Blueprint>
IntermediateBlueprintFactory<BlueprintType>::make_blueprint()
{
    auto res = std::make_unique<BlueprintType>();
    _child_names.clear();
    char name = 'A';
    for (const auto& factory : _children) {
        auto child = factory->make_blueprint();
        _child_names[child.get()] = name++;
        res->addChild(std::move(child));
    }
    return res;
}

template <typename BlueprintType>
vespalib::string
IntermediateBlueprintFactory<BlueprintType>::get_name(Blueprint& blueprint) const
{
    auto* intermediate = blueprint.asIntermediate();
    if (intermediate != nullptr) {
        std::ostringstream oss;
        bool first = true;
        oss << _name << "[";
        for (size_t i = 0; i < intermediate->childCnt(); ++i) {
            auto* child = &intermediate->getChild(i);
            oss << (first ? "" : ",") << child_name(child) << ".";
            if (child->strict()) {
                oss << "s(" << std::setw(6) << std::setprecision(3) << child->strict_cost() << ")";
            } else {
                oss << "n(" << std::setw(6) << std::setprecision(3) << child->cost() << ")";
            }
            first = false;
        }
        oss << "]";
        return oss.str();
    }
    return get_class_name(blueprint);
}

template class IntermediateBlueprintFactory<AndBlueprint>;

AndBlueprintFactory::AndBlueprintFactory()
    : IntermediateBlueprintFactory<AndBlueprint>("AND")
{}

}

