// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "outdated_nodes.h"
#include <vespa/document/bucket/bucketspace.h>
#include <unordered_map>

namespace storage::distributor::dbtransition {

using OutdatedNodesMap = std::unordered_map<document::BucketSpace, OutdatedNodes, document::BucketSpace::hash>;

}
