// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_set.h>

namespace storage::distributor::dbtransition {

using OutdatedNodes = vespalib::hash_set<uint16_t>;

}
