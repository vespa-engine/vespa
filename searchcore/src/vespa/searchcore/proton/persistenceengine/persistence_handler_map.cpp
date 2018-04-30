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

IPersistenceHandler::SP
PersistenceHandlerMap::getHandler(document::BucketSpace bucketSpace,
                                  const DocTypeName &docType) const
{
    auto itr = _map.find(bucketSpace);
    if (itr != _map.end()) {
        return itr->second.getHandler(docType);
    }
    return IPersistenceHandler::SP();
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

HandlerSnapshot::UP
PersistenceHandlerMap::getHandlerSnapshot() const
{
    std::vector<IPersistenceHandler::SP> handlers;
    for (auto spaceItr : _map) {
        for (auto handlerItr : spaceItr.second) {
            handlers.push_back(handlerItr.second);
        }
    }
    return std::make_unique<HandlerSnapshot>
            (std::make_unique<DocTypeToHandlerMap::Snapshot>(std::move(handlers)),
             handlers.size());
}

namespace {

struct EmptySequence : public vespalib::Sequence<IPersistenceHandler *> {
    virtual bool valid() const override { return false; }
    virtual IPersistenceHandler *get() const override { return nullptr; }
    virtual void next() override { }
    static EmptySequence::UP make() { return std::make_unique<EmptySequence>(); }
};

}

HandlerSnapshot::UP
PersistenceHandlerMap::getHandlerSnapshot(document::BucketSpace bucketSpace) const
{
    auto itr = _map.find(bucketSpace);
    if (itr != _map.end()) {
        return std::make_unique<HandlerSnapshot>(itr->second.snapshot(), itr->second.size());
    }
    return std::make_unique<HandlerSnapshot>(EmptySequence::make(), 0);
}

namespace {

class SequenceOfOne : public vespalib::Sequence<IPersistenceHandler *> {
private:
    bool _done;
    IPersistenceHandler *_value;
public:
    SequenceOfOne(IPersistenceHandler *value) : _done(false), _value(value) {}

    virtual bool valid() const override { return !_done; }
    virtual IPersistenceHandler *get() const override { return _value; }
    virtual void next() override { _done = true; }
    static SequenceOfOne::UP make(IPersistenceHandler *value) { return std::make_unique<SequenceOfOne>(value); }
};

}

}
