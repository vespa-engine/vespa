// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/*
 * Ergonomic builder pattern over blueprint factories.
 */

#include "benchmark_blueprint_factory.h"
#include "common.h"
#include "intermediate_blueprint_factory.h"
#include <vespa/eval/eval/value.h>

namespace search::queryeval::test {

using vespalib::eval::Value;

using FactoryPtr = std::shared_ptr<BenchmarkBlueprintFactory>;

FactoryPtr enn(AttributeVector::SP attr, Value::UP query, uint32_t target_hits);

FactoryPtr term(FieldConfig field, uint32_t num_docs, uint32_t default_values_per_document, double hit_ratio);

template <class... Cs> FactoryPtr and_(Cs&&... cs) {
    auto up = std::make_unique<AndBlueprintFactory>();
    (up->add_child(std::forward<Cs>(cs)), ...);
    return up;
}

} // namespace search::queryeval::test
