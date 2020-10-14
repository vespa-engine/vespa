// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "downpersistence.h"

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/searchlib/util/statefile.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>

namespace storage::spi {

namespace {

Result errorResult(Result::ErrorType::FATAL_ERROR, "Node is down");

}

DownPersistence::DownPersistence(const vespalib::string &downReason)
    : _downReason(downReason)
{
}

DownPersistence::~DownPersistence() = default;

Result
DownPersistence::initialize()
{
    return Result();
}

BucketIdListResult
DownPersistence::listBuckets(BucketSpace) const
{
    return BucketIdListResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

Result
DownPersistence:: setClusterState(BucketSpace, const ClusterState&)
{
    return Result();
}

Result
DownPersistence:: setActiveState(const Bucket&, BucketInfo::ActiveState)
{
    return errorResult;
}

BucketInfoResult
DownPersistence:: getBucketInfo(const Bucket&) const
{
    return BucketInfoResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

Result
DownPersistence::put(const Bucket&, Timestamp, Document::SP, Context&)
{
    return errorResult;
}

RemoveResult
DownPersistence:: remove(const Bucket&, Timestamp, const DocumentId&, Context&)
{
    return RemoveResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

RemoveResult
DownPersistence::removeIfFound(const Bucket&, Timestamp,const DocumentId&, Context&)
{
    return RemoveResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

Result
DownPersistence::removeEntry(const Bucket&, Timestamp, Context&)
{
    return errorResult;
}

UpdateResult DownPersistence::update(const Bucket&, Timestamp, DocumentUpdate::SP, Context&)
{
    return UpdateResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

GetResult
DownPersistence::get(const Bucket&, const document::FieldSet&, const DocumentId&, Context&) const
{
    return GetResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

CreateIteratorResult
DownPersistence::createIterator(const Bucket &, FieldSetSP, const Selection &, IncludedVersions, Context &)
{
    return CreateIteratorResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

IterateResult
DownPersistence::iterate(IteratorId, uint64_t, Context&) const
{
    return IterateResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}

Result
DownPersistence::destroyIterator(IteratorId, Context&)
{
    return errorResult;
}

Result
DownPersistence::createBucket(const Bucket&, Context&)
{
    return errorResult;
}

Result
DownPersistence::deleteBucket(const Bucket&, Context&)
{
    return errorResult;
}


BucketIdListResult
DownPersistence::getModifiedBuckets(BucketSpace) const
{
    return BucketIdListResult(errorResult.getErrorCode(), errorResult.getErrorMessage());
}


Result
DownPersistence::split(const Bucket&, const Bucket&, const Bucket&, Context&)
{
    return errorResult;
}


Result
DownPersistence::join(const Bucket&, const Bucket&, const Bucket&, Context&)
{
    return errorResult;
}

}
