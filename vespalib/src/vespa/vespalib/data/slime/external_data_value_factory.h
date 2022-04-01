// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include <memory>

namespace vespalib::slime {

struct ExternalMemory;

/**
 * Value factory for data values using external memory.
 **/
struct ExternalDataValueFactory : public ValueFactory {
    mutable std::unique_ptr<ExternalMemory> input;
    ExternalDataValueFactory(std::unique_ptr<ExternalMemory> in) : input(std::move(in)) {}
    ~ExternalDataValueFactory() override;
    Value *create(Stash &stash) const override;
};

} // namespace vespalib::slime
