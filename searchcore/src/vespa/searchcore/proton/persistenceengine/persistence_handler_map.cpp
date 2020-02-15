// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ipersistencehandler.h"
#include "persistence_handler_map.h"

namespace proton {

using HandlerSnapshot = PersistenceHandlerMap::HandlerSnapshot;

PersistenceHandlerMap::PersistenceHandlerMap()
    : _map()
{
}

IPersistenceHandler::SP
PersistenceHandlerMap::putHandler(document::BucketSpace bucketSpace,
                                  const DocTypeName &docType,
                                  const IPersistenceHandler::SP &handler)
{
    return _map[bucketSpace].putHandler(docType, handler);
}

IPersistenceHandler *
PersistenceHandlerMap::getHandler(document::BucketSpace bucketSpace,
                                  const DocTypeName &docType) const
{
    auto itr = _map.find(bucketSpace);
    if (itr != _map.end()) {
        return itr->second.getHandlerPtr(docType);
    }
    return nullptr;
}

IPersistenceHandler::SP
PersistenceHandlerMap::removeHandler(document::BucketSpace bucketSpace,
                                     const DocTypeName &docType)
{
    auto itr = _map.find(bucketSpace);
    if (itr != _map.end()) {
        return itr->second.removeHandler(docType);
    }
    return IPersistenceHandler::SP();
}

HandlerSnapshot
PersistenceHandlerMap::getHandlerSnapshot() const
{
    std::vector<IPersistenceHandler::SP> handlers;
    for (auto spaceItr : _map) {
        for (auto handlerItr : spaceItr.second) {
            handlers.push_back(handlerItr.second);
        }
    }
    size_t handlersSize = handlers.size();
    return HandlerSnapshot(DocTypeToHandlerMap::Snapshot(std::move(handlers)), handlersSize);
}

HandlerSnapshot
PersistenceHandlerMap::getHandlerSnapshot(document::BucketSpace bucketSpace) const
{
    auto itr = _map.find(bucketSpace);
    if (itr != _map.end()) {
        return HandlerSnapshot(itr->second.snapshot(), itr->second.size());
    }
    return HandlerSnapshot();
}

}
