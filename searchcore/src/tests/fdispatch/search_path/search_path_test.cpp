// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("search_path_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/fdispatch/search/search_path.h>
#include <vespa/searchcore/fdispatch/search/fnet_search.h>
#include <iostream>

using namespace fdispatch;

template <typename T>
vespalib::string
toString(const T &val)
{
    std::ostringstream oss;
    oss << "[";
    bool first = true;
    for (auto v : val) {
        if (!first) oss << ",";
        oss << v;
        first = false;
    }
    oss << "]";
    return oss.str();
}

void
assertParts(const std::vector<size_t> &exp, const SearchPath::NodeList &act)
{
    std::string expStr = toString(exp);
    std::string actStr = toString(act);
    std::cout << "assertParts(" << expStr << "," << actStr << ")" << std::endl;
    EXPECT_EQUAL(expStr, actStr);
}

void
assertElement(const std::vector<size_t> &parts, size_t row, const SearchPath::Element &elem)
{
    assertParts(parts, elem.nodes());
    EXPECT_TRUE(elem.hasRow());
    EXPECT_EQUAL(row, elem.row());
}

void
assertElement(const std::vector<size_t> &parts, const SearchPath::Element &elem)
{
    assertParts(parts, elem.nodes());
    EXPECT_FALSE(elem.hasRow());
}

void
assertSinglePath(const std::vector<size_t> &parts, const vespalib::string &spec, size_t numNodes=0)
{
    SearchPath p(spec, numNodes);
    EXPECT_EQUAL(1u, p.elements().size());
    assertElement(parts, p.elements().front());
}

void
assertSinglePath(const std::vector<size_t> &parts, size_t row, const vespalib::string &spec, size_t numNodes=0)
{
    SearchPath p(spec, numNodes);
    EXPECT_EQUAL(1u, p.elements().size());
    assertElement(parts, row, p.elements().front());
}

TEST("requireThatSinglePartCanBeSpecified")
{
    assertSinglePath({0}, "0/");
}

TEST("requireThatMultiplePartsCanBeSpecified")
{
    assertSinglePath({1,3,5}, "1,3,5/");
}

TEST("requireThatRangePartsCanBeSpecified")
{
    assertSinglePath({1,2,3}, "[1,4>/", 6);
}

TEST("requireThatAllPartsCanBeSpecified")
{
    assertSinglePath({0,1,2,3}, "*/", 4);
}

TEST("requireThatRowCanBeSpecified")
{
    assertSinglePath({1}, 2, "1/2");
}

TEST("requireThatMultipleSimpleElementsCanBeSpecified")
{
    SearchPath p("0/1;2/3", 3);
    EXPECT_EQUAL(2u, p.elements().size());
    assertElement({0}, 1, p.elements()[0]);
    assertElement({2}, 3, p.elements()[1]);
}

TEST("requireThatMultipleComplexElementsCanBeSpecified")
{
    SearchPath p("0,2,4/1;1,3,5/3", 6);
    EXPECT_EQUAL(2u, p.elements().size());
    assertElement({0,2,4}, 1, p.elements()[0]);
    assertElement({1,3,5}, 3, p.elements()[1]);
}

TEST("requireThatMultipleElementsWithoutRowsCanBeSpecified")
{
    SearchPath p("0/;1/", 2);
    EXPECT_EQUAL(2u, p.elements().size());
    assertElement({0}, p.elements()[0]);
    assertElement({1}, p.elements()[1]);
}

TEST("require that sizeof FastS_FNET_SearchNode is reasonable")
{
    EXPECT_EQUAL(240u, sizeof(FastS_FNET_SearchNode));
    EXPECT_EQUAL(40u, sizeof(search::common::SortDataIterator));
}

TEST_MAIN() { TEST_RUN_ALL(); }
