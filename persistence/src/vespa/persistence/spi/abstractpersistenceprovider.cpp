// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "abstractpersistenceprovider.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>

namespace storage::spi {

UpdateResult
AbstractPersistenceProvider::update(const Bucket& bucket, Timestamp ts,
                                    const DocumentUpdate::SP& upd, Context& context)
{
    GetResult getResult = get(bucket, document::AllFields(), upd->getId(), context);

    if (getResult.hasError()) {
        return UpdateResult(getResult.getErrorCode(), getResult.getErrorMessage());
    }

    auto docToUpdate = getResult.getDocumentPtr();
    Timestamp updatedTs = getResult.getTimestamp();
    if (!docToUpdate) {
        if (!upd->getCreateIfNonExistent()) {
            return UpdateResult();
        } else {
            docToUpdate = std::make_shared<document::Document>(upd->getType(), upd->getId());
            updatedTs = ts;
        }
    }

    upd->applyTo(*docToUpdate);

    Result putResult = put(bucket, ts, docToUpdate, context);

    if (putResult.hasError()) {
        return UpdateResult(putResult.getErrorCode(),
                            putResult.getErrorMessage());
    }

    return UpdateResult(updatedTs);
}

RemoveResult
AbstractPersistenceProvider::removeIfFound(const Bucket& b, Timestamp timestamp,
                                           const DocumentId& id, Context& context)
{
    return remove(b, timestamp, id, context);
}

BucketIdListResult
AbstractPersistenceProvider::getModifiedBuckets(BucketSpace) const
{
    BucketIdListResult::List list;
    return BucketIdListResult(list);
}

Result
AbstractPersistenceProvider::move(const Bucket& source, PartitionId target, Context& context)
{
    spi::Bucket to(source.getBucket(), spi::PartitionId(target));

    return join(source, source, to, context);
}

}
