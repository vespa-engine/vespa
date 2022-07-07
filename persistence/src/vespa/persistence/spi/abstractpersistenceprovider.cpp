// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "abstractpersistenceprovider.h"
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/idestructorcallback.h>

namespace storage::spi {

void
AbstractPersistenceProvider::removeIfFoundAsync(const Bucket& b, Timestamp timestamp,
                                                const DocumentId& id, OperationComplete::UP onComplete)
{
    std::vector<IdAndTimestamp> ids;
    ids.emplace_back(id, timestamp);
    removeAsync(b, std::move(ids), std::move(onComplete));
}

BucketIdListResult
AbstractPersistenceProvider::getModifiedBuckets(BucketSpace) const
{
    return BucketIdListResult(BucketIdListResult::List());
}

}
