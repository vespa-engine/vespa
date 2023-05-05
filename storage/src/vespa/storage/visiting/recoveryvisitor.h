// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::RecoveryVisitor
 * @ingroup visitors
 *
 * @brief A recovery visitor is a visitor that sends messages with bucketid
 * and a list of minimal documents to the client.
 *
 */
#pragma once

#include "visitor.h"
#include <vespa/storageapi/message/datagram.h>

namespace documentapi { class DocumentListMessage; }

namespace storage {

class RecoveryVisitor : public Visitor {
public:
    RecoveryVisitor(StorageComponent&, const vdslib::Parameters& params);

private:
    void handleDocuments(const document::BucketId& bucketId,
                         DocEntryList & entries,
                         HitCounter& hitCounter) override;

    void completedBucket(const document::BucketId&, HitCounter&) override;

    std::set<std::string> _requestedFields;

    using CommandPtr = std::unique_ptr<documentapi::DocumentListMessage>;
    using CommandMap = std::map<document::BucketId, CommandPtr>;
    CommandMap _activeCommands;

    std::mutex _mutex;
};

struct RecoveryVisitorFactory : public VisitorFactory {

    std::shared_ptr<VisitorEnvironment>
    makeVisitorEnvironment(StorageComponent&) override {
        return std::make_shared<VisitorEnvironment>();
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&, const vdslib::Parameters& params) override
    {
        return new RecoveryVisitor(c, params);
    }

};

}



