// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/vespalib/util/sequence.h>
#include <memory>
#include <unordered_map>

namespace document { class DocumentId; }

namespace proton {

class DocTypeName;
class IPersistenceHandler;

/**
 * Class that maintains a set of PersistenceHandler instances
 * and provides mapping from bucket space to the set of handlers in that space.
 */
class PersistenceHandlerMap {
public:
    using DocTypeToHandlerMap = HandlerMap<IPersistenceHandler>;
    using PersistenceHandlerSP = std::shared_ptr<IPersistenceHandler>;

    class HandlerSnapshot {
    private:
        DocTypeToHandlerMap::Snapshot  _handlers;
    public:
        HandlerSnapshot() : _handlers() {}
        HandlerSnapshot(DocTypeToHandlerMap::Snapshot handlers_)
            : _handlers(std::move(handlers_))
        {}
        HandlerSnapshot(const HandlerSnapshot &) = delete;
        HandlerSnapshot & operator = (const HandlerSnapshot &) = delete;
        ~HandlerSnapshot();

        size_t size() const { return _handlers.size(); }
        vespalib::Sequence<IPersistenceHandler*> &handlers() { return _handlers; }
        static DocTypeToHandlerMap::Snapshot release(HandlerSnapshot &&rhs) { return std::move(rhs._handlers); }
    };

    class UnsafeHandlerSnapshot {
    private:
        DocTypeToHandlerMap::UnsafeSnapshot  _handlers;
    public:
        UnsafeHandlerSnapshot() : _handlers() {}
        UnsafeHandlerSnapshot(DocTypeToHandlerMap::UnsafeSnapshot handlers_)
            : _handlers(std::move(handlers_))
        {}
        UnsafeHandlerSnapshot(const UnsafeHandlerSnapshot &) = delete;
        UnsafeHandlerSnapshot & operator = (const UnsafeHandlerSnapshot &) = delete;
        ~UnsafeHandlerSnapshot();

        size_t size() const { return _handlers.size(); }
        vespalib::Sequence<IPersistenceHandler*> &handlers() { return _handlers; }
    };

private:
    struct BucketSpaceHash {
        std::size_t operator() (const document::BucketSpace &bucketSpace) const { return bucketSpace.getId(); }
    };

    std::unordered_map<document::BucketSpace, DocTypeToHandlerMap, BucketSpaceHash> _map;

public:
    PersistenceHandlerMap();

    PersistenceHandlerSP putHandler(document::BucketSpace bucketSpace, const DocTypeName &docType, const PersistenceHandlerSP &handler);
    PersistenceHandlerSP removeHandler(document::BucketSpace bucketSpace, const DocTypeName &docType);
    IPersistenceHandler * getHandler(document::BucketSpace bucketSpace, const DocTypeName &docType) const;
    HandlerSnapshot getHandlerSnapshot() const;
    HandlerSnapshot getHandlerSnapshot(document::BucketSpace bucketSpace) const;
    UnsafeHandlerSnapshot getUnsafeHandlerSnapshot(document::BucketSpace bucketSpace) const;
};

}
