// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basic_value_factory.h"
#include "basic_value.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib::slime {

Value *
BoolValueFactory::create(Stash & stash) const {
    return & stash.create<BasicBoolValue>(input);
}

Value *
LongValueFactory::create(Stash & stash) const {
    return & stash.create<BasicLongValue>(input);
}

Value *
DoubleValueFactory::create(Stash & stash) const {
    return & stash.create<BasicDoubleValue>(input);
}

Value *
StringValueFactory::create(Stash & stash) const {
    return & stash.create<BasicStringValue>(input, stash);
}

Value *
DataValueFactory::create(Stash & stash) const {
    return & stash.create<BasicDataValue>(input, stash);
}

}
