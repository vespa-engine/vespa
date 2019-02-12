// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdslib/state/nodetype.h>
#include <vdslib/state/group.h>
#include <vespa/vdslib/state/idealgroup.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cppunit/extensions/HelperMacros.h>
#include <boost/lexical_cast.hpp>
#include <algorithm>

using namespace std;
using namespace vdslib;

class GroupTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(GroupTest);
    CPPUNIT_TEST(testTree);
    CPPUNIT_TEST(testSetNodes);
    CPPUNIT_TEST(testOperators);
    CPPUNIT_TEST(testStarConversion);
    CPPUNIT_TEST(testGroupIndexOrder);
    CPPUNIT_TEST(testNodeIndexOrder);
    CPPUNIT_TEST(testIdealGroupRedundancyOrder);
    CPPUNIT_TEST(testIdealGroupScoreOrder);
    CPPUNIT_TEST(testIdealGroupPickingConsistency);
    CPPUNIT_TEST_SUITE_END();

public:
protected:
    void testTree();
    void testSetNodes();
    void testOperators();
    void testStarConversion();
    void testGroupIndexOrder();
    void testNodeIndexOrder();
    void testIdealGroupRedundancyOrder();
    void testIdealGroupScoreOrder();
    void testIdealGroupPickingConsistency();
};

CPPUNIT_TEST_SUITE_REGISTRATION( GroupTest );

#define MAKEGROUP(group, name, index, distribution) \
    Group group; \
    group.setName(name); \
    group.setIndex(index); \
    group.setDistribution(distribution);

#define MAKEGROUPPTR(group, name, index, distribution) \
    Group* group = new Group(); \
    group->setName(name); \
    group->setIndex(index); \
    group->setDistribution(distribution);

#define VERIFY(group, expected) { \
    std::ostringstream ost; \
    group.serialize(ost, true); \
    CPPUNIT_ASSERT_EQUAL(std::string(expected), ost.str()); \
}

void
GroupTest::testTree()
{
    uint16_t numGroups=10;

    std::vector<Group*> groups;
    for (uint16_t i=0; i< numGroups; ++i){
        std::string name = vespalib::make_string("groupname%d", i);
        MAKEGROUPPTR(g, name, i, "");
        if (i >= numGroups/2) {
            std::string storageIndexes =
                vespalib::make_string("[%d-%d]", i, i);
            g->setNodes(NodeType::STORAGE, storageIndexes);
            std::string distributorIndexes =
                vespalib::make_string("[%d-%d]", i-1, i-1);
            g->setNodes(NodeType::DISTRIBUTOR, distributorIndexes);
        }
        groups.push_back(g);
    }

    for (uint16_t i=numGroups-1; i>0; --i){
        uint16_t child = i;
        uint16_t parent = i/2;
        groups[child]->setParent(groups[parent]);
        groups[parent]->addSubGroup(groups[child]);
    }

    //groups[0]->print(std::cerr, true, ""); std::cerr << "\n";

    CPPUNIT_ASSERT_EQUAL((uint16_t)(numGroups-1),
                         groups[0]->getMaxIndex(NodeType::STORAGE));
    CPPUNIT_ASSERT_EQUAL((uint16_t)(numGroups-2),
                         groups[0]->getMaxIndex(NodeType::DISTRIBUTOR));

    for (uint16_t i=0; i<numGroups+1; ++i){
        if (i<numGroups/2 || i>=numGroups) {
            CPPUNIT_ASSERT(!groups[0]->containsNode(NodeType::STORAGE, i));
        } else {
            CPPUNIT_ASSERT(groups[0]->containsNode(NodeType::STORAGE, i));
        }
    }

    delete groups[0];
}

