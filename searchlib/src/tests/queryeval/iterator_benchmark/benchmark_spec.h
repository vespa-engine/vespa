// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/*
 * Building trees.
 */

#include "benchmark_blueprint_factory.h"
#include "common.h"

#include <concepts>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>

namespace search::queryeval::test {

// ----------------- Node specs ------------------------

struct Spec;

struct TermSpec {
    FieldConfig field;
    double hit_ratio;
};

struct AndSpec {
    std::vector<Spec> children;
};

struct OrSpec {
    std::vector<Spec> children;
};

struct Spec {
    std::variant<TermSpec, AndSpec, OrSpec> node;
};

template <typename... Fs>
struct overloaded : Fs... { using Fs::operator()...; };

template <typename... Fs> overloaded(Fs...) -> overloaded<Fs...>;

// ----------------- Builders ------------------------

Spec term(FieldConfig field, double hit_ratio);

template <typename NodeSpec, typename... Specs>
    requires (std::same_as<std::remove_cvref_t<Specs>, Spec> && ...)
Spec make_intermediate(Specs&&... children) {
    std::vector<Spec> v;
    v.reserve(sizeof...(children));
    (v.push_back(std::forward<Specs>(children)), ...);
    return Spec{NodeSpec{std::move(v)}};
}

template <typename... Specs>
Spec and_(Specs&&... children) {
    return make_intermediate<AndSpec>(std::forward<Specs>(children)...);
}

template <typename... Specs>
Spec or_(Specs&&... children) {
    return make_intermediate<OrSpec>(std::forward<Specs>(children)...);
}

std::string to_string(const Spec& s);

// ----------------- Factory ------------------------

class SpecBlueprintFactory : public BenchmarkBlueprintFactory {
public:
    using FactoryList = std::vector<std::unique_ptr<BenchmarkBlueprintFactory>>;

private:
    Spec        _spec;
    uint32_t    _num_docs;
    FactoryList _leaf_factories;

public:
    SpecBlueprintFactory(Spec spec, uint32_t num_docs);
    ~SpecBlueprintFactory() override;

    std::unique_ptr<Blueprint> make_blueprint() override;
    std::string get_name(Blueprint& blueprint) const override;

private:
    static void collect_leaves(const Spec& spec, uint32_t num_docs, FactoryList& out);
    static std::unique_ptr<Blueprint> build_tree(const Spec& spec, FactoryList& leaves, size_t& leaf_idx,
                                                 uint32_t docid_limit);
};

}
