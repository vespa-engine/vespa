// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_factory.h"
#include "external_memory.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib::slime {

/**
 * Value factory for data values using external memory.
 **/
struct ExternalDataValueFactory : public ValueFactory {
    mutable ExternalMemory::UP input;
    ExternalDataValueFactory(ExternalMemory::UP in) : input(std::move(in)) {}
    Value *create(Stash &stash) const override;
};

} // namespace vespalib::slime
