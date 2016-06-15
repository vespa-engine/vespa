// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::MessageDispatcher
 * @ingroup storageserver
 *
 * @brief Sends messages through to multiple hosts.
 *
 * In VDS, some messages are sent to the first storage node, and the node itself
 * should send the request on to another storage node and so on (put/remove).
 * This link is responsible for receiving such messages, and send it through to
 * next host, as well as through to the local host, wait for both responses and
 * reply back. If one of the responses fails, it should issue a revert command.
 *
 * @author H�kon Humberset
 * @date 2006-01-16
 * @version $Id$
 */

#pragma once

#include <vespa/vespalib/util/sync.h>
#include <map>
#include <vdslib/state/systemstate.h>
#include <vespa/storage/common/storagelink.h>

namespace storage {
namespace api {
    class BucketId;
    class ChainedCommand;
    class ChainedReply;
}

class MessageDispatcher : public StorageLink {
    mutable vespalib::Lock _access;
    typedef std::pair<std::shared_ptr<api::ChainedReply>, uint32_t> ReplyPair;
    std::map<api::StorageMessage::Id, std::shared_ptr<ReplyPair> > _cache;
    lib::ClusterState _systemState;
    StorageServerInterface& _server;

public:
    explicit MessageDispatcher(StorageServerInterface& server);
    ~MessageDispatcher();

    virtual void onClose();

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    class Factory : public StorageLink::Factory {
    public:
        std::unique_ptr<StorageLink> create(const std::string& configId,
                                          StorageServerInterface& server) const
        {
            (void) configId;
            return std::unique_ptr<StorageLink>(new MessageDispatcher(server));
        }
    };

private:

    bool onDown(const std::shared_ptr<api::StorageMessage> & msg);
    bool onUp(const std::shared_ptr<api::StorageMessage> & msg);

    bool handleCommand(const std::shared_ptr<api::ChainedCommand>& cmd);
    bool handleReply(const std::shared_ptr<api::ChainedReply>& reply,
                     bool localSource);

    bool isCorrectDistributor(const document::BucketId& id, uint16_t distributor,
                              uint16_t& expected);

};

} // storage


