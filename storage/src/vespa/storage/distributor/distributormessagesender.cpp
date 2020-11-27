// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributormessagesender.h"
#include <vespa/storageapi/messageapi/storagecommand.h>

namespace storage::distributor {

uint64_t
DistributorMessageSender::sendToNode(const lib::NodeType& nodeType, uint16_t node,
                                     const std::shared_ptr<api::StorageCommand> & cmd, bool useDocumentAPI)
{
    cmd->setSourceIndex(getDistributorIndex());
    cmd->setAddress(useDocumentAPI
                        ? api::StorageMessageAddress::createDocApi(&getClusterName(), nodeType, node)
                        : api::StorageMessageAddress::create(&getClusterName(), nodeType, node));
    uint64_t msgId = cmd->getMsgId();
    sendCommand(cmd);
    return msgId;
}

}

