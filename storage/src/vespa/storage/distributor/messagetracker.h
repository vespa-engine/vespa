// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/messagesender.h>
#include <vector>
#include <map>

namespace storage::api {
    class BucketCommand;
    class BucketReply;
}

namespace storage::distributor {

class MessageTracker {
public:
    class ToSend {
    public:
        ToSend(std::shared_ptr<api::BucketCommand> msg, uint16_t target) :
            _msg(std::move(msg)), _target(target) {};

        std::shared_ptr<api::BucketCommand> _msg;
        uint16_t _target;
    };

    MessageTracker(const std::string& clusterName);
    MessageTracker(MessageTracker &&) = default;
    MessageTracker & operator = (MessageTracker &&) = default;
    ~MessageTracker();

    void queueCommand(std::shared_ptr<api::BucketCommand> msg, uint16_t target) {
        _commandQueue.emplace_back(std::move(msg), target);
    }

    void flushQueue(MessageSender& sender);

    /**
       If the reply is for a message that is being tracked here, returns the node the message was sent to. If not, returns (uint16_t)-1
    */
    uint16_t handleReply(api::BucketReply& reply);

    /**
       Returns true if all messages sent have been received.
    */
    bool finished();

protected:
    std::vector<ToSend> _commandQueue;

    // Keeps track of which node a message was sent to.
    std::map<uint64_t, uint16_t> _sentMessages;
    std::string _clusterName;
};

}
