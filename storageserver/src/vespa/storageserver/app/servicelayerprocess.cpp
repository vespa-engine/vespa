// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayerprocess.h"
#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/searchvisitor/searchvisitor.h>

#include <vespa/log/log.h>
LOG_SETUP(".storageserver.service_layer_process");

namespace storage {

ServiceLayerProcess::ServiceLayerProcess(const config::ConfigUri & configUri)
    : Process(configUri)
{
}

ServiceLayerProcess::~ServiceLayerProcess() {}

void
ServiceLayerProcess::shutdown()
{
    Process::shutdown();
    _node.reset();
}

void
ServiceLayerProcess::createNode()
{
    _externalVisitors["searchvisitor"].reset(new SearchVisitorFactory(_configUri));
    setupProvider();
    _node = std::make_unique<ServiceLayerNode>(_configUri, _context, *this, getProvider(), _externalVisitors);
    _node->init();
}

StorageNode&
ServiceLayerProcess::getNode() {
    return *_node;
}

StorageNodeContext&
ServiceLayerProcess::getContext() {
    return _context;
}

std::string
ServiceLayerProcess::getComponentName() const {
    return "servicelayer";
}

} // storage
