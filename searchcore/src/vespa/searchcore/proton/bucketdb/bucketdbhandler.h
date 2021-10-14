// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_owner.h"
#include "ibucketdbhandler.h"
#include "ibucketdbhandlerinitializer.h"
#include "bucket_create_notifier.h"

namespace proton::bucketdb {

/**
 * The BucketDBHandler class handles operations on a bucket db.
 */
class BucketDBHandler : public IBucketDBHandler,
                        public IBucketDBHandlerInitializer
{
private:
    struct MetaStoreDesc
    {
        IDocumentMetaStore *_dms;
        search::SerialNum   _flushedSerialNum;

        MetaStoreDesc(IDocumentMetaStore *dms,
                      search::SerialNum flushedSerialNum)
            : _dms(dms),
              _flushedSerialNum(flushedSerialNum)
        {
        }
    };

    BucketDBOwner             &_bucketDB;
    std::vector<MetaStoreDesc> _dmsv;
    BucketCreateNotifier       _bucketCreateNotifier;

public:
    BucketDBHandler(BucketDBOwner &bucketDB);
    ~BucketDBHandler();

    void setBucketDB(BucketDBOwner &bucketDB);
    void addDocumentMetaStore(IDocumentMetaStore *dms, search::SerialNum flushedSerialNum) override;
    void handleSplit(search::SerialNum serialNum, const BucketId &source,
                     const BucketId &target1, const BucketId &target2) override;
    void handleJoin(search::SerialNum serialNum, const BucketId &source1,
                    const BucketId &source2, const BucketId &target) override;
    void handleCreateBucket(const BucketId &bucketId) override;
    void handleDeleteBucket(const BucketId &bucketId) override;

    IBucketCreateNotifier &getBucketCreateNotifier() { return _bucketCreateNotifier; }
};

}
