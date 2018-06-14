// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packetwrapper.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/persistenceengine/resulthandler.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/executor.h>

namespace proton {

class FeedOperation;

/**
 * Class representing the current state of a feed handler.
 */
class FeedState {
public:
    enum Type { NORMAL, REPLAY_TRANSACTION_LOG, INIT };

private:
    Type _type;

protected:
    using FeedOperationUP = std::unique_ptr<FeedOperation>;
    void throwExceptionInReceive(const vespalib::string &docType, uint64_t serialRangeFrom,
                                 uint64_t serialRangeTo, size_t packetSize);
    void throwExceptionInHandleOperation(const vespalib::string &docType, const FeedOperation &op);

public:
    typedef std::shared_ptr<FeedState> SP;

    FeedState(Type type) : _type(type) {}
    virtual ~FeedState() {}

    Type getType() const { return _type; }
    vespalib::string getName() const;

    virtual void handleOperation(FeedToken token, FeedOperationUP op) = 0;
    virtual void receive(const PacketWrapper::SP &wrap, vespalib::Executor &executor) = 0;
};

}  // namespace proton
