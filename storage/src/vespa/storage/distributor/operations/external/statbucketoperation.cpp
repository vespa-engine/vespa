// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketoperation.h"
#include <vespa/storage/distributor/distributorcomponent.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.statbucket");

namespace storage::distributor {

StatBucketOperation::StatBucketOperation(
        DistributorBucketSpace &bucketSpace,
        const std::shared_ptr<api::StatBucketCommand> & cmd)
    : Operation(),
      _bucketSpace(bucketSpace),
      _command(cmd)
{
}

StatBucketOperation::~StatBucketOperation() = default;

void
StatBucketOperation::onClose(DistributorMessageSender& sender)
{
    api::StatBucketReply* rep = (api::StatBucketReply*)_command->makeReply().release();
    rep->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
    sender.sendReply(std::shared_ptr<api::StatBucketReply>(rep));
}

void
StatBucketOperation::onStart(DistributorMessageSender& sender)
{
    std::vector<uint16_t> nodes;

    BucketDatabase::Entry entry(_bucketSpace.getBucketDatabase().get(_command->getBucketId()));

    if (entry.valid()) {
        nodes = entry->getNodes();
    }

    // If no entries exist, give empty reply
    if (nodes.size() == 0) {
        api::StatBucketReply::SP reply(new api::StatBucketReply(*_command, "Bucket was not stored on any nodes."));
        reply->setResult(api::ReturnCode(api::ReturnCode::OK));
        sender.sendReply(reply);
    } else {
        std::vector<std::shared_ptr<api::StorageCommand> > messages;
        for (uint32_t i = 0; i < nodes.size(); i++) {
            std::shared_ptr<api::StatBucketCommand> cmd(
                    new api::StatBucketCommand(
                            _command->getBucket(),
                            _command->getDocumentSelection()));

            messages.push_back(cmd);
            _sent[cmd->getMsgId()] = nodes[i];
        }

        for (uint32_t i = 0; i < nodes.size(); i++) {
            sender.sendToNode(
                       lib::NodeType::STORAGE,
                       nodes[i],
                       messages[i],
                       true);
        }
    }
};

void
StatBucketOperation::onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg)
{
    assert(msg->getType() == api::MessageType::STATBUCKET_REPLY);
    api::StatBucketReply& myreply(dynamic_cast<api::StatBucketReply&>(*msg));

    std::map<uint64_t, uint16_t>::iterator found = _sent.find(msg->getMsgId());

    if (found != _sent.end()) {
        std::ostringstream ost;
        if (myreply.getResult().getResult() == api::ReturnCode::OK) {
            ost << "\tBucket information from node " << found->second << ":\n" << myreply.getResults() << "\n\n";
        } else {
            ost << "\tBucket information retrieval failed on node " << found->second << ": " << myreply.getResult() << "\n\n";
        }
        _results[found->second] = ost.str();

        _sent.erase(found);
    }

    if (_sent.empty()) {
        std::ostringstream ost;
        for (std::map<uint16_t, std::string>::iterator iter = _results.begin();
             iter != _results.end();
             iter++) {
            ost << iter->second;
        }

        api::StatBucketReply::SP reply(new api::StatBucketReply(*_command, ost.str()));
        sender.sendReply(reply);
    }
}

}
