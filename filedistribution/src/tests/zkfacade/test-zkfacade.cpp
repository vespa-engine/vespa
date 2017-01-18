// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN
#define BOOST_TEST_MODULE zkfacade test
#include <vespa/fastos/fastos.h>
#include <boost/test/unit_test.hpp>

#include <iostream>

#include <boost/thread/barrier.hpp>
#include <boost/checked_delete.hpp>

#include <vespa/filedistribution/common/componentsdeleter.h>
#include <vespa/filedistribution/model/zkfacade.h>

#include <zookeeper/zookeeper.h>


using namespace std::literals;
using namespace filedistribution;

namespace {


struct Watcher : public ZKFacade::NodeChangedWatcher {
    boost::barrier _barrier;

    Watcher() :
        _barrier(2) {}

    void operator()() {
        _barrier.wait();
    }
};

struct Fixture {
    ComponentsDeleter _componentsDeleter;
    std::shared_ptr<ZKFacade> zk;
    Path testNode;

    Fixture() {
        zoo_set_debug_level(ZOO_LOG_LEVEL_WARN);
        zk = _componentsDeleter.track(new ZKFacade("test1-tonyv:2181", false));

        testNode = "/test-node";
        zk->removeIfExists(testNode);
    }

    ~Fixture() {
        if (zk) {
            zk->removeIfExists(testNode);
        }
    }
};

} //anonymous namespace


BOOST_FIXTURE_TEST_SUITE(ZKFacadeTests, Fixture)

BOOST_AUTO_TEST_CASE(hasNode)
{
    zk->setData(testNode, "", 0);
    BOOST_CHECK(zk->hasNode(testNode));

    zk->remove(testNode);
    BOOST_CHECK(!zk->hasNode(testNode));
}

BOOST_AUTO_TEST_CASE(getValidZKServers)
{
    BOOST_CHECK_EQUAL("localhost:22", ZKFacade::getValidZKServers("localhost:22", false));
    BOOST_CHECK_EQUAL("localhost:22", ZKFacade::getValidZKServers("localhost:22", true));
    BOOST_CHECK_EQUAL("idonotexist:22", ZKFacade::getValidZKServers("idonotexist:22", false));
    BOOST_CHECK_EQUAL("", ZKFacade::getValidZKServers("idonotexist:22", true));
    BOOST_CHECK_EQUAL("localhost:22,idonotexist:22", ZKFacade::getValidZKServers("localhost:22,idonotexist:22", false));
    BOOST_CHECK_EQUAL("localhost:22", ZKFacade::getValidZKServers("localhost:22,idonotexist:22", true));
    BOOST_CHECK_EQUAL("idonotexist:22,localhost:22", ZKFacade::getValidZKServers("idonotexist:22,localhost:22", false));
    BOOST_CHECK_EQUAL("localhost:22", ZKFacade::getValidZKServers("idonotexist:22,localhost:22", true));
}

BOOST_AUTO_TEST_CASE(hasNodeNotification)
{
    std::shared_ptr<Watcher> watcher(new Watcher);

    zk->hasNode(testNode, watcher);
    zk->setData(testNode, "", 0);
    watcher->_barrier.wait();

    //after the notification has returned, the watcher must no longer reside in watchers map.
    for (int i=0; i<20 && !watcher.unique(); ++i)  {
        std::this_thread::sleep_for(100ms);
    }
    BOOST_CHECK(watcher.unique());
}

BOOST_AUTO_TEST_CASE(getAndSetData)
{
    std::string inputString = "test data.";
    Buffer inputBuffer(inputString.begin(), inputString.end());

    zk->setData(testNode, inputBuffer);

    Buffer outputBuffer(zk->getData(testNode));
    std::string outputString(outputBuffer.begin(), outputBuffer.end());

    BOOST_CHECK(outputString == inputString);

    outputString = zk->getString(testNode);
    BOOST_CHECK(outputString == inputString);
}

