// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::RecoveryVisitor
 * @ingroup visitors
 *
 * @brief A recovery visitor is a visitor that sends messages with bucketid
 * and a list of minimal documents to the client.
 *
 */
#pragma once

#include <vespa/storage/visiting/visitor.h>
#include <vespa/storageapi/message/datagram.h>

namespace documentapi {
class DocumentListMessage;
}

namespace storage {

class RecoveryVisitor : public Visitor {
public:
    RecoveryVisitor(StorageComponent&,
                    const vdslib::Parameters& params);

private:
    void handleDocuments(const document::BucketId& bucketId,
                         std::vector<spi::DocEntry::UP>& entries,
                         HitCounter& hitCounter);

    void completedBucket(const document::BucketId&, HitCounter&);

    std::set<std::string> _requestedFields;

    using CommandPtr = std::unique_ptr<documentapi::DocumentListMessage>;
    using CommandMap = std::map<document::BucketId, CommandPtr>;
    CommandMap _activeCommands;

    vespalib::Lock _mutex;
};

struct RecoveryVisitorFactory : public VisitorFactory {

    VisitorEnvironment::UP
    makeVisitorEnvironment(StorageComponent&) {
        return VisitorEnvironment::UP(new VisitorEnvironment);
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&,
                const vdslib::Parameters& params)
    {
        return new RecoveryVisitor(c, params);
    }

};

}



