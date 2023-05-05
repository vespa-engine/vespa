// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::TestVisitor
 * @ingroup visitors
 *
 * @brief A visitor used purely for testing the visitor framework.
 *
 */
#pragma once

#include "visitor.h"

namespace storage {

class TestVisitor : public Visitor {
public:
    TestVisitor(StorageComponent&, const vdslib::Parameters&);

private:
    void startingVisitor(const std::vector<document::BucketId>& buckets) override;

    void handleDocuments(const document::BucketId& bucketId,
                         DocEntryList & entries,
                         HitCounter& hitCounter) override;

    void completedBucket(const document::BucketId& bucket, HitCounter& hitCounter) override;

    spi::ReadConsistency getRequiredReadConsistency() const override {
        return spi::ReadConsistency::WEAK;
    }

    void completedVisiting(HitCounter& hitCounter) override;
    void abortedVisiting() override;

    // Send datagram with message back to client
    void report(const std::string& message);

    std::string _params;
};

struct TestVisitorFactory : public VisitorFactory {

    std::shared_ptr<VisitorEnvironment>
    makeVisitorEnvironment(StorageComponent&) override {
        return std::make_shared<VisitorEnvironment>();
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&,
                const vdslib::Parameters& params) override {
        return new TestVisitor(c, params);
    }

};

}
