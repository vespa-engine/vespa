// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceprovider.h"
#include <future>
#include <cassert>

namespace storage::spi {

PersistenceProvider::~PersistenceProvider() = default;

class CatchResult : public OperationComplete {
public:
    CatchResult() : _promisedResult(), _resulthandler(nullptr) {}
    std::future<Result::UP> future_result() {
        return _promisedResult.get_future();
    }
    void onComplete(Result::UP result) override {
        _promisedResult.set_value(std::move(result));
    }
    void addResultHandler(const ResultHandler * resultHandler) override {
        assert(_resulthandler == nullptr);
        _resulthandler = resultHandler;
    }
private:
    std::promise<Result::UP>  _promisedResult;
    const ResultHandler      *_resulthandler;
};

Result
PersistenceProvider::put(const Bucket& bucket, Timestamp timestamp, DocumentSP doc, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    putAsync(bucket, timestamp, std::move(doc), context, std::move(catcher));
    return *future.get();
}

void
PersistenceProvider::putAsync(const Bucket &bucket, Timestamp timestamp, DocumentSP doc, Context &context,
                              OperationComplete::UP onComplete)
{
    Result result = put(bucket, timestamp, std::move(doc), context);
    onComplete->onComplete(std::make_unique<Result>(result));
}

RemoveResult
PersistenceProvider::remove(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    removeAsync(bucket, timestamp, docId, context, std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

void
PersistenceProvider::removeAsync(const Bucket &bucket, Timestamp timestamp, const DocumentId & docId, Context &context,
                                 OperationComplete::UP onComplete)
{
    RemoveResult result = remove(bucket, timestamp, docId, context);
    onComplete->onComplete(std::make_unique<RemoveResult>(result));
}

RemoveResult
PersistenceProvider::removeIfFound(const Bucket& bucket, Timestamp timestamp, const DocumentId & docId, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    removeIfFoundAsync(bucket, timestamp, docId, context, std::move(catcher));
    return dynamic_cast<const RemoveResult &>(*future.get());
}

void
PersistenceProvider::removeIfFoundAsync(const Bucket &bucket, Timestamp timestamp, const DocumentId & docId, Context &context,
                                        OperationComplete::UP onComplete)
{
    RemoveResult result = removeIfFound(bucket, timestamp, docId, context);
    onComplete->onComplete(std::make_unique<RemoveResult>(result));
}

UpdateResult
PersistenceProvider::update(const Bucket& bucket, Timestamp timestamp, DocumentUpdateSP upd, Context& context) {
    auto catcher = std::make_unique<CatchResult>();
    auto future = catcher->future_result();
    updateAsync(bucket, timestamp, std::move(upd), context, std::move(catcher));
    return dynamic_cast<const UpdateResult &>(*future.get());
}

void
PersistenceProvider::updateAsync(const Bucket &bucket, Timestamp timestamp, DocumentUpdateSP upd, Context &context,
                                        OperationComplete::UP onComplete)
{
    UpdateResult result = update(bucket, timestamp, std::move(upd), context);
    onComplete->onComplete(std::make_unique<UpdateResult>(result));
}

}
