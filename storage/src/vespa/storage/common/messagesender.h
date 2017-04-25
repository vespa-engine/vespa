// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

namespace storage {
namespace api {
    class StorageCommand;
    class StorageReply;
    class StorageMessage;
}

struct MessageSender {
    virtual ~MessageSender() {}

    virtual void sendCommand(const std::shared_ptr<api::StorageCommand>&) = 0;
    virtual void sendReply(const std::shared_ptr<api::StorageReply>&) = 0;

    void send(const std::shared_ptr<api::StorageMessage>&);
};

struct ChainedMessageSender {
    virtual ~ChainedMessageSender() {}
    virtual void sendUp(const std::shared_ptr<api::StorageMessage>&) = 0;
    virtual void sendDown(const std::shared_ptr<api::StorageMessage>&) = 0;
};

} // storage
