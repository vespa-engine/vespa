// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-bootstrap.h"
#include "config-foo.h"
#include "config-bar.h"
#include <vespa/config/frt/protocol.h>
#include <vespa/config/print.h>
#include <vespa/config/retriever/configretriever.h>
#include <vespa/config/retriever/configsnapshot.hpp>
#include <vespa/config/retriever/simpleconfigretriever.h>
#include <vespa/config/retriever/simpleconfigurer.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/subscription/configsubscription.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <atomic>
#include <thread>

using namespace config;
using namespace std;
using namespace vespalib::slime;
using namespace vespalib;

struct ComponentFixture {
    typedef std::shared_ptr<ComponentFixture> SP;
    FooConfigBuilder fooBuilder;
    BarConfigBuilder barBuilder;
};

struct ConfigTestFixture {
    const std::string configId;
    BootstrapConfigBuilder bootstrapBuilder;
    map<std::string, ComponentFixture::SP> componentConfig;
    ConfigSet set;
    std::shared_ptr<IConfigContext> context;
    int idcounter;

    explicit ConfigTestFixture(const std::string & id)
        : configId(id),
          bootstrapBuilder(),
          componentConfig(),
          set(),
          context(std::make_shared<ConfigContext>(set)),
          idcounter(-1)
    {
        set.addBuilder(configId, &bootstrapBuilder);
    }
    ~ConfigTestFixture();

    void addComponent(const std::string & name, const std::string & fooValue, const std::string & barValue)
    {
        BootstrapConfigBuilder::Component component;
        component.name = name;
        component.configid = configId + "/" + name;
        bootstrapBuilder.component.push_back(component);

        ComponentFixture::SP fixture(new ComponentFixture());
        fixture->fooBuilder.fooValue = fooValue;
        fixture->barBuilder.barValue = barValue;
        set.addBuilder(component.configid, &fixture->fooBuilder);
        set.addBuilder(component.configid, &fixture->barBuilder);
        componentConfig[name] = fixture;
    }

    void removeComponent(const std::string & name)
    {
        for (BootstrapConfigBuilder::ComponentVector::iterator it(bootstrapBuilder.component.begin()),
                                                               mt(bootstrapBuilder.component.end()); it != mt; it++) {
            if ((*it).name.compare(name) == 0) {
                bootstrapBuilder.component.erase(it);
                break;
            }
        }
    }

    bool configEqual(const std::string & name, const FooConfig & fooConfig) {
        ComponentFixture::SP fixture(componentConfig[name]);
        return (fixture->fooBuilder == fooConfig);
    }

    bool configEqual(const std::string & name, const BarConfig & barConfig) {
        ComponentFixture::SP fixture(componentConfig[name]);
        return (fixture->barBuilder == barConfig);
    }

    bool configEqual(const BootstrapConfig & bootstrapConfig) {
        return (bootstrapBuilder == bootstrapConfig);
    }

    void reload() { context->reload(); }
};

ConfigTestFixture::~ConfigTestFixture() = default;

struct SimpleSetup
{
    ConfigKeySet bootstrapKeys;
    ConfigKeySet componentKeys;
    std::unique_ptr<ConfigRetriever> retriever;
    SimpleSetup(ConfigTestFixture & f1)
        : bootstrapKeys(), componentKeys(), retriever()
    {
        f1.addComponent("c1", "foo1", "bar1");
        bootstrapKeys.add<BootstrapConfig>(f1.configId);
        retriever.reset(new ConfigRetriever(bootstrapKeys, f1.context));
    }
    ~SimpleSetup();
};

SimpleSetup::~SimpleSetup() = default;

struct MySource : public Source
{
    ~MySource() override;
    void getConfig() override { }
    void close() override { }
    void reload(int64_t gen) override { (void) gen; }
};

MySource::~MySource() = default;

struct SubscriptionFixture
{
    std::shared_ptr<IConfigHolder> holder;
    std::shared_ptr<ConfigSubscription> sub;
    SubscriptionFixture(const ConfigKey & key, const ConfigValue value)
        : holder(std::make_shared<ConfigHolder>()),
          sub(std::make_shared<ConfigSubscription>(0, key, holder, std::make_unique<MySource>()))
    {
        holder->handle(std::make_unique<ConfigUpdate>(value, 3, 3));
        EXPECT_TRUE(sub->nextUpdate(0, steady_clock::now()));
        sub->flip();
    }
    ~SubscriptionFixture();
};

