// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/result.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage {

/**
 * @class ReadBucketList
 * @ingroup common
 *
 * @brief List buckets existing in a bucket space.
 */
class ReadBucketList : public api::InternalCommand {
    document::BucketSpace _bucketSpace;

public:
    typedef std::unique_ptr<ReadBucketList> UP;
    static const uint32_t ID = 2003;

    ReadBucketList(document::BucketSpace bucketSpace);
    ~ReadBucketList();
    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    document::Bucket getBucket() const override;

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


/**
 * @class ReadBucketListReply
 * @ingroup common
 */
class ReadBucketListReply : public api::InternalReply {
    document::BucketSpace _bucketSpace;
    spi::BucketIdListResult::List _buckets;

public:
    typedef std::unique_ptr<ReadBucketListReply> UP;
    typedef std::shared_ptr<ReadBucketListReply> SP;
    static const uint32_t ID = 2004;

    ReadBucketListReply(const ReadBucketList& cmd);
    ~ReadBucketListReply();

    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    document::Bucket getBucket() const override;

    spi::BucketIdListResult::List& getBuckets() { return _buckets; }
    const spi::BucketIdListResult::List& getBuckets() const {
        return _buckets;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class ReadBucketInfo
 * @ingroup common
 *
 * @brief Get more detailed information about a set of buckets.
 *
 * The distributor wants some information for each bucket, that one
 * have to open the bucket and read its headers to find. This class is
 * used to retrieve such information.
 */
class ReadBucketInfo : public api::InternalCommand {
    document::Bucket _bucket;

public:
    static const uint32_t ID = 2005;

    ReadBucketInfo(const document::Bucket &bucket);
    ~ReadBucketInfo();

    document::Bucket getBucket() const override { return _bucket; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    vespalib::string getSummary() const override;
};


/**
 * @class ReadBucketInfoReply
 * @ingroup common
 */
class ReadBucketInfoReply : public api::InternalReply {
    document::Bucket _bucket;

public:
    static const uint32_t ID = 2006;

    ReadBucketInfoReply(const ReadBucketInfo& cmd);
    ~ReadBucketInfoReply();

    document::Bucket getBucket() const override { return _bucket; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // storage
