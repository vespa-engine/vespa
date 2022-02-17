// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configmanager.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/config/common/iconfigholder.h>

#include <vespa/config/raw/rawsource.h>
#include "config-my.h"

using namespace config;

namespace {

    ConfigValue createValue(const std::string & myField, const std::string & md5)
    {
        StringVector lines;
        lines.push_back("myField \"" + myField + "\"");
        return ConfigValue(lines, md5);
    }

    struct TestContext
    {
        int numGetConfig;
        int numUpdate;
        int numClose;
        int64_t generation;
        bool respond;
        TestContext()
            : numGetConfig(0), numUpdate(0), numClose(0), generation(-1), respond(true)
        { }
    };

    class MySource : public Source
    {
    public:
        MySource(TestContext * data, std::shared_ptr<IConfigHolder> holder) : _holder(std::move(holder)), _data(data) { }
        void getConfig() override
        {
            _data->numGetConfig++;
            if (_data->respond) {
                _holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), true, _data->generation));
            }
        }
        void reload(int64_t generation) override
        {
            _data->numUpdate++;
            _data->generation = generation;
        }
        void close() override
        {
            _data->numClose++;
        }
        std::shared_ptr<IConfigHolder> _holder;
        TestContext * _data;
    };

    class MySourceFactory : public SourceFactory
    {
    public:
        MySourceFactory(TestContext * d) : data(d) { }
        std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const override
        {
            (void) key;
            return std::make_unique<MySource>(data, std::move(holder));
        }
        TestContext * data;
    };

    class MySpec : public SourceSpec
    {
    public:
        MySpec(TestContext * data)
            : _key("foo"),
              _data(data)
        {
        }
        SourceSpecKey createKey() const { return SourceSpecKey(_key); }
        std::unique_ptr<SourceFactory> createSourceFactory(const TimingValues & timingValues) const override {
            (void) timingValues;
            return std::make_unique<MySourceFactory>(_data);
        }
        SourceSpec * clone() const { return new MySpec(*this); }
    private:
        const std::string _key;
        TestContext * _data;
    };

    static TimingValues testTimingValues(
            2000ms,  // successTimeout
            500ms,  // errorTimeout
            500ms,   // initialTimeout
            4000ms,  // unsubscribeTimeout
            0ms,     // fixedDelay
            250ms,   // successDelay
            250ms,   // unconfiguredDelay
            500ms,   // configuredErrorDelay
            5,
            1000ms,
            2000ms);    // maxDelayMultiplier

    class ManagerTester {
    public:
        ConfigKey key;
        ConfigManager _mgr;
        ConfigSubscription::SP sub;

        ManagerTester(const ConfigKey & k, const MySpec & s);
        ~ManagerTester();

        void subscribe()
        {
            sub = _mgr.subscribe(key, 5000ms);
        }
    };

    ManagerTester::ManagerTester(const ConfigKey & k, const MySpec & s)
        : key(k),
          _mgr(s.createSourceFactory(testTimingValues), 1)
    { }
    ManagerTester::~ManagerTester() { }

}

TEST("requireThatSubscriptionTimesout") {
    const ConfigKey key(ConfigKey::create<MyConfig>("myid"));
    const ConfigValue testValue(createValue("l33t", "a"));

    { // No valid response
        TestContext data;
        data.respond = false;

        ManagerTester tester(ConfigKey::create<MyConfig>("myid"), MySpec(&data));
        bool thrown = false;
        try {
            tester.subscribe();
        } catch (const ConfigRuntimeException & e) {
            thrown = true;
        }
        ASSERT_TRUE(thrown);
        ASSERT_EQUAL(1, data.numGetConfig);
    }
}
TEST("requireThatSourceIsAskedForRequest") {
    TestContext data;
    const ConfigKey key(ConfigKey::create<MyConfig>("myid"));
    const ConfigValue testValue(createValue("l33t", "a"));
    try {
        ManagerTester tester(key, MySpec(&data));
        tester.subscribe();
        ASSERT_EQUAL(1, data.numGetConfig);
    } catch (ConfigRuntimeException & e) {
        ASSERT_TRUE(false);
    }
    ASSERT_EQUAL(1, data.numClose);
}

TEST("require that new sources are given the correct generation") {
    TestContext data;
    const ConfigKey key(ConfigKey::create<MyConfig>("myid"));
    const ConfigValue testValue(createValue("l33t", "a"));
    try {
        ManagerTester tester(key, MySpec(&data));
        tester._mgr.reload(30);
        tester.subscribe();
        ASSERT_EQUAL(30, data.generation);
    } catch (ConfigRuntimeException & e) {
        ASSERT_TRUE(false);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
