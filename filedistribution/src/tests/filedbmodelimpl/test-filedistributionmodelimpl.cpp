// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN
#define BOOST_TEST_MODULE filedbmodelimpl test
#include <boost/test/unit_test.hpp>

#include <iostream>
#include <vector>
#include <vespa/filedistribution/common/componentsdeleter.h>
#include <vespa/filedistribution/model/filedistributionmodelimpl.h>
#include <vespa/filedistribution/model/zkfacade.h>
#include <zookeeper/zookeeper.h>


using namespace filedistribution;


namespace {


struct Fixture {
    ComponentsDeleter _componentsDeleter;
    std::shared_ptr<ZKFacade> _zk;
    std::shared_ptr<FileDistributionModelImpl> _distModel;
    Fixture() {
        _zk = _componentsDeleter.track(new ZKFacade("test1-tonyv:2181"));
        _distModel.reset(new FileDistributionModelImpl("hostname", 12345, _zk));       
     }
    ~Fixture() { }
};

} //anonymous namespace


BOOST_FIXTURE_TEST_SUITE(FileDistributionModelImplTests, Fixture)

BOOST_AUTO_TEST_CASE(configServersAsPeers)
{
    std::vector<std::string> peers;
    peers.push_back("old");
    peers.push_back("config:123");
    peers.push_back("config:567");
    peers.push_back("foo:123");
    _distModel->addConfigServersAsPeers(peers, "config,configTwo", 123);
    BOOST_CHECK(peers.size() == 5);
    BOOST_CHECK(peers[4] == "configTwo:123");
    _distModel->addConfigServersAsPeers(peers, NULL, 123);
    BOOST_CHECK(peers.size() == 5);
}

BOOST_AUTO_TEST_SUITE_END()
