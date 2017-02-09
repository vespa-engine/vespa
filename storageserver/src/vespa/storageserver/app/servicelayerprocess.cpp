// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayerprocess.h"

#include <vespa/searchvisitor/searchvisitor.h>
#include <vespa/storage/storageutil/utils.h>

namespace storage {

ServiceLayerProcess::ServiceLayerProcess(const config::ConfigUri & configUri)
    : Process(configUri)
{
}

void
ServiceLayerProcess::shutdown()
{
    Process::shutdown();
    _node.reset(0);
}

void
ServiceLayerProcess::createNode()
{
    _externalVisitors["searchvisitor"].reset(new SearchVisitorFactory(_configUri));
    setupProvider();
    _node.reset(new ServiceLayerNode(_configUri, _context, *this, getProvider(), _externalVisitors));
    _node->init();
}

} // storage
