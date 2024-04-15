// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_blueprint_factory.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <unordered_map>

namespace search::queryeval::test {

/**
 * Factory that creates an IntermediateBlueprint (of the given type) with children created by the given factories.
 */
template <typename BlueprintType>
class IntermediateBlueprintFactory : public BenchmarkBlueprintFactory {
private:
    vespalib::string _name;
    std::vector<std::shared_ptr<BenchmarkBlueprintFactory>> _children;
    std::unordered_map<void*, char> _child_names;

    char child_name(void* blueprint) const;

public:
    IntermediateBlueprintFactory(vespalib::stringref name);
    ~IntermediateBlueprintFactory();
    void add_child(std::shared_ptr<BenchmarkBlueprintFactory> child) {
        _children.push_back(std::move(child));
    }
    std::unique_ptr<Blueprint> make_blueprint() override;
    vespalib::string get_name(Blueprint& blueprint) const override;
};

class AndBlueprintFactory : public IntermediateBlueprintFactory<AndBlueprint> {
public:
    AndBlueprintFactory();
};

}