SubscriptionFixture::~SubscriptionFixture() = default;

namespace {

class FixedPayload : public protocol::Payload {
public:
    const Inspector & getSlimePayload() const override
    {
        return _data.get();
    }

    Slime & getData() {
        return _data;
    }
    ~FixedPayload() override;
private:
    Slime _data;
};

FixedPayload::~FixedPayload() = default;

}

ConfigValue createKeyValueV2(const std::string & key, const std::string & value)
{
    auto payload = std::make_unique<FixedPayload>();
    payload->getData().setObject().setString(key, Memory(value));
    return ConfigValue(std::move(payload), "");
}


TEST(ConfigRetrieverTest, require_that_basic_retriever_usage_works)
{
    ConfigTestFixture f1("myid");
    f1.addComponent("c1", "foo1", "bar1");
    f1.addComponent("c2", "foo2", "bar2");

    ConfigKeySet keys;
    keys.add<BootstrapConfig>(f1.configId);

    ConfigRetriever ret(keys, f1.context);
    ConfigSnapshot configs = ret.getBootstrapConfigs();
    ASSERT_EQ(1u, configs.size());

    std::unique_ptr<BootstrapConfig> bootstrapConfig = configs.getConfig<BootstrapConfig>(f1.configId);
    ASSERT_TRUE(f1.configEqual(*bootstrapConfig));

    {
        ConfigKeySet componentKeys;
        for (size_t i = 0; i < bootstrapConfig->component.size(); i++) {
            const std::string & configId(bootstrapConfig->component[i].configid);
            componentKeys.add<FooConfig>(configId);
        }
        configs = ret.getConfigs(componentKeys);
        ASSERT_EQ(2u, configs.size());
        ASSERT_TRUE(f1.configEqual("c1", *configs.getConfig<FooConfig>(bootstrapConfig->component[0].configid)));
        ASSERT_TRUE(f1.configEqual("c2", *configs.getConfig<FooConfig>(bootstrapConfig->component[1].configid)));
    }
    {
        ConfigKeySet componentKeys;
        for (size_t i = 0; i < bootstrapConfig->component.size(); i++) {
            const std::string & configId(bootstrapConfig->component[i].configid);
            componentKeys.add<BarConfig>(configId);
        }
        configs = ret.getConfigs(componentKeys);
        ASSERT_EQ(2u, configs.size());
        ASSERT_TRUE(f1.configEqual("c1", *configs.getConfig<BarConfig>(bootstrapConfig->component[0].configid)));
        ASSERT_TRUE(f1.configEqual("c2", *configs.getConfig<BarConfig>(bootstrapConfig->component[1].configid)));
    }
    {
        ConfigKeySet componentKeys;
        for (size_t i = 0; i < bootstrapConfig->component.size(); i++) {
            const std::string & configId(bootstrapConfig->component[i].configid);
            componentKeys.add<FooConfig>(configId);
            componentKeys.add<BarConfig>(configId);
        }
        configs = ret.getConfigs(componentKeys);

        ASSERT_EQ(4u, configs.size());
        ASSERT_TRUE(f1.configEqual("c1", *configs.getConfig<FooConfig>(bootstrapConfig->component[0].configid)));
        ASSERT_TRUE(f1.configEqual("c1", *configs.getConfig<BarConfig>(bootstrapConfig->component[0].configid)));
        ASSERT_TRUE(f1.configEqual("c2", *configs.getConfig<FooConfig>(bootstrapConfig->component[1].configid)));
        ASSERT_TRUE(f1.configEqual("c2", *configs.getConfig<BarConfig>(bootstrapConfig->component[1].configid)));
    }
}

