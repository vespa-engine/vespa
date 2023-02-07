// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/state/nodetype.h>
#include <vespa/storage/distributor/distributormessagesender.h>
#include <vespa/vespalib/util/time.h>

namespace storage {

namespace api {
    class StorageMessage;
    class StorageReply;
}

class StorageComponent;

namespace distributor {

class DistributorStripeOperationContext;
class PendingMessageTracker;
class OperationSequencer;

class Operation
{
public:
    using SP = std::shared_ptr<Operation>;

    Operation();

    virtual ~Operation();

    /**
       Tell the callback that storage is shutting down. Reply to any pending
       stuff.
    */
    virtual void onClose(DistributorStripeMessageSender&) = 0;

    /**
       When a reply has been received, the storagelink will call receive()
       on the owner of the message that was replied to.
    */
    virtual void receive(DistributorStripeMessageSender& sender,
                 const std::shared_ptr<api::StorageReply> & msg)
    {
        onReceive(sender, msg);
    }

    [[nodiscard]] virtual const char* getName() const noexcept = 0;

    [[nodiscard]] virtual std::string getStatus() const;

    [[nodiscard]] virtual std::string toString() const {
        return getName();
    }

    /**
       Starts the callback, sending any messages etc. Sets _startTime to current time
    */
    virtual void start(DistributorStripeMessageSender& sender, vespalib::system_time startTime);
    void start(DistributorStripeMessageSender& sender);

    /**
     * Returns true if we are blocked to start this operation given
     * the pending messages.
     */
    [[nodiscard]] virtual bool isBlocked(const DistributorStripeOperationContext&, const OperationSequencer&) const {
        return false;
    }

    /*
     * Called by blocking operation starter if operation was blocked
     */
    virtual void on_blocked();

    /*
     * Called by throttling operation starter if operation was throttled
     */
    virtual void on_throttled();

    /**
        Transfers message settings such as priority, timeout, etc. from one message to another.
    */
    static void copyMessageSettings(const api::StorageCommand& source,
                                    api::StorageCommand& target);

private:
    /**
       Implementation of start for the callback
     */
    virtual void onStart(DistributorStripeMessageSender& sender) = 0;

    virtual void onReceive(DistributorStripeMessageSender& sender,
                           const std::shared_ptr<api::StorageReply> & msg) = 0;

protected:
    static constexpr vespalib::duration MAX_TIMEOUT = 3600s;
    vespalib::system_time _startTime;
};

}

}



