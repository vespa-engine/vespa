// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceprovider.h"
#include "catchresult.h"
#include <vespa/document/base/documentid.h>
#include <future>

namespace storage::spi {

PersistenceProvider::~PersistenceProvider() = default;

Result
PersistenceProvider::setActiveState(const Bucket& bucket, BucketInfo::ActiveState activeState) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    setActiveStateAsync(bucket, activeState, std::move(catcher));
    return *future.get();
}

Result
PersistenceProvider::createBucket(const Bucket& bucket, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    createBucketAsync(bucket, context, std::move(catcher));
    return *future.get();
}

Result
PersistenceProvider::deleteBucket(const Bucket& bucket, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    deleteBucketAsync(bucket, context, std::move(catcher));
    return *future.get();
}

Result
PersistenceProvider::put(const Bucket& bucket, Timestamp timestamp, DocumentSP doc, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    putAsync(bucket, timestamp, std::move(doc), context, std::move(catcher));
    return *future.get();
}

RemoveResult
PersistenceProvider::remove(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    std::vector<TimeStampAndDocumentId> ids;
    ids.emplace_back(timestamp, docId);
    removeAsync(bucket, std::move(ids), context, std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

RemoveResult
PersistenceProvider::removeIfFound(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    removeIfFoundAsync(bucket, timestamp, docId, context, std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

UpdateResult
PersistenceProvider::update(const Bucket& bucket, Timestamp timestamp, DocumentUpdateSP upd, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    updateAsync(bucket, timestamp, std::move(upd), context, std::move(catcher));
    return dynamic_cast<const UpdateResult &>(*future.get());
}

}
