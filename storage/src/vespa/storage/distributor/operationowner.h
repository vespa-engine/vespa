// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sentmessagemap.h"
#include "distributormessagesender.h"
#include "operationstarter.h"
#include <vespa/storage/common/storagelink.h>

namespace storage::framework { struct Clock; }

namespace storage::distributor {

class Operation;

/**
   Storage link that keeps track of running operations.
 */
class OperationOwner : public OperationStarter {
public:

    class Sender : public DistributorStripeMessageSender {
    public:
        Sender(OperationOwner& owner,
               DistributorStripeMessageSender& sender,
               const std::shared_ptr<Operation>& cb)
            : _owner(owner),
              _sender(sender),
              _cb(cb) 
         {}

        void sendCommand(const std::shared_ptr<api::StorageCommand> &) override;
        void sendReply(const std::shared_ptr<api::StorageReply> & msg) override;

        OperationOwner& getOwner() {
            return _owner;
        }

        int getDistributorIndex() const override {
            return _sender.getDistributorIndex();
        }
        
        const ClusterContext & cluster_context() const override {
            return _sender.cluster_context();
        }

        PendingMessageTracker& getPendingMessageTracker() override {
            return _sender.getPendingMessageTracker();
        }

        const PendingMessageTracker& getPendingMessageTracker() const override {
            return _sender.getPendingMessageTracker();
        }

        const OperationSequencer& operation_sequencer() const noexcept override {
            return _sender.operation_sequencer();
        }

        OperationSequencer& operation_sequencer() noexcept override {
            return _sender.operation_sequencer();
        }

    private:
        OperationOwner& _owner;
        DistributorStripeMessageSender& _sender;
        std::shared_ptr<Operation> _cb;
    };

    OperationOwner(DistributorStripeMessageSender& sender,
                   const framework::Clock& clock)
    : _sender(sender),
      _clock(clock) {
    }
    ~OperationOwner() override;

    /**
       Handles replies from storage, mapping from a message id to an operation.

       If the operation was found, returns it in result.first. If the operation was
       done after the reply was processed (no more pending commands), returns true

     */
    bool handleReply(const std::shared_ptr<api::StorageReply>& reply);

    SentMessageMap& getSentMessageMap() {
        return _sentMessageMap;
    };

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override;

    /**
       If the given message exists, create a reply and pass it to the
       appropriate callback.
     */
    void erase(api::StorageMessage::Id msgId);

    [[nodiscard]] DistributorStripeMessageSender& sender() noexcept { return _sender; }

    void onClose();
    uint32_t size() const { return _sentMessageMap.size(); }
    std::string toString() const;

private:
    SentMessageMap _sentMessageMap;
    DistributorStripeMessageSender& _sender;
    const framework::Clock& _clock;
};

}
