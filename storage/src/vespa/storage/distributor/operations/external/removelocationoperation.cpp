// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removelocationoperation.h"
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/document/bucket/bucketselector.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/select/parser.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.doc.removelocation");


using namespace storage::distributor;
using namespace storage;
using document::BucketSpace;

RemoveLocationOperation::RemoveLocationOperation(
        DistributorComponent& manager,
        DistributorBucketSpace &bucketSpace,
        const std::shared_ptr<api::RemoveLocationCommand> & msg,
        PersistenceOperationMetricSet& metric)
    : Operation(),
      _trackerInstance(metric,
               std::shared_ptr<api::BucketInfoReply>(new api::RemoveLocationReply(*msg)),
               manager,
               0),
      _tracker(_trackerInstance),
      _msg(msg),
      _manager(manager),
      _bucketSpace(bucketSpace)
{}

RemoveLocationOperation::~RemoveLocationOperation() {}

int
RemoveLocationOperation::getBucketId(
        DistributorComponent& manager,
        const api::RemoveLocationCommand& cmd, document::BucketId& bid)
{
        std::shared_ptr<const document::DocumentTypeRepo> repo =
            manager.getTypeRepo();
        document::select::Parser parser(
                *repo, manager.getBucketIdFactory());

    document::BucketSelector bucketSel(manager.getBucketIdFactory());
    std::unique_ptr<document::BucketSelector::BucketVector> exprResult
        = bucketSel.select(*parser.parse(cmd.getDocumentSelection()));

    if (!exprResult.get()) {
        return 0;
    } else if (exprResult->size() != 1) {
        return exprResult->size();
    } else {
        bid = (*exprResult)[0];
        return 1;
    }
}

void
RemoveLocationOperation::onStart(DistributorMessageSender& sender)
{
    document::BucketId bid;
    int count = getBucketId(_manager, *_msg, bid);

    if (count != 1) {
        _tracker.fail(sender,
                      api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS,
                                      "Document selection could not be mapped to a single location"));
    }

    std::vector<BucketDatabase::Entry> entries;
    _bucketSpace.getBucketDatabase().getAll(bid, entries);

    bool sent = false;
    for (uint32_t j = 0; j < entries.size(); ++j) {
        const BucketDatabase::Entry& e = entries[j];

        std::vector<uint16_t> nodes = e->getNodes();

        for (uint32_t i = 0; i < nodes.size(); i++) {
            std::shared_ptr<api::RemoveLocationCommand> command(
                    new api::RemoveLocationCommand(
                            _msg->getDocumentSelection(),
                            document::Bucket(_msg->getBucket().getBucketSpace(), e.getBucketId())));

            copyMessageSettings(*_msg, *command);
            _tracker.queueCommand(command, nodes[i]);
            sent = true;
        }
    }

    if (!sent) {
        LOG(debug,
            "Remove location %s failed since no available nodes found. "
            "System state is %s",
            _msg->toString().c_str(),
            _bucketSpace.getClusterState().toString().c_str());

        _tracker.fail(sender, api::ReturnCode(api::ReturnCode::OK));
    } else {
        _tracker.flushQueue(sender);
    }
};


void
RemoveLocationOperation::onReceive(
        DistributorMessageSender& sender,
        const std::shared_ptr<api::StorageReply> & msg)
{
    _tracker.receiveReply(sender, static_cast<api::BucketInfoReply&>(*msg));
}

void
RemoveLocationOperation::onClose(DistributorMessageSender& sender)
{
    _tracker.fail(sender, api::ReturnCode(api::ReturnCode::ABORTED,
                                          "Process is shutting down"));
}