void
GroupTest::testSetNodes()
{
    MAKEGROUP(group, "group", 0, "");
    std::string noNodes(" ( name:group index:0 )");
    std::string threeNodes(" ( name:group index:0 distributor:[0-2] )");
    {
        try {
            group.setNodes(NodeType::DISTRIBUTOR, "a");
            CPPUNIT_ASSERT(false);
        } catch (boost::bad_lexical_cast& e) {
            VERIFY(group, noNodes);
        }
        try {
            group.setNodes(NodeType::DISTRIBUTOR, "[b]");
            CPPUNIT_ASSERT(false);
        } catch (boost::bad_lexical_cast& e) {
            VERIFY(group, noNodes);
        }
        try {
            group.setNodes(NodeType::DISTRIBUTOR, "[0-c]");
            CPPUNIT_ASSERT(false);
        } catch (boost::bad_lexical_cast& e) {
            VERIFY(group, noNodes);
        }
        try {
            group.setNodes(NodeType::DISTRIBUTOR, "[-1-6]");
            CPPUNIT_ASSERT(false);
        } catch (boost::bad_lexical_cast& e) {
            VERIFY(group, noNodes);
        }
        group.setNodes(NodeType::DISTRIBUTOR, "[5-3]");
        //group.print(std::cerr, true, ""); std::cerr << "\n";
        VERIFY(group, noNodes);

        group.setNodes(NodeType::DISTRIBUTOR, "3");
        VERIFY(group, threeNodes);

        group.setNodes(NodeType::DISTRIBUTOR, "[0-2]");
        VERIFY(group, threeNodes);

        group.setNodes(NodeType::DISTRIBUTOR, "[0,1,2]");
        VERIFY(group, threeNodes);

        group.setNodes(NodeType::DISTRIBUTOR, "[,0,1,2,]");
        VERIFY(group, threeNodes);

        group.setNodes(NodeType::DISTRIBUTOR, "[0,0-2]");
        VERIFY(group, threeNodes);
    }
}

void
GroupTest::testOperators()
{
    {
        MAKEGROUP(g0, "group", 0, "1|*|*");
        MAKEGROUP(g1, "group", 1, "1|*|*");
        CPPUNIT_ASSERT(g0 != g1);
    }
    {
        MAKEGROUP(g0, "group", 0, "1|*");
        MAKEGROUP(g1, "group", 0, "1|*|*");
        CPPUNIT_ASSERT(g0 != g1);
    }
    {
        MAKEGROUPPTR(g0, "group", 0, "1|*");
        MAKEGROUPPTR(g1, "group", 1, "1|*|*");
        g0->addSubGroup(g1);
        MAKEGROUPPTR(g2, "group", 0, "1|*");
        CPPUNIT_ASSERT(g0 != g2);
        delete g0; delete g2;
    }
}


