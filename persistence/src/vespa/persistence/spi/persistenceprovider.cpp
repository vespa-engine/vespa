// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
                              OperationComplete::UP onComplete) {
    Result result = put(bucket, timestamp, std::move(doc), context);
    onComplete->onComplete(std::make_unique<Result>(result));
}

}
