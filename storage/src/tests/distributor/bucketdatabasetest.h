// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>

namespace storage::distributor {

struct BucketDatabaseTest : public ::testing::TestWithParam<std::shared_ptr<BucketDatabase>> {
    void SetUp() override ;

    std::string doFindParents(const std::vector<document::BucketId>& ids, const document::BucketId& searchId);
    std::string doFindAll(const std::vector<document::BucketId>& ids, const document::BucketId& searchId);
    document::BucketId doCreate(const std::vector<document::BucketId>& ids,
                                uint32_t minBits, const document::BucketId& wantedId);

    BucketDatabase& db() noexcept { return *GetParam(); }

    using UBoundFunc = std::function<document::BucketId(const BucketDatabase&, const document::BucketId&)>;

    void doTestUpperBound(const UBoundFunc& f);
};

}
