// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::TestVisitor
 * @ingroup visitors
 *
 * @brief A visitor used purely for testing the visitor framework.
 *
 */
#pragma once

#include <vespa/storage/visiting/visitor.h>

namespace storage {

class TestVisitor : public Visitor {
public:
    TestVisitor(StorageComponent&, const vdslib::Parameters&);

private:
    void startingVisitor(const std::vector<document::BucketId>& buckets);

    void handleDocuments(const document::BucketId& bucketId,
                         std::vector<spi::DocEntry::UP>& entries,
                         HitCounter& hitCounter);

    void completedBucket(const document::BucketId& bucket, HitCounter& hitCounter);

    spi::ReadConsistency getRequiredReadConsistency() const override {
        return spi::ReadConsistency::WEAK;
    }

    void completedVisiting(HitCounter& hitCounter);

    void abortedVisiting();

        // Send datagram with message back to client
    void report(const std::string& message);

    std::string _params;
};

struct TestVisitorFactory : public VisitorFactory {

    VisitorEnvironment::UP
    makeVisitorEnvironment(StorageComponent&) {
        return VisitorEnvironment::UP(new VisitorEnvironment);
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&,
                const vdslib::Parameters& params) {
        return new TestVisitor(c, params);
    }

};

}



