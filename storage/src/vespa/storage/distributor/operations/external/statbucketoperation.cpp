// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketoperation.h"
#include <vespa/storage/distributor/distributor_stripe_component.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operations.external.stat_bucket");

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
StatBucketOperation::onClose(DistributorStripeMessageSender& sender)
{
    api::StatBucketReply* rep = (api::StatBucketReply*)_command->makeReply().release();
    rep->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Process is shutting down"));
    sender.sendReply(std::shared_ptr<api::StatBucketReply>(rep));
}

void
StatBucketOperation::onStart(DistributorStripeMessageSender& sender)
{
    std::vector<uint16_t> nodes;

    BucketDatabase::Entry entry(_bucketSpace.getBucketDatabase().get(_command->getBucketId()));

    if (entry.valid()) {
        nodes = entry->getNodes();
    }

    // If no entries exist, give empty reply
    if (nodes.size() == 0) {
        auto reply = std::make_shared<api::StatBucketReply>(*_command, "Bucket was not stored on any nodes.");
        reply->setResult(api::ReturnCode(api::ReturnCode::OK));
        sender.sendReply(reply);
    } else {
        std::vector<std::shared_ptr<api::StorageCommand>> messages;
        for (uint16_t node : nodes) {
            auto cmd = std::make_shared<api::StatBucketCommand>(_command->getBucket(), _command->getDocumentSelection());
            _sent[cmd->getMsgId()] = node;
            messages.emplace_back(std::move(cmd));
        }

        for (uint32_t i = 0; i < nodes.size(); i++) {
            sender.sendToNode(lib::NodeType::STORAGE, nodes[i], messages[i], true);
        }
    }
};

void
StatBucketOperation::onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg)
{
    assert(msg->getType() == api::MessageType::STATBUCKET_REPLY);
    auto& myreply = dynamic_cast<api::StatBucketReply&>(*msg);
    auto found = _sent.find(msg->getMsgId());

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
        for (const auto& result : _results) {
            ost << result.second;
        }
        auto reply = std::make_shared<api::StatBucketReply>(*_command, ost.str());
        sender.sendReply(reply);
    }
}

}