TEST(ConfigRetrieverTest, require_that_SimpleConfigRetriever_usage_works) {
    ConfigSet set;
    FooConfigBuilder fooBuilder;
    BarConfigBuilder barBuilder;
    fooBuilder.fooValue = "barz";
    barBuilder.barValue = "fooz";
    set.addBuilder("id", &fooBuilder);
    set.addBuilder("id", &barBuilder);
    auto ctx = std::make_shared<ConfigContext>(set);
    ConfigKeySet sub;
    sub.add<FooConfig>("id");
    sub.add<BarConfig>("id");
    SimpleConfigRetriever retr(sub, ctx);
    ConfigSnapshot snap = retr.getConfigs();
    ASSERT_FALSE(snap.empty());
    ASSERT_EQ(2u, snap.size());
    std::unique_ptr<FooConfig> foo = snap.getConfig<FooConfig>("id");
    std::unique_ptr<BarConfig> bar = snap.getConfig<BarConfig>("id");
    ASSERT_EQ("barz", foo->fooValue);
    ASSERT_EQ("fooz", bar->barValue);
}

class ConfigurableFixture : public SimpleConfigurable
{
public:
    /**
     * Note that due to some bug in gcc 5.2 this file must be compiled with
     * -fno-tree-vrp which turns off some optimization. Or you can reorder the menbers here
     * and put 'snap' after the atomics.
     */
    ConfigurableFixture() __attribute__((noinline));
    virtual ~ConfigurableFixture() __attribute__((noinline));
    void configure(const ConfigSnapshot & snapshot) override {
        (void) snapshot;
        if (throwException) {
            throw ConfigRuntimeException("foo");
        }
        snap = snapshot;
        configured = true;
    }
    bool waitUntilConfigured(vespalib::duration timeout) {
        vespalib::Timer timer;
        while (timer.elapsed() < timeout) {
            if (configured) {
                return true;
            }
            std::this_thread::sleep_for(200ms);
        }
        return configured;
    }

    ConfigSnapshot snap;
    std::atomic<bool> configured;
    std::atomic<bool> throwException;
};

ConfigurableFixture::ConfigurableFixture() :
    configured(false),
    throwException(false)
{
}

ConfigurableFixture::~ConfigurableFixture()
{
}

TEST(ConfigRetrieverTest, require_that_SimpleConfigurer_usage_works)
{
    ConfigurableFixture f1;
    ConfigSet set;
    FooConfigBuilder fooBuilder;
    BarConfigBuilder barBuilder;
    fooBuilder.fooValue = "barz";
    barBuilder.barValue = "fooz";
    set.addBuilder("id", &fooBuilder);
    set.addBuilder("id", &barBuilder);
    auto ctx = std::make_shared<ConfigContext>(set);
    ConfigKeySet sub;
    sub.add<FooConfig>("id");
    sub.add<BarConfig>("id");
    SimpleConfigurer configurer(SimpleConfigRetriever::UP(new SimpleConfigRetriever(sub, ctx)), &f1);
    configurer.start();
    ASSERT_FALSE(f1.snap.empty());
    ASSERT_EQ(2u, f1.snap.size());
    ConfigSnapshot snap = f1.snap;
    std::unique_ptr<FooConfig> foo = snap.getConfig<FooConfig>("id");
    std::unique_ptr<BarConfig> bar = snap.getConfig<BarConfig>("id");
    ASSERT_EQ("barz", foo->fooValue);
    ASSERT_EQ("fooz", bar->barValue);

    f1.configured = false;
    fooBuilder.fooValue = "bimz";
    ctx->reload();
    ASSERT_TRUE(f1.waitUntilConfigured(60s));
    snap = f1.snap;
    foo = snap.getConfig<FooConfig>("id");
    ASSERT_EQ("bimz", foo->fooValue);
    configurer.close();
    fooBuilder.fooValue = "bamz";
    f1.configured = false;
    ctx->reload();
    ASSERT_FALSE(f1.waitUntilConfigured(2s));

    SimpleConfigurer configurer2(SimpleConfigRetriever::UP(new SimpleConfigRetriever(sub, ctx)), &f1);
    f1.throwException = true;
    VESPA_EXPECT_EXCEPTION(configurer2.start(), ConfigRuntimeException, "foo");
    configurer2.close();
}