BOOST_AUTO_TEST_CASE(setDataMustExist)
{
    bool mustExist = true;
    BOOST_REQUIRE_THROW(zk->setData(testNode, "", 0, mustExist),  ZKNodeDoesNotExistsException);
}

BOOST_AUTO_TEST_CASE(createSequenceNode)
{
    zk->setData(testNode, "", 0);

    Path prefix = testNode / "prefix";
    zk->createSequenceNode(prefix, "test", 4);
    zk->createSequenceNode(prefix, "test", 4);
    zk->createSequenceNode(prefix, "test", 4);

    std::vector<std::string> children = zk->getChildren(testNode);
    BOOST_CHECK(children.size() == 3);
    BOOST_CHECK(children.begin()->substr(0,6) == "prefix");

    Buffer buffer(zk->getData(testNode / *children.begin()));
    std::string bufferContent(buffer.begin(), buffer.end());

    BOOST_CHECK(bufferContent == "test");
}

BOOST_AUTO_TEST_CASE(retainOnly)
{
    zk->setData(testNode, "", 0);

    zk->setData(testNode / "a", "", 0);
    zk->setData(testNode / "b", "", 0);
    zk->setData(testNode / "c", "", 0);
    zk->setData(testNode / "d", "", 0);

    std::vector<std::string> toRetain;
    toRetain.push_back("a");
    toRetain.push_back("c");

    zk->retainOnly(testNode, toRetain);
    std::vector<std::string> children = zk->getChildren(testNode);

    std::sort(children.begin(), children.end());
    BOOST_CHECK(children == toRetain);
}



BOOST_AUTO_TEST_CASE(addEphemeralNode)
{
    Path ephemeralNode = "/test-ephemeral-node";
    zk->removeIfExists(ephemeralNode);

    //Checked deleter is ok here since we're not installing any watchers
    ZKFacade::SP zk2(new ZKFacade("test1-tonyv:2181", false), boost::checked_deleter<ZKFacade>());
    zk2->addEphemeralNode(ephemeralNode);

    BOOST_CHECK(zk->hasNode(ephemeralNode));
    zk2.reset();
    BOOST_CHECK(!zk->hasNode(ephemeralNode));
}



BOOST_AUTO_TEST_CASE(dataChangedNotification)
{
    std::shared_ptr<Watcher> watcher(new Watcher);

    zk->setData(testNode, "", 0);
    Buffer buffer(zk->getData(testNode, watcher));
    BOOST_CHECK(buffer.size() == 0);

    bool mustExist = true;
    zk->setData(testNode, "test",  4, mustExist);
    watcher->_barrier.wait();
}

BOOST_AUTO_TEST_CASE(getChildrenNotification)
{
    std::shared_ptr<Watcher> watcher(new Watcher);

    zk->setData(testNode, "", 0);
    zk->getChildren(testNode, watcher);

    zk->setData(testNode / "child", "", 0);
    watcher->_barrier.wait();
}

BOOST_AUTO_TEST_CASE(require_that_zkfacade_can_be_deleted_from_callback)
{
    struct DeleteZKFacadeWatcher : public Watcher {
        std::shared_ptr<ZKFacade> _zk;

        DeleteZKFacadeWatcher(const std::shared_ptr<ZKFacade>& zk)
            :_zk(zk)
        {}

        void operator()() {
            BOOST_CHECK(_zk.use_count() == 2);
            _zk.reset();
            Watcher::operator()();
        }
    };

    std::shared_ptr<Watcher> watcher((Watcher*)new DeleteZKFacadeWatcher(zk));

    zk->setData(testNode, "", 0);
    zk->getData(testNode, watcher);

    ZKFacade* unprotectedZk = zk.get();
    zk.reset();

    unprotectedZk->setData(testNode, "t", 1);
    watcher->_barrier.wait();

    //Must wait longer than the zookeeper_close timeout to catch
    //problems due to closing zookeeper in a zookeeper watcher thread.
    sleep(3);
}

BOOST_AUTO_TEST_SUITE_END()
