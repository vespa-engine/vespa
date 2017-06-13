// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "abstractpersistenceprovider.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldset/fieldsets.h>


namespace storage {

namespace spi {

UpdateResult
AbstractPersistenceProvider::update(const Bucket& bucket, Timestamp ts,
                                    const DocumentUpdate::SP& upd, Context& context)
{
    GetResult getResult = get(bucket, document::AllFields(), upd->getId(), context);

    if (getResult.hasError()) {
        return UpdateResult(getResult.getErrorCode(), getResult.getErrorMessage());
    }

    if (!getResult.hasDocument()) {
        return UpdateResult();
    }

    upd->applyTo(getResult.getDocument());

    Result putResult = put(bucket, ts, getResult.getDocumentPtr(), context);

    if (putResult.hasError()) {
        return UpdateResult(putResult.getErrorCode(),
                            putResult.getErrorMessage());
    }

    return UpdateResult(getResult.getTimestamp());
}

RemoveResult
AbstractPersistenceProvider::removeIfFound(const Bucket& b, Timestamp timestamp,
                                           const DocumentId& id, Context& context)
{
    return remove(b, timestamp, id, context);
}

BucketIdListResult
AbstractPersistenceProvider::getModifiedBuckets() const
{
    BucketIdListResult::List list;
    return BucketIdListResult(list);
}

Result
AbstractPersistenceProvider::move(const Bucket& source, PartitionId target, Context& context)
{
    spi::Bucket to(source.getBucketId(), spi::PartitionId(target));

    return join(source, source, to, context);
}

}

}