TEST(ConfigRetrieverTest, require_that_variadic_templates_can_be_used_to_create_key_sets)
{
    ConfigKeySet set;
    set.add<FooConfig, BarConfig, BootstrapConfig>("myid");
    ASSERT_EQ(3u, set.size());
}

TEST(ConfigRetrieverTest, require_that_getBootstrapConfigs_returns_empty_snapshot_when_closed)
{
    ConfigTestFixture f1("myid");
    SimpleSetup f2(f1);
    ConfigSnapshot configs = f2.retriever->getBootstrapConfigs();
    ASSERT_TRUE(!configs.empty());
    ASSERT_FALSE(f2.retriever->isClosed());
    f2.retriever->close();
    ASSERT_TRUE(f2.retriever->isClosed());
    configs = f2.retriever->getBootstrapConfigs();
    ASSERT_TRUE(configs.empty());
}

TEST(ConfigRetrieverTest, require_that_getConfigs_throws_exception_when_closed)
{
    ConfigTestFixture f1("myid");
    SimpleSetup f2(f1);
    ConfigSnapshot configs = f2.retriever->getBootstrapConfigs();
    std::unique_ptr<BootstrapConfig> bootstrapConfig = configs.getConfig<BootstrapConfig>(f1.configId);
    ConfigKeySet componentKeys;
    for (size_t i = 0; i < bootstrapConfig->component.size(); i++) {
        const std::string & configId(bootstrapConfig->component[i].configid);
        componentKeys.add<FooConfig>(configId);
        componentKeys.add<BarConfig>(configId);
    }
    ASSERT_FALSE(f2.retriever->isClosed());
    f2.retriever->close();
    ASSERT_TRUE(f2.retriever->isClosed());
    configs = f2.retriever->getConfigs(componentKeys);
    ASSERT_TRUE(configs.empty());
}


TEST(ConfigRetrieverTest, require_that_snapshots_throws_exception_if_invalid_key)
{
    ConfigTestFixture f1("myid");
    SimpleSetup f2(f1);
    f1.addComponent("c3", "foo3", "bar3");
    ConfigSnapshot snap1 = f2.retriever->getBootstrapConfigs();
    ASSERT_FALSE(snap1.hasConfig<BarConfig>("doesnotexist"));
    VESPA_EXPECT_EXCEPTION(snap1.getConfig<BarConfig>("doesnotexist"), IllegalConfigKeyException, "Unable to find config for key name=config.bar,configId=doesnotexist");
    VESPA_EXPECT_EXCEPTION(snap1.isChanged<BarConfig>("doesnotexist", 0), IllegalConfigKeyException, "Unable to find config for key name=config.bar,configId=doesnotexist");
    ASSERT_TRUE(snap1.hasConfig<BootstrapConfig>("myid"));
}

TEST(ConfigRetrieverTest, require_that_snapshots_can_be_ignored)
{
    ConfigTestFixture f1("myid");
    SimpleSetup f2(f1);
    f1.addComponent("c3", "foo3", "bar3");
    ConfigSnapshot snap1 = f2.retriever->getBootstrapConfigs();
    int64_t lastGen = snap1.getGeneration();
    f1.reload();
    ASSERT_EQ(lastGen, snap1.getGeneration());
    ConfigSnapshot snap2 = f2.retriever->getBootstrapConfigs();
    ASSERT_EQ(snap2.getGeneration(), 2);
    ASSERT_TRUE(snap2.isChanged<BootstrapConfig>("myid", lastGen));
    ASSERT_FALSE(snap2.isChanged<BootstrapConfig>("myid", lastGen + 1));
    f1.reload();
    ConfigSnapshot snap3 = f2.retriever->getBootstrapConfigs();
    ASSERT_TRUE(snap3.isChanged<BootstrapConfig>("myid", lastGen));
    ASSERT_FALSE(snap3.isChanged<BootstrapConfig>("myid", lastGen + 1));
}

