// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/persistenceengine/resulthandler.h>

namespace proton::test {

class GenericResultHandler : public IGenericResultHandler
{
private:
    std::unique_ptr<storage::spi::Result> _result;
public:
    ~GenericResultHandler() override;
    void handle(const storage::spi::Result &result) override;
    bool valid() const { return static_cast<bool>(_result); }
    const storage::spi::Result &getResult() const { return *_result; }
};


class BucketInfoResultHandler : public IBucketInfoResultHandler
{
private:
    std::unique_ptr<storage::spi::BucketInfoResult> _result;
public:
    ~BucketInfoResultHandler() override;
    void handle(const storage::spi::BucketInfoResult &result) override;
    bool valid() const { return static_cast<bool>(_result); }
    const storage::spi::BucketInfoResult &getResult() const { return *_result; }
    const storage::spi::BucketInfo &getInfo() const { return getResult().getBucketInfo(); }
};


class BucketIdListResultHandler : public IBucketIdListResultHandler
{
private:
    std::unique_ptr<storage::spi::BucketIdListResult> _result;
public:
    ~BucketIdListResultHandler() override;
    void handle(storage::spi::BucketIdListResult result) override;
    bool valid() const { return static_cast<bool>(_result); }
    const storage::spi::BucketIdListResult &getResult() const { return *_result; }
    const storage::spi::BucketIdListResult::List &getList() const { return getResult().getList(); }
};

}
