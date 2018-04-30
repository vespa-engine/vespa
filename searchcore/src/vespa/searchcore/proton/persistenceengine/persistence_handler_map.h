// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    using PersistenceHandlerSequence = vespalib::Sequence<IPersistenceHandler *>;
    using PersistenceHandlerSP = std::shared_ptr<IPersistenceHandler>;

    class HandlerSnapshot {
    private:
        PersistenceHandlerSequence::UP _handlers;
        size_t                         _size;
    public:
        using UP = std::unique_ptr<HandlerSnapshot>;
        HandlerSnapshot(PersistenceHandlerSequence::UP handlers_, size_t size_)
            : _handlers(std::move(handlers_)),
              _size(size_)
        {}
        HandlerSnapshot(const HandlerSnapshot &) = delete;
        HandlerSnapshot & operator = (const HandlerSnapshot &) = delete;

        size_t size() const { return _size; }
        PersistenceHandlerSequence &handlers() { return *_handlers; }
        static PersistenceHandlerSequence::UP release(HandlerSnapshot &&rhs) { return std::move(rhs._handlers); }
    };

private:
    using DocTypeToHandlerMap = HandlerMap<IPersistenceHandler>;

    struct BucketSpaceHash {
        std::size_t operator() (const document::BucketSpace &bucketSpace) const { return bucketSpace.getId(); }
    };

    std::unordered_map<document::BucketSpace, DocTypeToHandlerMap, BucketSpaceHash> _map;

public:
    PersistenceHandlerMap();

    PersistenceHandlerSP putHandler(document::BucketSpace bucketSpace,
                                    const DocTypeName &docType,
                                    const PersistenceHandlerSP &handler);
    PersistenceHandlerSP removeHandler(document::BucketSpace bucketSpace,
                                       const DocTypeName &docType);
    PersistenceHandlerSP getHandler(document::BucketSpace bucketSpace,
                                    const DocTypeName &docType) const;
    HandlerSnapshot::UP getHandlerSnapshot() const;
    HandlerSnapshot::UP getHandlerSnapshot(document::BucketSpace bucketSpace) const;
};

}
