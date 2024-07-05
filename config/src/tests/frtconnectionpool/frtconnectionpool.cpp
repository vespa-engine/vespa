// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/frt/frtconnectionpool.h>
#include <vespa/fnet/frt/error.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <sstream>
#include <set>
#include <unistd.h>

using namespace config;

class FRTConnectionPoolTest : public testing::Test {
protected:
    ServerSpec::HostSpecList _sources;
    FNET_Transport    _transport;
    void verifyAllSourcesInRotation(FRTConnectionPool& sourcePool);
    FRTConnectionPoolTest();
    ~FRTConnectionPoolTest() override;
};

FRTConnectionPoolTest::FRTConnectionPoolTest()
    : testing::Test(),
      _sources(),
      _transport()
{
    _sources.push_back("host0");
    _sources.push_back("host1");
    _sources.push_back("host2");
    _transport.Start();
}

FRTConnectionPoolTest::~FRTConnectionPoolTest() {
    _transport.ShutDown(true);
}

TimingValues timingValues;

void FRTConnectionPoolTest::verifyAllSourcesInRotation(FRTConnectionPool& sourcePool) {
    std::set<std::string> completeSet(_sources.begin(), _sources.end());
    std::set<std::string> foundSet;
    for (int i = 0; i < (int)_sources.size(); i++) {
        foundSet.insert(sourcePool.getNextRoundRobin()->getAddress());
    }
    EXPECT_EQ(true, completeSet == foundSet);
}

/**
 * Tests that basic round robin selection through the list works.
 */
TEST_F(FRTConnectionPoolTest, test_basic_round_robin)
{
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    for (int i = 0; i < 9; i++) {
        int j = i % _sources.size();
        std::stringstream s;
        s << "host" << j;
        EXPECT_EQ(s.str(), sourcePool.getNextRoundRobin()->getAddress());
    }
}

/**
 * Tests that hash-based selection through the list works.
 */
TEST_F(FRTConnectionPoolTest, test_basic_hash_based_selection)
{
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    sourcePool.setHostname("a.b.com");
    for (int i = 0; i < 9; i++) {
        EXPECT_EQ("host1", sourcePool.getNextHashBased()->getAddress());
    }
    sourcePool.setHostname("host98");
    for (int i = 0; i < 9; i++) {
        EXPECT_EQ("host0", sourcePool.getNextHashBased()->getAddress());
    }

    ServerSpec::HostSpecList hostnames;
    hostnames.push_back("sutter-01.example.yahoo.com");
    hostnames.push_back("stroustrup-02.example.yahoo.com");
    hostnames.push_back("alexandrescu-03.example.yahoo.com");
    const ServerSpec spec2(hostnames);
    FRTConnectionPool sourcePool2(_transport, spec2, timingValues);
    sourcePool2.setHostname("sutter-01.example.yahoo.com");
    EXPECT_EQ("stroustrup-02.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
    sourcePool2.setHostname("stroustrup-02.example.yahoo.com");
    EXPECT_EQ("sutter-01.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
    sourcePool2.setHostname("alexandrescu-03.example.yahoo.com");
    EXPECT_EQ("alexandrescu-03.example.yahoo.com", sourcePool2.getNextHashBased()->getAddress());
}

/**
 * Tests that a source is taken out of rotation when an error is reported,
 * and that it is taken back in when a success is reported.
 */
TEST_F(FRTConnectionPoolTest, test_set_error_round_robin)
{
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* source = sourcePool.getNextRoundRobin();
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < 9; i++) {
        EXPECT_NE(source->getAddress(), sourcePool.getCurrent()->getAddress());
    }
    source->setSuccess();
    verifyAllSourcesInRotation(sourcePool);
}

/**
 * Tests that all sources are in rotation when all sources have errors set.
 */
TEST_F(FRTConnectionPoolTest, test_set_error_all_round_robin)
{
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
TEST_F(FRTConnectionPoolTest, test_set_error_hash_based)
{
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* source = sourcePool.getNextHashBased();
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < (int)_sources.size(); i++) {
        EXPECT_NE(source->getAddress(), sourcePool.getNextHashBased()->getAddress());
    }
    source->setSuccess();
    EXPECT_EQ(source->getAddress(), sourcePool.getNextHashBased()->getAddress());
}

/**
 * Tests that the same source is used when all sources have errors set.
 */
TEST_F(FRTConnectionPoolTest, test_set_error_all_hash_based)
{
    const ServerSpec spec(_sources);
    FRTConnectionPool sourcePool(_transport, spec, timingValues);
    FRTConnection* firstSource = sourcePool.getNextHashBased();
    auto readySources = sourcePool.getReadySources();
    for (int i = 0; i < (int)readySources.size(); i++) {
        readySources[i]->setError(FRTE_RPC_CONNECTION);
    }
    EXPECT_EQ(sourcePool.getReadySources().size(), 0u);
    EXPECT_EQ(sourcePool.getSuspendedSources().size(), 3u);

    // should get the same source now, since all are suspended
    EXPECT_EQ(firstSource->getAddress(), sourcePool.getNextHashBased()->getAddress());

    // set all except firstSource to OK
    for (int i = 0; i < (int)readySources.size(); i++) {
        if (readySources[i]->getAddress() != firstSource->getAddress()) {
            readySources[i]->setSuccess();
        }
    }

    EXPECT_EQ(sourcePool.getReadySources().size(), 2u);
    EXPECT_EQ(sourcePool.getSuspendedSources().size(), 1u);

    // should not get the same source now, since original source is
    // suspended, while the rest are OK
    EXPECT_NE(firstSource->getAddress(), sourcePool.getNextHashBased()->getAddress());
}

/**
 * Tests that the source is put back into rotation when the suspension times out.
 */
TEST_F(FRTConnectionPoolTest, test_suspension_timeout)
{
    const ServerSpec spec(_sources);
    TimingValues short_transient_delay;
    short_transient_delay.transientDelay = 1s;
    FRTConnectionPool sourcePool(_transport, spec, short_transient_delay);
    FRTConnection* source = dynamic_cast<FRTConnection *>(sourcePool.getCurrent());
    source->setError(FRTE_RPC_CONNECTION);
    for (int i = 0; i < 9; i++) {
        EXPECT_NE(source->getAddress(), sourcePool.getCurrent()->getAddress());
    }
    sleep(2);
    verifyAllSourcesInRotation(sourcePool);
}

/**
 * Tests that when there are two sources and several clients
 * the sources will be chosen with equal probability.
 */
TEST_F(FRTConnectionPoolTest, test_many_sources)
{
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
    EXPECT_EQ(timesUsed["host0"], (int)hostnames.size() / 2);
    EXPECT_EQ(timesUsed["host1"], (int)hostnames.size() / 2);
}

GTEST_MAIN_RUN_ALL_TESTS()
