// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace storage {
namespace api { class StorageMessage; }

// Interface for enqueuing a StorageMessage for further processing
class MessageEnqueuer {
public:
    virtual ~MessageEnqueuer() = default;
    virtual void enqueue(std::shared_ptr<api::StorageMessage> msg) = 0;
};

}
