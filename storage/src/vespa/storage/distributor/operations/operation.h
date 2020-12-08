// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/state/nodetype.h>
#include <vespa/storage/distributor/distributormessagesender.h>
#include <vespa/storageframework/generic/clock/time.h>


namespace storage {

namespace api {
    class StorageMessage;
    class StorageReply;
}

class StorageComponent;

namespace distributor {

class PendingMessageTracker;
class OperationSequencer;

class Operation
{
public:
    typedef std::shared_ptr<Operation> SP;

    Operation();

    virtual ~Operation();

    /**
       Tell the callback that storage is shutting down. Reply to any pending
       stuff.
    */
    virtual void onClose(DistributorMessageSender&) = 0;

    /**
       When a reply has been received, the storagelink will call receive()
       on the owner of the message that was replied to.
    */
    virtual void receive(DistributorMessageSender& sender,
                 const std::shared_ptr<api::StorageReply> & msg)
    {
        onReceive(sender, msg);
    }

    virtual const char* getName() const = 0;

    virtual std::string getStatus() const;

    virtual std::string toString() const {
        return std::string(getName());
    }

    /**
       Starts the callback, sending any messages etc. Sets _startTime to current time
    */
    virtual void start(DistributorMessageSender& sender, framework::MilliSecTime startTime);

    /**
     * Returns true if we are blocked to start this operation given
     * the pending messages.
     */
    virtual bool isBlocked(const PendingMessageTracker&, const OperationSequencer&) const {
        return false;
    }

    /**
       Returns the timestamp on which the first message was sent from this callback.
    */
    framework::MilliSecTime getStartTime() const { return _startTime; }

    /**
        Transfers message settings such as priority, timeout, etc. from one message to another.
    */
    static void copyMessageSettings(const api::StorageCommand& source,
                                    api::StorageCommand& target);

private:
    /**
       Implementation of start for the callback
     */
    virtual void onStart(DistributorMessageSender& sender) = 0;

    virtual void onReceive(DistributorMessageSender& sender,
                           const std::shared_ptr<api::StorageReply> & msg) = 0;

protected:
    framework::MilliSecTime _startTime;
};

}

}



