// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operationstarter.h>
#include <vespa/storage/distributor/operations/operation.h>

namespace storage {
namespace distributor {

class ThrottlingOperationStarter : public OperationStarter
{
    class ThrottlingOperation : public Operation
    {
    public:
        ThrottlingOperation(const Operation::SP& operation,
                           ThrottlingOperationStarter& operationStarter)
            : _operation(operation),
              _operationStarter(operationStarter)
        {}

        ~ThrottlingOperation();
    private:
        Operation::SP _operation;
        ThrottlingOperationStarter& _operationStarter;

        ThrottlingOperation(const ThrottlingOperation&);
        ThrottlingOperation& operator=(const ThrottlingOperation&);
        
        virtual void onClose(DistributorMessageSender& sender) {
            _operation->onClose(sender);
        }
        virtual const char* getName() const {
            return _operation->getName();
        }
        virtual std::string getStatus() const {
            return _operation->getStatus();
        }
        virtual std::string toString() const {
            return _operation->toString();
        }
        virtual void start(DistributorMessageSender& sender,
                           framework::MilliSecTime startTime)
        {
            _operation->start(sender, startTime);
        }
        virtual void receive(DistributorMessageSender& sender,
                             const std::shared_ptr<api::StorageReply> & msg)
        {
            _operation->receive(sender, msg);
        }
        framework::MilliSecTime getStartTime() const {
            return _operation->getStartTime();
        }
        virtual void onStart(DistributorMessageSender&) {
            // Should never be called directly on the throttled operation
            // instance, but rather on its wrapped implementation.
            assert(false);
        }
        virtual void onReceive(DistributorMessageSender&,
                               const std::shared_ptr<api::StorageReply>&)
        {
            assert(false);
        }
    };

    OperationStarter& _starterImpl;
public:
    ThrottlingOperationStarter(OperationStarter& starterImpl)
        : _starterImpl(starterImpl),
          _minPending(0),
          _maxPending(UINT32_MAX),
          _pendingCount(0)
    {}

    virtual bool start(const std::shared_ptr<Operation>& operation,
                       Priority priority);

    bool canStart(uint32_t currentOperationCount,
                  Priority priority) const;

    void setMaxPendingRange(uint32_t minPending, uint32_t maxPending) {
        _minPending = minPending;
        _maxPending = maxPending;
    }

private:
    ThrottlingOperationStarter(const ThrottlingOperationStarter&);
    ThrottlingOperationStarter& operator=(const ThrottlingOperationStarter&);

    friend class ThrottlingOperation;
    void signalOperationFinished(const Operation& op);

    uint32_t _minPending;
    uint32_t _maxPending;
    uint32_t _pendingCount;
};

}
}

