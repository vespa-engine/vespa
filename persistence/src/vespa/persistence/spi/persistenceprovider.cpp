// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceprovider.h"
#include "catchresult.h"
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
PersistenceProvider::createBucket(const Bucket& bucket) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    createBucketAsync(bucket, std::move(catcher));
    return *future.get();
}

Result
PersistenceProvider::deleteBucket(const Bucket& bucket) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    deleteBucketAsync(bucket, std::move(catcher));
    return *future.get();
}

Result
PersistenceProvider::put(const Bucket& bucket, Timestamp timestamp, DocumentSP doc) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    putAsync(bucket, timestamp, std::move(doc), std::move(catcher));
    return *future.get();
}

RemoveResult
PersistenceProvider::remove(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    std::vector<IdAndTimestamp> ids;
    ids.emplace_back(docId, timestamp);
    removeAsync(bucket, std::move(ids), std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

RemoveResult
PersistenceProvider::removeIfFound(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    removeIfFoundAsync(bucket, timestamp, docId, std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

UpdateResult
PersistenceProvider::update(const Bucket& bucket, Timestamp timestamp, DocumentUpdateSP upd) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    updateAsync(bucket, timestamp, std::move(upd), std::move(catcher));
    return dynamic_cast<const UpdateResult &>(*future.get());
}

}