void
GroupTest::testStarConversion()
{
    {
        MAKEGROUP(g, "group", 0, "*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 3, distribution[0]);
    }
    {
        MAKEGROUP(g, "group", 0, "1|*|*");
        std::vector<double> distribution = g.getDistribution(5);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[2]);
    }
    {
        MAKEGROUP(g, "group", 0, "1|*|*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[2]);
    }
    {
        MAKEGROUP(g, "group", 0, "1|*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 2, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[1]);
    }
    {
        MAKEGROUP(g, "group", 0, "4|*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 3, distribution[0]);
    }
    {
        MAKEGROUP(g, "group", 0, "2|*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 2, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[1]);
    }
    {
        MAKEGROUP(g, "group", 0, "2|*");
        std::vector<double> distribution = g.getDistribution(0);
        CPPUNIT_ASSERT_EQUAL((size_t) 0, distribution.size());
    }
    {
        MAKEGROUP(g, "group", 0, "*|*");
        std::vector<double> distribution = g.getDistribution(3);
        CPPUNIT_ASSERT_EQUAL((size_t) 2, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[1]);
    }
    {
        MAKEGROUP(g, "group", 0, "*|*|*");
        std::vector<double> distribution = g.getDistribution(4);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[2]);
    }
    {
        MAKEGROUP(g, "group", 0, "*|*|*");
        std::vector<double> distribution = g.getDistribution(5);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[2]);
    }
    {
        MAKEGROUP(g, "group", 0, "*|*|*");
        std::vector<double> distribution = g.getDistribution(12); // Shall be evenly divided
        CPPUNIT_ASSERT_EQUAL((size_t) 3, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 4, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 4, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 4, distribution[2]);
    }
    {
        MAKEGROUP(g, "group", 0, "*|*|*|*");
        std::vector<double> distribution = g.getDistribution(5);
        CPPUNIT_ASSERT_EQUAL((size_t) 4, distribution.size());
        CPPUNIT_ASSERT_EQUAL((double) 2, distribution[0]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[1]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[2]);
        CPPUNIT_ASSERT_EQUAL((double) 1, distribution[3]);
    }



}

void
GroupTest::testGroupIndexOrder()
{
    uint16_t numGroups=10;

    std::vector<Group*> groups;
    for (uint16_t i=0; i< numGroups; ++i){
        MAKEGROUPPTR(g, "group", i, "5.3|*");
        groups.push_back(g);
    }

    for (uint16_t i=1; i< 10; ++i){
        groups[0]->addSubGroup(groups[numGroups-i]);
    }

    uint16_t last=0;
    for (uint16_t i=0; i< groups[0]->getNumSubGroups(); ++i){
        CPPUNIT_ASSERT(last < groups[0]->getSubGroup(i)->getIndex());
        last = groups[0]->getSubGroup(i)->getIndex();
    }
    delete groups[0];
}


void
GroupTest::testNodeIndexOrder()
{
    uint16_t numNodes=10;
    MAKEGROUP(g, "group", 0, "5.3|*");

    for (uint16_t i=0; i< numNodes; ++i){
        g.addNode(NodeType::DISTRIBUTOR, numNodes-i);
    }

    uint16_t last=0;
    const std::vector<uint16_t>& nodes = g.getNodes(NodeType::DISTRIBUTOR);
    CPPUNIT_ASSERT_EQUAL((size_t)numNodes, nodes.size());
    for (uint16_t i=0; i< nodes.size(); ++i){
        CPPUNIT_ASSERT(last < nodes[i]);
        last = nodes[i];
    }
}


void
GroupTest::testIdealGroupRedundancyOrder()
{
    MAKEGROUPPTR(g0, "group0", 0, "1|2|*");
    MAKEGROUPPTR(g1, "group1", 1, "*");
    MAKEGROUPPTR(g2, "group2", 2, "*");
    MAKEGROUPPTR(g3, "group3", 3, "*");

    g0->addSubGroup(g1);
    g0->addSubGroup(g2);
    g0->addSubGroup(g3);

    std::vector<IdealGroup> idealGroups;
    g0->getIdealGroups(6, 100, idealGroups);

    for (uint16_t i=0; i<idealGroups.size(); ++i){
        CPPUNIT_ASSERT_EQUAL((double) (3-i),  idealGroups[i].getRedundancy().getValue());
    }
    delete g0;
}

void
GroupTest::testIdealGroupScoreOrder()
{
    MAKEGROUPPTR(g0, "group0", 0, "1|2|*");
    MAKEGROUPPTR(g1, "group1", 1, "*");
    MAKEGROUPPTR(g2, "group2", 2, "*");
    MAKEGROUPPTR(g3, "group3", 3, "*");

    g0->addSubGroup(g1);
    g0->addSubGroup(g2);
    g0->addSubGroup(g3);

    std::vector<IdealGroup> idealGroups;
    g0->getIdealGroups(6, 100, idealGroups);

    std::sort(idealGroups.rbegin(), idealGroups.rend(), vdslib::IdealGroup::sortScore);
    double last=1.0;
    for (uint16_t g=0; g< idealGroups.size(); ++g){
        CPPUNIT_ASSERT(last >= idealGroups[g].getScore());
        last = idealGroups[g].getScore();
        CPPUNIT_ASSERT_EQUAL((double) (g+1),  idealGroups[g].getRedundancy().getValue());
    }
    delete g0;
}


void
GroupTest::testIdealGroupPickingConsistency()
{
    MAKEGROUPPTR(g0, "group0", 0, "1|2|*");
    MAKEGROUPPTR(g1, "group1", 1, "*");
    MAKEGROUPPTR(g2, "group2", 2, "*");
    MAKEGROUPPTR(g3, "group3", 3, "*");

    g0->addSubGroup(g1);
    g0->addSubGroup(g2);
    g0->addSubGroup(g3);

    std::vector<IdealGroup> idealGroups1;
    g0->getIdealGroups(6, 100, idealGroups1);


    std::vector<IdealGroup> idealGroups2;
    g0->getIdealGroups(6, 100, idealGroups2);

    CPPUNIT_ASSERT_EQUAL((size_t)3, idealGroups1.size());
    CPPUNIT_ASSERT_EQUAL((size_t)3, idealGroups2.size());

    for (uint16_t g=0; g< idealGroups1.size(); ++g){
        CPPUNIT_ASSERT_EQUAL(idealGroups1[g].getGroup(),
                             idealGroups2[g].getGroup());
    }
    delete g0;
}
