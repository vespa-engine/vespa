// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/common/configrequest.h>
#include <vespa/config/common/configresponse.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/frt/frtconfigagent.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <config-my.h>

using namespace config;

class MyConfigRequest : public ConfigRequest
{
public:
    MyConfigRequest(const ConfigKey & key)
        : _key(key)
    { }

    const ConfigKey & getKey() const override { return _key; }
    bool abort() override { return false; }
    void setError(int errorCode) override { (void) errorCode; }
    bool verifyState(const ConfigState &) const override { return false; }
    const ConfigKey _key;
};

class MyConfigResponse : public ConfigResponse
{
public:
    MyConfigResponse(const ConfigKey & key, ConfigValue value, bool valid, int64_t timestamp,
                     const std::string & xxhash64, const std::string & errorMsg, int errorC0de, bool iserror)
        : _key(key),
          _value(std::move(value)),
          _fillCalled(false),
          _valid(valid),
          _state(xxhash64, timestamp, false),
          _errorMessage(errorMsg),
          _errorCode(errorC0de),
          _isError(iserror)
    { }

    const ConfigKey& getKey() const override { return _key; }
    const ConfigValue & getValue() const override { return _value; }
    const ConfigState & getConfigState() const override { return _state; }
    bool hasValidResponse() const override { return _valid; }
    bool validateResponse() override { return _valid; }
    void fill() override { _fillCalled = true; }
    std::string errorMessage() const override { return _errorMessage; }
    int errorCode() const override { return _errorCode; }
    bool isError() const override { return  _isError; }
    const Trace & getTrace() const override { return _trace; }

    const ConfigKey _key;
    const ConfigValue _value;
    bool _fillCalled;
    bool _valid;
    const ConfigState _state;
    std::string _errorMessage;
    int _errorCode;
    bool _isError;
    Trace _trace;


    static std::unique_ptr<ConfigResponse> createOKResponse(const ConfigKey & key, const ConfigValue & value, uint64_t timestamp = 10, const std::string & xxhash64 = "a")
    {
        return std::make_unique<MyConfigResponse>(key, value, true, timestamp, xxhash64, "", 0, false);
    }

    static std::unique_ptr<ConfigResponse> createServerErrorResponse(const ConfigKey & key, const ConfigValue & value)
    {
        return std::make_unique<MyConfigResponse>(key, value, true, 10, "a", "whinewhine", 2, true);
    }

    static std::unique_ptr<ConfigResponse> createConfigErrorResponse(const ConfigKey & key, const ConfigValue & value)
    {
        return std::make_unique<MyConfigResponse>(key, value, false, 10, "a", "", 0, false);
    }
};

class MyHolder : public IConfigHolder
{
public:
    MyHolder() noexcept = default;
    ~MyHolder() = default;

    std::unique_ptr<ConfigUpdate> provide() override
    {
        return std::move(_update);
    }

    bool wait_until(vespalib::steady_time) override
    {
        return true;
    }

    void handle(std::unique_ptr<ConfigUpdate> update) override
    {
        if (_update) {
            update->merge(*_update);
        }
        _update = std::move(update);
    }

    bool poll() override { return true; }
    void close() override { }
private:
    std::unique_ptr<ConfigUpdate> _update;
};


ConfigValue createValue(const std::string & myField, const std::string & xxhash64)
{
    StringVector lines;
    lines.push_back("myField \"" + myField + "\"");
    return ConfigValue(lines, xxhash64);
}

static TimingValues testTimingValues(
        2000ms,  // successTimeout
        500ms,  // errorTimeout
        500ms,   // initialTimeout
        4000ms,  // subscribeTimeout
        0ms,     // fixedDelay
        250ms,   // successDelay
        250ms,   // unconfiguredDelay
        500ms,   // configuredErrorDelay
        5,
        1000ms,
        2000ms);    // maxDelayMultiplier

TEST(ConfigAgentTest, require_that_agent_returns_correct_values)
{
    FRTConfigAgent handler(std::make_shared<MyHolder>(), testTimingValues);
    ASSERT_EQ(500ms, handler.getTimeout());
    ASSERT_EQ(0ms, handler.getWaitTime());
    ConfigState cs;
    ASSERT_EQ(cs.xxhash64, handler.getConfigState().xxhash64);
    ASSERT_EQ(cs.generation, handler.getConfigState().generation);
    ASSERT_EQ(cs.applyOnRestart, handler.getConfigState().applyOnRestart);
}

