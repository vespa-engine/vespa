// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/config/frt/frtconnectionpool.h>
#include <vespa/fnet/frt/error.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/size_literals.h>
#include <sstream>
#include <set>
#include <unistd.h>

using namespace config;

class Test : public vespalib::TestApp {
private:
    static ServerSpec::HostSpecList _sources;
    FastOS_ThreadPool _threadPool;
    FNET_Transport    _transport;
    void verifyAllSourcesInRotation(FRTConnectionPool& sourcePool);
public:
    Test();
    ~Test() override;
    int Main() override;
    void testBasicRoundRobin();
    void testBasicHashBasedSelection();
    void testSetErrorRoundRobin();
    void testSetErrorAllRoundRobin();
    void testSetErrorHashBased();
    void testSetErrorAllHashBased();
    void testSuspensionTimeout();
    void testManySources();
};

Test::Test()
    : vespalib::TestApp(),
      _threadPool(64_Ki),
      _transport()
{
    _transport.Start(&_threadPool);
}

Test::~Test() {
    _transport.ShutDown(true);
}

TEST_APPHOOK(Test);

ServerSpec::HostSpecList Test::_sources;
TimingValues timingValues;

int Test::Main() {
    TEST_INIT("frtconnectionpool_test");

    _sources.push_back("host0");
    _sources.push_back("host1");
    _sources.push_back("host2");

    testBasicRoundRobin();
    TEST_FLUSH();

    testBasicHashBasedSelection();
    TEST_FLUSH();

    testSetErrorRoundRobin();
    TEST_FLUSH();

    testSetErrorAllRoundRobin();
    TEST_FLUSH();

    testSetErrorHashBased();
    TEST_FLUSH();

    testSetErrorAllHashBased();
    TEST_FLUSH();

    testSuspensionTimeout();
    TEST_FLUSH();

    testManySources();
    TEST_FLUSH();

    TEST_DONE();
    return 0;
}

void Test::verifyAllSourcesInRotation(FRTConnectionPool& sourcePool) {
    std::set<std::string> completeSet(_sources.begin(), _sources.end());
    std::set<std::string> foundSet;
    for (int i = 0; i < (int)_sources.size(); i++) {
        foundSet.insert(sourcePool.getNextRoundRobin()->getAddress());
    }
    EXPECT_EQUAL(true, completeSet == foundSet);
}

/**
 * Tests that basic round robin selection through the list works.
 */
void Test::testBasicRoundRobin() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    for (int i = 0; i < 9; i++) {
        int j = i % _sources.size();
        std::stringstream s;
        s << "host" << j;
        EXPECT_EQUAL(s.str(), sourcePool.getNextRoundRobin()->getAddress());
    }
}

/**
 * Tests that hash-based selection through the list works.
 */
void Test::testBasicHashBasedSelection() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    sourcePool.setHostname("a.b.com");
    for (int i = 0; i < 9; i++) {
        EXPECT_EQUAL("host1", sourcePool.getNextHashBased()->getAddress());
    }
    sourcePool.setHostname("host98");
    for (int i = 0; i < 9; i++) {
        EXPECT_EQUAL("host0", sourcePool.getNextHashBased()->getAddress());
    }

    ServerSpec::HostSpecList hostnames;
    hostnames.push_back("sutter-01.example.yahoo.com");
    hostnames.push_back("stroustrup-02.example.yahoo.com");
    hostnames.push_back("alexandrescu-03.example.yahoo.com");
    const ServerSpec spec2(hostnames);
    FRTConnectionPool sourcePool2(_transport, spec2, timingValues);
    sourcePool2.setHostname("sutter-01.example.yahoo.com");
    EXPECT_EQUAL("stroustrup-02.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
    sourcePool2.setHostname("stroustrup-02.example.yahoo.com");
    EXPECT_EQUAL("sutter-01.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
    sourcePool2.setHostname("alexandrescu-03.example.yahoo.com");
    EXPECT_EQUAL("alexandrescu-03.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
}

/**
 * Tests that a source is taken out of rotation when an error is reported,
 * and that it is taken back in when a success is reported.
 */
void Test::testSetErrorRoundRobin() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* source = sourcePool.getNextRoundRobin();
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < 9; i++) {
        EXPECT_NOT_EQUAL(source->getAddress(), sourcePool.getCurrent()->getAddress());
    }
    source->setSuccess();
    verifyAllSourcesInRotation(sourcePool);
}

