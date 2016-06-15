// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".intermediatenodes");

#include "intermediatenodes.h"

namespace search {
namespace query {

And::~And() {}

AndNot::~AndNot() {}

Or::~Or() {}

WeakAnd::~WeakAnd() {}

Equiv::~Equiv() {}

Rank::~Rank() {}

Near::~Near() {}

ONear::~ONear() {}

Phrase::~Phrase() {}

WeightedSetTerm::~WeightedSetTerm() {}

DotProduct::~DotProduct() {}

WandTerm::~WandTerm() {}

}  // namespace query
}  // namespace search
