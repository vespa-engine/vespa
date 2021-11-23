// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "abstractpersistenceprovider.h"
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/idestructorcallback.h>

namespace storage::spi {

void
AbstractPersistenceProvider::removeIfFoundAsync(const Bucket& b, Timestamp timestamp,
                                                const DocumentId& id, Context& context, OperationComplete::UP onComplete)
{
    std::vector<TimeStampAndDocumentId> ids;
    ids.emplace_back(timestamp, id);
    removeAsync(b, std::move(ids), context, std::move(onComplete));
}

BucketIdListResult
AbstractPersistenceProvider::getModifiedBuckets(BucketSpace) const
{
    BucketIdListResult::List list;
    return BucketIdListResult(list);
}

}