TEST(ConfigRetrieverTest, require_that_snapshots_can_produce_subsets)
{
    SubscriptionFixture f1(ConfigKey::create<FooConfig>("id"), createKeyValueV2("fooValue", "bar"));
    SubscriptionFixture f2(ConfigKey::create<BarConfig>("id"), createKeyValueV2("barValue", "foo"));
    ConfigSnapshot::SubscriptionList f3;
    f3.push_back(f1.sub);
    f3.push_back(f2.sub);
    ConfigSnapshot parent(f3, 3);
    ASSERT_FALSE(parent.empty());
    ASSERT_EQ(3, parent.getGeneration());
    ASSERT_EQ(2u, parent.size());

    ConfigSnapshot subset1(parent.subset(ConfigKeySet().add<FooConfig>("id")));
    ASSERT_FALSE(subset1.empty());
    ASSERT_EQ(3, subset1.getGeneration());
    ASSERT_EQ(1u, subset1.size());
    std::unique_ptr<FooConfig> cfg1(subset1.getConfig<FooConfig>("id"));
    ASSERT_TRUE(cfg1);

    ConfigSnapshot subset2(parent.subset(ConfigKeySet().add<BarConfig>("id")));
    ASSERT_FALSE(subset2.empty());
    ASSERT_EQ(3, subset2.getGeneration());
    ASSERT_EQ(1u, subset2.size());
    std::unique_ptr<BarConfig> cfg2(subset2.getConfig<BarConfig>("id"));
    ASSERT_TRUE(cfg2);

    ConfigSnapshot subset3(parent.subset(ConfigKeySet().add<BarConfig>("doesnotexist")));
    ASSERT_TRUE(subset3.empty());
    ASSERT_EQ(3, subset3.getGeneration());
    ASSERT_EQ(0u, subset3.size());

    ConfigSnapshot subset4(parent.subset(ConfigKeySet().add<BarConfig>("doesnotexist").add<FooConfig>("id").add<FooConfig>("nosuchthing").add<BarConfig>("id").add<BarConfig>("nothere")));
    ASSERT_FALSE(subset4.empty());
    ASSERT_EQ(3, subset4.getGeneration());
    ASSERT_EQ(2u, subset4.size());
    cfg1 = subset4.getConfig<FooConfig>("id");
    ASSERT_TRUE(cfg1);
    cfg2 = subset4.getConfig<BarConfig>("id");
    ASSERT_TRUE(cfg2);
}

TEST(ConfigRetrieverTest, require_that_snapshots_can_be_serialized)
{
    SubscriptionFixture f1(ConfigKey::create<FooConfig>("id"), createKeyValueV2("fooValue", "bar"));
    SubscriptionFixture f2(ConfigKey::create<BarConfig>("id"), createKeyValueV2("barValue", "foo"));
    ConfigSnapshot::SubscriptionList f3;
    f3.push_back(f1.sub);
    f3.push_back(f2.sub);
    ConfigSnapshot parent(f3, 3);

    typedef std::shared_ptr<ConfigSnapshotWriter> WSP;
    typedef std::shared_ptr<ConfigSnapshotReader> RSP;
    typedef std::pair<WSP, RSP> SerializePair;
    typedef std::vector<SerializePair> Vec;
    Vec vec;
    vespalib::asciistream ss;
    vec.push_back(SerializePair(WSP(new FileConfigSnapshotWriter("testsnapshot.txt")),
                                RSP(new FileConfigSnapshotReader("testsnapshot.txt"))));
    vec.push_back(SerializePair(WSP(new AsciiConfigSnapshotWriter(ss)),
                                RSP(new AsciiConfigSnapshotReader(ss))));
    for (Vec::iterator it(vec.begin()), mt(vec.end()); it != mt; it++) {
        ASSERT_TRUE(it->first->write(parent));
        ConfigSnapshot deser(it->second->read());
        ASSERT_EQ(parent.getGeneration(), deser.getGeneration());
        ASSERT_EQ(parent.size(), deser.size());
        ASSERT_TRUE(deser.hasConfig<FooConfig>("id"));
        ASSERT_TRUE(deser.hasConfig<BarConfig>("id"));
        std::unique_ptr<FooConfig> foo = deser.getConfig<FooConfig>("id");
        std::unique_ptr<BarConfig> bar = deser.getConfig<BarConfig>("id");
        ASSERT_EQ("bar", foo->fooValue);
        ASSERT_EQ("foo", bar->barValue);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
