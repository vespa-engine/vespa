// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/*
 * Building trees.
 */

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

}
