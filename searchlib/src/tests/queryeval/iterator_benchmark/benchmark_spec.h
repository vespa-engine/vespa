// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/*
 * Building trees.
 */

#include "common.h"

#include <variant>
#include <vector>

namespace search::queryeval::test {

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

}
