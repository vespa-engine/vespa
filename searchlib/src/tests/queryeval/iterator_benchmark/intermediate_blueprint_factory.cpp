// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediate_blueprint_factory.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <iomanip>
#include <sstream>

namespace search::queryeval::test {

char
IntermediateBlueprintFactory::child_name(void* blueprint) const
{
    auto itr = _child_names.find(blueprint);
    if (itr != _child_names.end()) {
        return itr->second;
    }
    return '?';
}

IntermediateBlueprintFactory::IntermediateBlueprintFactory(std::string_view name)
    : _name(name),
      _children(),
      _child_names()
{
}

IntermediateBlueprintFactory::~IntermediateBlueprintFactory() = default;

std::unique_ptr<Blueprint>
IntermediateBlueprintFactory::make_blueprint()
{
    auto res = make_self();
    _child_names.clear();
    char name = 'A';
    uint32_t source = 1;
    for (const auto& factory : _children) {
        auto child = factory->make_blueprint();
        _child_names[child.get()] = name++;
        child->setSourceId(source++); // ignored by non-source-blender blueprints
        res->addChild(std::move(child));
    }
    return res;
}

vespalib::string
IntermediateBlueprintFactory::get_name(Blueprint& blueprint) const
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

//-----------------------------------------------------------------------------

AndBlueprintFactory::AndBlueprintFactory()
  : IntermediateBlueprintFactory("AND")
{}

std::unique_ptr<IntermediateBlueprint>
AndBlueprintFactory::make_self() const
{
    return std::make_unique<AndBlueprint>();
}

//-----------------------------------------------------------------------------

SourceBlenderBlueprintFactory::SourceBlenderBlueprintFactory()
  : IntermediateBlueprintFactory("SB"),
    _selector(250, "my_source_blender", 1000)
{}

std::unique_ptr<IntermediateBlueprint>
SourceBlenderBlueprintFactory::make_self() const
{
    return std::make_unique<SourceBlenderBlueprint>(_selector);
}

}
