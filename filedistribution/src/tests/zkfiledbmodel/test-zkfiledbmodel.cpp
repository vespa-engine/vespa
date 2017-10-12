// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN
#define BOOST_TEST_MODULE zkfiledbmodel test
#include <boost/test/unit_test.hpp>

#include <iostream>

#include <vespa/filedistribution/common/componentsdeleter.h>
#include <vespa/filedistribution/model/zkfacade.h>
#include <vespa/filedistribution/model/zkfiledbmodel.h>

#include <zookeeper/zookeeper.h>


using namespace filedistribution;

namespace {

struct Fixture {
    ComponentsDeleter _componentsDeleter;
    std::shared_ptr<ZKFacade> zk;
    std::shared_ptr<ZKFileDBModel> model;

    Fixture() {
        zoo_set_debug_level(ZOO_LOG_LEVEL_WARN);
        zk = _componentsDeleter.track(new ZKFacade("test1-tonyv:2181"));
        zk->setData("/vespa", "", 0);

        model = _componentsDeleter.track(new ZKFileDBModel(zk));
    }
};

} //anonymous namespace


BOOST_FIXTURE_TEST_SUITE(ZKFileDBModelTests, Fixture)

BOOST_AUTO_TEST_CASE(retainOnlyHostsForTenant)
{
    Path path = "/vespa/filedistribution/hosts";
    std::vector<std::string> files = {"myfile"};
    BOOST_CHECK(zk->hasNode("/vespa"));
    BOOST_CHECK(zk->hasNode("/vespa/filedistribution"));
    BOOST_CHECK(zk->hasNode(path));
    model->setDeployedFilesToDownload("testhost", "myapp:so:cool", files);
    model->setDeployedFilesToDownload("testhost2", "myapp:so:cool", files);
    model->setDeployedFilesToDownload("testhost3", "myapp:so:cool", files);
    model->setDeployedFilesToDownload("testhost3", "myapp:legacyid:so:cool", files);
    model->setDeployedFilesToDownload("testhost3", "yourapp:so:cool", files);
    BOOST_CHECK(zk->getChildren(path / "testhost").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost2").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost3").size() == 3);

    model->cleanDeployedFilesToDownload({"testhost3"}, "yourapp:so:cool");
    model->removeDeploymentsThatHaveDifferentApplicationId({"testhost3"}, "yourapp:so:cool");
    BOOST_CHECK(zk->hasNode(path / "testhost"));
    BOOST_CHECK(zk->hasNode(path / "testhost2"));
    BOOST_CHECK(zk->hasNode(path / "testhost3"));
    BOOST_CHECK(zk->getChildren(path / "testhost").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost2").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost3").size() == 1);

    model->cleanDeployedFilesToDownload({"testhost"}, "myapp:not:cool");
    model->removeDeploymentsThatHaveDifferentApplicationId({"testhost"}, "myapp:not:cool");
    BOOST_CHECK(zk->hasNode(path / "testhost"));
    BOOST_CHECK(zk->hasNode(path / "testhost2"));
    BOOST_CHECK(zk->hasNode(path / "testhost3"));
    BOOST_CHECK(zk->getChildren(path / "testhost").size() == 0);
    BOOST_CHECK(zk->getChildren(path / "testhost2").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost3").size() == 1);

    model->cleanDeployedFilesToDownload({"testhost2"}, "myapp:so:cool");
    model->removeDeploymentsThatHaveDifferentApplicationId({"testhost2"}, "myapp:so:cool");

    BOOST_CHECK(!zk->hasNode(path / "testhost"));
    BOOST_CHECK(zk->hasNode(path / "testhost2"));
    BOOST_CHECK(zk->hasNode(path / "testhost3"));
    BOOST_CHECK(zk->getChildren(path / "testhost2").size() == 1);
    BOOST_CHECK(zk->getChildren(path / "testhost3").size() == 1);

    model->cleanDeployedFilesToDownload({"testhost2"}, "yourapp:so:cool");
    BOOST_CHECK(!zk->hasNode(path / "testhost"));
    BOOST_CHECK(zk->hasNode(path / "testhost2"));
    BOOST_CHECK(!zk->hasNode(path / "testhost3"));
}

BOOST_AUTO_TEST_SUITE_END()
