// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "external_data_value_factory.h"
#include "external_data_value.h"
#include "basic_value.h"

namespace vespalib::slime {

Value *
ExternalDataValueFactory::create(Stash &stash) const
{
    if (!input) {
        return &stash.create<BasicDataValue>(Memory(), stash);
    }
    return &stash.create<ExternalDataValue>(std::move(input));
}

} // namespace vespalib::slime