/**
 * Tests that all sources are in rotation when all sources have errors set.
 */
void Test::testSetErrorAllRoundRobin() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    for (int i = 0; i < (int)_sources.size(); i++) {
        FRTConnection* source = sourcePool.getNextRoundRobin();
        source->setError(FRTE_RPC_CONNECTION);
    }
    verifyAllSourcesInRotation(sourcePool);
}

/**
 * Tests that a source is not used when an error is reported,
 * and that the same source is used when a success is reported.
 */
void Test::testSetErrorHashBased() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* source = sourcePool.getNextHashBased();
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < (int)_sources.size(); i++) {
        EXPECT_NOT_EQUAL(source->getAddress(), sourcePool.getNextHashBased()->getAddress());
    }
    source->setSuccess();
    EXPECT_EQUAL(source->getAddress(), sourcePool.getNextHashBased()->getAddress());
}

/**
 * Tests that the same source is used when all sources have errors set.
 */
void Test::testSetErrorAllHashBased() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* firstSource = sourcePool.getNextHashBased();
    auto readySources = sourcePool.getReadySources();
    for (int i = 0; i < (int)readySources.size(); i++) {
        readySources[i]->setError(FRTE_RPC_CONNECTION);
    }
    EXPECT_EQUAL(sourcePool.getReadySources().size(), 0u);
    EXPECT_EQUAL(sourcePool.getSuspendedSources().size(), 3u);

    // should get the same source now, since all are suspended
    EXPECT_EQUAL(firstSource->getAddress(), sourcePool.getNextHashBased()->getAddress());

    // set all except firstSource to OK
    for (int i = 0; i < (int)readySources.size(); i++) {
        if (readySources[i]->getAddress() != firstSource->getAddress()) {
            readySources[i]->setSuccess();
        }
    }

    EXPECT_EQUAL(sourcePool.getReadySources().size(), 2u);
    EXPECT_EQUAL(sourcePool.getSuspendedSources().size(), 1u);

    // should not get the same source now, since original source is
    // suspended, while the rest are OK
    EXPECT_NOT_EQUAL(firstSource->getAddress(), sourcePool.getNextHashBased()->getAddress());
}

/**
 * Tests that the source is put back into rotation when the suspension times out.
 */
void Test::testSuspensionTimeout() {
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    Connection* source = sourcePool.getCurrent();
    source->setTransientDelay(1s);
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < 9; i++) {
        EXPECT_NOT_EQUAL(source->getAddress(), sourcePool.getCurrent()->getAddress());
    }
    sleep(2);
    verifyAllSourcesInRotation(sourcePool);
}

/**
 * Tests that when there are two sources and several clients
 * the sources will be chosen with equal probability.
 */
void Test::testManySources() {
    std::vector<std::string> hostnames;
    for (int i = 0; i < 20; ++i) {
        hostnames.push_back("host-" + std::to_string(i) + ".example.yahoo.com");
    }

    std::map<std::string, int> timesUsed;
    ServerSpec::HostSpecList twoSources;

    twoSources.push_back("host0");
    twoSources.push_back("host1");

    const ServerSpec spec(twoSources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);

    for (int i = 0; i < (int)hostnames.size(); i++) {
        sourcePool.setHostname(hostnames[i]);
        std::string address = sourcePool.getNextHashBased()->getAddress();
        if (timesUsed.find(address) != timesUsed.end()) {
            int times = timesUsed[address];
            timesUsed[address] = times + 1;
        } else {
            timesUsed[address] = 1;
        }
    }
    EXPECT_EQUAL(timesUsed["host0"], (int)hostnames.size() / 2);
    EXPECT_EQUAL(timesUsed["host1"], (int)hostnames.size() / 2);
}
