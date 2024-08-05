// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "benchmark_blueprint_factory.h"
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <unordered_map>

namespace search::queryeval::test {

/**
 * Factory that creates an IntermediateBlueprint (of the given type) with children created by the given factories.
 */
class IntermediateBlueprintFactory : public BenchmarkBlueprintFactory {
private:
    vespalib::string _name;
    std::vector<std::shared_ptr<BenchmarkBlueprintFactory>> _children;
    std::unordered_map<void*, char> _child_names;

    char child_name(void* blueprint) const;
protected:
    virtual std::unique_ptr<IntermediateBlueprint> make_self() const = 0;
public:
    IntermediateBlueprintFactory(std::string_view name);
    ~IntermediateBlueprintFactory();
    void add_child(std::shared_ptr<BenchmarkBlueprintFactory> child) {
        _children.push_back(std::move(child));
    }
    std::unique_ptr<Blueprint> make_blueprint() override;
    vespalib::string get_name(Blueprint& blueprint) const override;
};

class AndBlueprintFactory : public IntermediateBlueprintFactory {
protected:
    std::unique_ptr<IntermediateBlueprint> make_self() const override;
public:
    AndBlueprintFactory();
};

class SourceBlenderBlueprintFactory : public IntermediateBlueprintFactory
{
private:
    FixedSourceSelector _selector;
protected:
    std::unique_ptr<IntermediateBlueprint> make_self() const override;
public:
    SourceBlenderBlueprintFactory();
    void init_selector(auto f, uint32_t limit) {
        for (uint32_t i = 0; i < limit; ++i) {
            _selector.setSource(i, f(i));
        }
    }
};

}