TEST(ConfigAgentTest, require_that_successful_request_is_delivered_to_holder)
{
    const ConfigKey testKey(ConfigKey::create<MyConfig>("mykey"));
    const ConfigValue testValue(createValue("l33t", "a"));
    auto latch = std::make_shared<MyHolder>();

    FRTConfigAgent handler(latch, testTimingValues);
    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createOKResponse(testKey, testValue));
    ASSERT_TRUE(latch->poll());
    std::unique_ptr<ConfigUpdate> update(latch->provide());
    ASSERT_TRUE(update);
    ASSERT_TRUE(update->hasChanged());
    MyConfig cfg(update->getValue());
    ASSERT_EQ("l33t", cfg.myField);
}

TEST(ConfigAgentTest, require_that_request_with_change_is_delivered_to_holder_even_if_it_was_not_the_last)
{
    const ConfigKey testKey(ConfigKey::create<MyConfig>("mykey"));
    const ConfigValue testValue1(createValue("l33t", "a"));
    const ConfigValue testValue2(createValue("l34t", "b"));
    auto latch = std::make_shared<MyHolder>();

    FRTConfigAgent handler(latch, testTimingValues);
    handler.handleResponse(MyConfigRequest(testKey),
                           MyConfigResponse::createOKResponse(testKey, testValue1, 1, testValue1.getXxhash64()));
    ASSERT_TRUE(latch->poll());
    std::unique_ptr<ConfigUpdate> update(latch->provide());
    ASSERT_TRUE(update);
    ASSERT_TRUE(update->hasChanged());
    MyConfig cfg(update->getValue());
    ASSERT_EQ("l33t", cfg.myField);

    handler.handleResponse(MyConfigRequest(testKey),
                           MyConfigResponse::createOKResponse(testKey, testValue2, 2, testValue2.getXxhash64()));
    handler.handleResponse(MyConfigRequest(testKey),
                           MyConfigResponse::createOKResponse(testKey, testValue2, 3, testValue2.getXxhash64()));
    ASSERT_TRUE(latch->poll());
    update = latch->provide();
    ASSERT_TRUE(update);
    ASSERT_TRUE(update->hasChanged());
    MyConfig cfg2(update->getValue());
    ASSERT_EQ("l34t", cfg2.myField);
}

TEST(ConfigAgentTest, require_that_successful_request_sets_correct_wait_time)
{
    const ConfigKey testKey(ConfigKey::create<MyConfig>("mykey"));
    const ConfigValue testValue(createValue("l33t", "a"));
    auto latch = std::make_shared<MyHolder>();
    FRTConfigAgent handler(latch, testTimingValues);

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createOKResponse(testKey, testValue));
    ASSERT_EQ(250ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createOKResponse(testKey, testValue));
    ASSERT_EQ(250ms, handler.getWaitTime());
}

TEST(ConfigAgentTest, require_that_bad_config_response_returns_false)
{
    const ConfigKey testKey(ConfigKey::create<MyConfig>("mykey"));
    const ConfigValue testValue(createValue("myval", "a"));
    auto latch = std::make_shared<MyHolder>();
    FRTConfigAgent handler(latch, testTimingValues);

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(250ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(500ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(750ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(1000ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(1250ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(1250ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createOKResponse(testKey, testValue));
    ASSERT_EQ(250ms, handler.getWaitTime());
    ASSERT_EQ(2000ms, handler.getTimeout());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createConfigErrorResponse(testKey, testValue));
    ASSERT_EQ(500ms, handler.getWaitTime());
    ASSERT_EQ(500ms, handler.getTimeout());
}

TEST(ConfigAgentTest, require_that_bad_response_returns_false)
{
    const ConfigKey testKey(ConfigKey::create<MyConfig>("mykey"));
    const ConfigValue testValue(StringVector(), "a");

    auto latch = std::make_shared<MyHolder>();
    FRTConfigAgent handler(latch, testTimingValues);

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(250ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(500ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(750ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(1000ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(1250ms, handler.getWaitTime());

    handler.handleResponse(MyConfigRequest(testKey), MyConfigResponse::createServerErrorResponse(testKey, testValue));
    ASSERT_EQ(1250ms, handler.getWaitTime());
}

GTEST_MAIN_RUN_ALL_TESTS()
