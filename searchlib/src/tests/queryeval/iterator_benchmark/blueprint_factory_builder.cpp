// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint_factory_builder.h"

#include "leaf_blueprint_factory.h"

namespace search::queryeval::test {

FactoryPtr enn(const EnnConfig& cfg) {
    return std::make_unique<EnnBlueprintFactory>(cfg);
}

FactoryPtr term(FieldConfig field, uint32_t num_docs, uint32_t default_values_per_document, double hit_ratio) {
    return make_blueprint_factory(field, QueryOperator::Term, num_docs, default_values_per_document, hit_ratio, 1,
                                  false);
}

} // namespace search::queryeval::test
