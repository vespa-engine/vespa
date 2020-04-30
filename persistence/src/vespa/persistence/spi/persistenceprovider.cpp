// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceprovider.h"
#include <future>

namespace storage::spi {

PersistenceProvider::~PersistenceProvider() = default;

class CatchResult : public OperationComplete {
public:
    std::future<Result::UP> future_result() {
        return promisedResult.get_future();
    }
    void onComplete(Result::UP result) override {
        promisedResult.set_value(std::move(result));
    }
private:
    std::promise<Result::UP> promisedResult;
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
