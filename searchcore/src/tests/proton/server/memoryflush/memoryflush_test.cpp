// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/flushengine/flushcontext.h>
#include <vespa/searchcore/proton/flushengine/tls_stats_map.h>
#include <vespa/searchcore/proton/test/dummy_flush_target.h>
#include <vespa/searchcore/proton/server/memoryflush.h>
#include <vespa/vespalib/util/size_literals.h>

using vespalib::system_time;
using search::SerialNum;
using namespace proton;
using namespace searchcorespi;

typedef IFlushTarget::MemoryGain MemoryGain;
typedef IFlushTarget::DiskGain DiskGain;

class MyFlushHandler : public IFlushHandler {
public:
    MyFlushHandler(const vespalib::string &name) noexcept : IFlushHandler(name) {}
    std::vector<IFlushTarget::SP> getFlushTargets() override {
        return std::vector<IFlushTarget::SP>();
    }
    SerialNum getCurrentSerialNumber() const override { return 0; }
    void flushDone(SerialNum oldestSerial) override { (void) oldestSerial; }
    void syncTls(search::SerialNum syncTo) override {(void) syncTo;}
};

class MyFlushTarget : public test::DummyFlushTarget {
private:
    MemoryGain    _memoryGain;
    DiskGain      _diskGain;
    SerialNum     _flushedSerial;
    system_time  _lastFlushTime;
    bool          _urgentFlush;
public:
    MyFlushTarget(const vespalib::string &name, MemoryGain memoryGain,
                  DiskGain diskGain, SerialNum flushedSerial,
                  system_time lastFlushTime, bool urgentFlush) noexcept :
        test::DummyFlushTarget(name),
        _memoryGain(memoryGain),
        _diskGain(diskGain),
        _flushedSerial(flushedSerial),
        _lastFlushTime(lastFlushTime),
        _urgentFlush(urgentFlush)
    {
    }
    MemoryGain getApproxMemoryGain() const override { return _memoryGain; }
    DiskGain getApproxDiskGain() const override { return _diskGain; }
    SerialNum getFlushedSerialNum() const override { return _flushedSerial; }
    system_time getLastFlushTime() const override { return _lastFlushTime; }
    bool needUrgentFlush() const override { return _urgentFlush; }
};

struct StringList : public std::vector<vespalib::string> {
    StringList() : std::vector<vespalib::string>() {}
    StringList &add(const vespalib::string &str) {
        push_back(str);
        return *this;
    }
};

class ContextBuilder {
private:
    FlushContext::List _list;
    IFlushHandler::SP  _handler;
    flushengine::TlsStatsMap::Map _map;
    void
    fixupMap(const vespalib::string &name, SerialNum lastSerial)
    {
        flushengine::TlsStats oldStats = _map[name];
        if (oldStats.getLastSerial() < lastSerial) {
            _map[name] =
                flushengine::TlsStats(oldStats.getNumBytes(),
                                      oldStats.getFirstSerial(),
                                      lastSerial);
        }
    }
public:
    ContextBuilder();
    ~ContextBuilder();
    void addTls(const vespalib::string &name,
                const flushengine::TlsStats &tlsStats) {
        _map[name] = tlsStats;
    }
    ContextBuilder &add(const FlushContext::SP &context) {
        _list.push_back(context);
        fixupMap(_handler->getName(), context->getLastSerial());
        return *this;
    }
    ContextBuilder &add(const IFlushTarget::SP &target, SerialNum lastSerial = 0) {
        FlushContext::SP ctx(new FlushContext(_handler, target, lastSerial));
        return add(ctx);
    }
    const FlushContext::List &list() const { return _list; }
    flushengine::TlsStatsMap tlsStats() const {
        flushengine::TlsStatsMap::Map map(_map);
        return flushengine::TlsStatsMap(std::move(map));
    }
};

using minutes = std::chrono::minutes;
using seconds = std::chrono::seconds;

ContextBuilder::ContextBuilder()
    : _list(), _handler(new MyFlushHandler("myhandler"))
{}
ContextBuilder::~ContextBuilder() = default;

MyFlushTarget::SP
createTargetM(const vespalib::string &name, MemoryGain memoryGain)
{
    return std::make_shared<MyFlushTarget>(name, memoryGain, DiskGain(),SerialNum(), system_time(), false);
}

MyFlushTarget::SP
createTargetD(const vespalib::string &name, DiskGain diskGain, SerialNum serial = 0)
{
    return std::make_shared<MyFlushTarget>(name, MemoryGain(), diskGain, serial, system_time(), false);
}

