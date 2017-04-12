// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::DumpVisitor
 * @ingroup visitors
 *
 * @brief A dump visitor is a visitor that sends documents to the client.
 *
 */
#pragma once

#include <vespa/storage/visiting/visitor.h>

namespace documentapi {
class MultiOperationMessage;
}

namespace storage {

class DumpVisitor : public Visitor {
public:
    DumpVisitor(StorageComponent& component, const vdslib::Parameters&);

private:
    std::unique_ptr<documentapi::MultiOperationMessage>
        createMultiOperation(const document::BucketId& bucketId,
                             const std::vector<const document::Document*>& docs);

    void handleDocuments(const document::BucketId& bucketId,
                         std::vector<spi::DocEntry::UP>& entries,
                         HitCounter& hitCounter) override;

    std::unique_ptr<std::set<std::string> > _requestedFields;
    std::unique_ptr<std::set<std::string> > _requestedDocuments;

    bool _keepTimeStamps;
};

class DumpVisitorFactory : public VisitorFactory {
public:
    DumpVisitorFactory() {}

    VisitorEnvironment::UP
    makeVisitorEnvironment(StorageComponent&) override {
        return VisitorEnvironment::UP(new VisitorEnvironment);
    };

    storage::Visitor*
    makeVisitor(StorageComponent& component, storage::VisitorEnvironment&,
                const vdslib::Parameters& params) override
    {
        return new DumpVisitor(component, params);
    }
};

}



