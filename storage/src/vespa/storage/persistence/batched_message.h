// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.//
#pragma once

#include "shared_operation_throttler.h"
#include <memory>
#include <utility>

namespace storage {

namespace api { class StorageMessage; }

using BatchedMessage = std::pair<std::shared_ptr<api::StorageMessage>, ThrottleToken>;

}