MyFlushTarget::SP
createTargetS(const vespalib::string &name, SerialNum serial, system_time timeStamp = system_time())
{
    return std::make_shared<MyFlushTarget>(name, MemoryGain(), DiskGain(), serial, timeStamp, false);
}

MyFlushTarget::SP
createTargetT(const vespalib::string &name, system_time lastFlushTime, SerialNum serial = 0)
{
    return std::make_shared<MyFlushTarget>(name, MemoryGain(), DiskGain(), serial, lastFlushTime, false);
}

MyFlushTarget::SP
createTargetF(const vespalib::string &name, bool urgentFlush)
{
    return std::make_shared<MyFlushTarget>(name, MemoryGain(), DiskGain(), SerialNum(), system_time(), urgentFlush);
}

bool
assertOrder(const StringList &exp, const FlushContext::List &act)
{
    if (!EXPECT_EQUAL(exp.size(), act.size()))
        return false;
    for (size_t i = 0; i < exp.size(); ++i) {
        if (!EXPECT_EQUAL(exp[i], act[i]->getTarget()->getName())) return false;
    }
    return true;
}

void
requireThatWeCanOrderByMemoryGain()
{
    ContextBuilder cb;
    cb.add(createTargetM("t2", MemoryGain(10, 0)))
      .add(createTargetM("t1", MemoryGain(5, 0)))
      .add(createTargetM("t4", MemoryGain(20, 0)))
      .add(createTargetM("t3", MemoryGain(15, 0)));
    { // target t4 has memoryGain >= maxMemoryGain
        MemoryFlush flush({1000, 20_Gi, 1.0, 20, 1.0, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // trigger totalMemoryGain >= globalMaxMemory
        MemoryFlush flush({50, 20_Gi, 1.0, 1000, 1.0, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

int64_t milli = 1000000;

void
requireThatWeCanOrderByDiskGainWithLargeValues()
{
    ContextBuilder cb;
    int64_t before = 100 * milli;
    cb.add(createTargetD("t2", DiskGain(before, 70 * milli)))  // gain 30M
      .add(createTargetD("t1", DiskGain(before, 75 * milli)))  // gain 25M
      .add(createTargetD("t4", DiskGain(before, 45 * milli)))  // gain 55M
      .add(createTargetD("t3", DiskGain(before, 50 * milli))); // gain 50M
    { // target t4 has diskGain > bloatValue
        // t4 gain: 55M / 100M = 0.55 -> bloat factor 0.54 to trigger
        MemoryFlush flush({1000, 20_Gi, 10.0, 1000, 0.54, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // trigger totalDiskGain > totalBloatValue
        // total gain: 160M / 4 * 100M = 0.4 -> bloat factor 0.39 to trigger
        MemoryFlush flush({1000, 20_Gi, 0.39, 1000, 10.0, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

void
requireThatWeCanOrderByDiskGainWithSmallValues()
{
    ContextBuilder cb;
    cb.add(createTargetD("t2", DiskGain(100, 70)))  // gain 30
      .add(createTargetD("t1", DiskGain(100, 75)))  // gain 25
      .add(createTargetD("t4", DiskGain(100, 45)))  // gain 55
      .add(createTargetD("t3", DiskGain(100, 50))); // gain 50
    // total disk bloat value calculation uses min 100M disk size
    // target bloat value calculation uses min 100M disk size
    { // target t4 has diskGain > bloatValue
        // t4 gain: 55 / 100M = 0.0000055 -> bloat factor 0.0000054 to trigger
        MemoryFlush flush({1000, 20_Gi, 10.0, 1000, 0.00000054, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // trigger totalDiskGain > totalBloatValue
        // total gain: 160 / 100M = 0.0000016 -> bloat factor 0.0000015 to trigger
        MemoryFlush flush({1000, 20_Gi, 0.0000015, 1000, 10.0, minutes(1)});
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

void
requireThatWeCanOrderByAge()
{
    system_time now(vespalib::system_clock::now());
    system_time start(now - seconds(20));
    ContextBuilder cb;
    cb.add(createTargetT("t2", now - seconds(10)))
      .add(createTargetT("t1", now - seconds(5)))
      .add(createTargetT("t4", system_time()))
      .add(createTargetT("t3", now - seconds(15)));

    { // all targets have timeDiff >= maxTimeGain
        MemoryFlush flush({1000, 20_Gi, 1.0, 1000, 1.0, seconds(2)}, start);
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t3").add("t2").add("t1"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // no targets have timeDiff >= maxTimeGain
        MemoryFlush flush({1000, 20_Gi, 1.0, 1000, 1.0, seconds(30)}, start);
        EXPECT_TRUE(assertOrder(StringList(), flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

void
requireThatWeCanOrderByTlsSize()
{
    system_time now(vespalib::system_clock::now());
    system_time start = now - seconds(20);
    ContextBuilder cb;
    IFlushHandler::SP handler1(std::make_shared<MyFlushHandler>("handler1"));
    IFlushHandler::SP handler2(std::make_shared<MyFlushHandler>("handler2"));
    cb.addTls("handler1", {20_Gi, 1001, 2000 });
    cb.addTls("handler2", { 5_Gi, 1001, 2000 });
    cb.add(std::make_shared<FlushContext>(handler1, createTargetT("t2", now - seconds(10), 1900), 2000)).
        add(std::make_shared<FlushContext>(handler2, createTargetT("t1", now - seconds(5), 1000), 2000)).
        add(std::make_shared<FlushContext>(handler1, createTargetT("t4", system_time(), 1000), 2000)).
        add(std::make_shared<FlushContext>(handler2, createTargetT("t3", now - seconds(15), 1900), 2000));
    { // sum of tls sizes above limit, trigger sort order based on tls size
        MemoryFlush flush({1000, 3_Gi, 1.0, 1000, 1.0, seconds(2)}, start);
        EXPECT_TRUE(assertOrder(StringList().add("t4").add("t1").add("t2").add("t3"),
                                flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // sum of tls sizes below limit
        MemoryFlush flush({1000, 30_Gi, 1.0, 1000, 1.0, seconds(30)}, start);
        EXPECT_TRUE(assertOrder(StringList(), flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

void
requireThatWeHandleLargeSerialNumbersWhenOrderingByTlsSize()
{
    uint64_t uint32_max = std::numeric_limits<uint32_t>::max();
    ContextBuilder builder;
    SerialNum firstSerial = 10;
    SerialNum lastSerial = uint32_max + 10;
    builder.addTls("myhandler", {uint32_max, firstSerial, lastSerial});
    builder.add(createTargetT("t1", system_time(), uint32_max + 5), lastSerial);
    builder.add(createTargetT("t2", system_time(), uint32_max - 5), lastSerial);
    uint64_t maxMemoryGain = 10;
    MemoryFlush flush({maxMemoryGain, 1000, 0, maxMemoryGain, 0, vespalib::duration(0)}, system_time());
    EXPECT_TRUE(assertOrder(StringList().add("t2").add("t1"), flush.getFlushTargets(builder.list(), builder.tlsStats())));
}

void
requireThatOrderTypeIsPreserved()
{
    system_time now(vespalib::system_clock::now());
    system_time ts2 = now - seconds(20);

    { // MAXAGE VS DISKBLOAT
        ContextBuilder cb;
        cb.add(createTargetT("t2", ts2, 5), 14)
          .add(createTargetD("t1", DiskGain(100 * milli, 80 * milli), 5));
        MemoryFlush flush({1000, 20_Gi, 1.0, 1000, 0.19, seconds(30)});
        EXPECT_TRUE(assertOrder(StringList().add("t1").add("t2"), flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // DISKBLOAT VS MEMORY
        ContextBuilder cb;
        cb.add(createTargetD("t2", DiskGain(100 * milli, 80 * milli)))
          .add(createTargetM("t1", MemoryGain(100, 80)));
        MemoryFlush flush({1000, 20_Gi, 1.0, 20, 0.19, seconds(30)});
        EXPECT_TRUE(assertOrder(StringList().add("t1").add("t2"), flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
    { // urgent flush
        ContextBuilder cb;
        cb.add(createTargetF("t2", false))
          .add(createTargetF("t1", true));
        MemoryFlush flush({1000, 20_Gi, 1.0, 1000, 1.0, seconds(30)});
        EXPECT_TRUE(assertOrder(StringList().add("t1").add("t2"), flush.getFlushTargets(cb.list(), cb.tlsStats())));
    }
}

TEST_MAIN()
{
    TEST_DO(requireThatWeCanOrderByMemoryGain());
    TEST_DO(requireThatWeCanOrderByDiskGainWithLargeValues());
    TEST_DO(requireThatWeCanOrderByDiskGainWithSmallValues());
    TEST_DO(requireThatWeCanOrderByAge());
    TEST_DO(requireThatWeCanOrderByTlsSize());
    TEST_DO(requireThatWeHandleLargeSerialNumbersWhenOrderingByTlsSize());
    TEST_DO(requireThatOrderTypeIsPreserved());
}
