// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "abstractpersistenceprovider.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/util/idestructorcallback.h>

namespace storage::spi {

RemoveResult
AbstractPersistenceProvider::removeIfFound(const Bucket& b, Timestamp timestamp,
                                           const DocumentId& id, Context& context)
{
    return remove(b, timestamp, id, context);
}

void
AbstractPersistenceProvider::removeIfFoundAsync(const Bucket& b, Timestamp timestamp,
                                                const DocumentId& id, Context& context, OperationComplete::UP onComplete)
{
    removeAsync(b, timestamp, id, context, std::move(onComplete));
}

BucketIdListResult
AbstractPersistenceProvider::getModifiedBuckets(BucketSpace) const
{
    BucketIdListResult::List list;
    return BucketIdListResult(list);
}

}
