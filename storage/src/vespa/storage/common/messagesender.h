// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::MessageSender
 * @ingroup common
 *
 * @brief Interface to implement for classes which send messages on for others.
 *
 * Used for instance by filestor manager. Filestor threads needs to send
 * messages through the filemanager. The filestor manager thus implements this
 * interface and gives to the filestor thread.
 *
 * @author H�kon Humberset
 * @date 2006-03-22
 * @version $Id$
 */

#pragma once

#include <memory>

namespace storage::api {
    class StorageCommand;
    class StorageReply;
    class StorageMessage;
}

namespace storage {

struct MessageSender {
    virtual ~MessageSender() = default;

    virtual void sendCommand(const std::shared_ptr<api::StorageCommand>&) = 0;
    virtual void sendReply(const std::shared_ptr<api::StorageReply>&) = 0;
    // By calling this you certify that it can continue in same thread or be dispatched.
    virtual void sendReplyDirectly(const std::shared_ptr<api::StorageReply>&);

    void send(const std::shared_ptr<api::StorageMessage>&);
};

struct ChainedMessageSender {
    virtual ~ChainedMessageSender() = default;
    virtual void sendUp(const std::shared_ptr<api::StorageMessage>&) = 0;
    virtual void sendDown(const std::shared_ptr<api::StorageMessage>&) = 0;
};

/**
 * Interface to send messages "up" that bypasses message tracking.
 */
class NonTrackingMessageSender {
public:
    virtual ~NonTrackingMessageSender() = default;
    virtual void send_up_without_tracking(const std::shared_ptr<api::StorageMessage>&) = 0;
};

} // storage
