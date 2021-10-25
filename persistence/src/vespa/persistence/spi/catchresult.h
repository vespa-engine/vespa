// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationcomplete.h"
#include <future>

namespace storage::spi {

class CatchResult : public OperationComplete {
public:
    CatchResult();
    ~CatchResult() override;
    std::future<std::unique_ptr<Result>> future_result() {
        return _promisedResult.get_future();
    }
    void onComplete(std::unique_ptr<Result> result) noexcept override;
    void addResultHandler(const ResultHandler * resultHandler) override;
private:
    std::promise<std::unique_ptr<Result>>  _promisedResult;
    const ResultHandler                   *_resulthandler;
};

class NoopOperationComplete : public OperationComplete {
    void onComplete(std::unique_ptr<spi::Result>) noexcept override { }
    void addResultHandler(const spi::ResultHandler *) override { }
};

}
