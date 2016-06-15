// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/sentmessagemap.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/distributor/distributormessagesender.h>
#include <vespa/storage/distributor/operationstarter.h>

namespace storage {

namespace distributor {

class Operation;

/**
   Storage link that keeps track of running operations.
 */
class OperationOwner : public OperationStarter {
public:

    class Sender : public DistributorMessageSender {
    public:
        Sender(OperationOwner& owner,
               DistributorMessageSender& sender,
               const std::shared_ptr<Operation>& cb)
            : _owner(owner),
              _sender(sender),
              _cb(cb) 
         {}

        /**
           Sends a message.
         */
        void sendCommand(const std::shared_ptr<api::StorageCommand> &);

        /**
           Send a reply.
         */
        void sendReply(const std::shared_ptr<api::StorageReply> & msg);

        OperationOwner& getOwner() {
            return _owner;
        }

        virtual int getDistributorIndex() const {
            return _sender.getDistributorIndex();
        }
        
        virtual const std::string& getClusterName() const {
            return _sender.getClusterName();
        }

        virtual const PendingMessageTracker& getPendingMessageTracker() const {
            return _sender.getPendingMessageTracker();
        }

    private:
        OperationOwner& _owner;
        DistributorMessageSender& _sender;
        std::shared_ptr<Operation> _cb;
    };

    OperationOwner(DistributorMessageSender& sender,
                   const framework::Clock& clock)
    : _sender(sender),
      _clock(clock) {
    }
    ~OperationOwner();

    /**
       Handles replies from storage, mapping from a message id to an operation.

       If the operation was found, returns it in result.first. If the operation was
       done after the reply was processed (no more pending commands), returns true

     */
    bool handleReply(const std::shared_ptr<api::StorageReply>& reply);

    SentMessageMap& getSentMessageMap() {
        return _sentMessageMap;
    };

    virtual bool start(const std::shared_ptr<Operation>& operation,
            Priority priority);

    /**
       If the given message exists, create a reply and pass it to the
       appropriate callback.
     */
    void erase(api::StorageMessage::Id msgId);

    void onClose();

    uint32_t size() const {
        return _sentMessageMap.size();
    }

    std::string toString() const;

private:
    SentMessageMap _sentMessageMap;
    DistributorMessageSender& _sender;
    const framework::Clock& _clock;
};

}

}

