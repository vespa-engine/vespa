// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/messageapi/storagemessage.h>

namespace storage::mbusprot {

class StorageMessage {
public:
    using UP = std::unique_ptr<StorageMessage>;

    virtual ~StorageMessage() = default;

    virtual api::StorageMessage::SP getInternalMessage() = 0;
    virtual api::StorageMessage::CSP getInternalMessage() const = 0;

};

}

