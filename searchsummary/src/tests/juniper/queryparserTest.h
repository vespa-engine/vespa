// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author Knut Omang
 */
#pragma once

#include "test.h"
#include "testenv.h"
#include <map>
#include "queryparser.h"

/**
 * The QueryParserTest class holds
 * the unit tests for the QueryParser class.
 *
 * @sa      QueryParser
 * @author  Knut Omang
 */
class QueryParserTest : public Test {

    /*************************************************************************
     *                      Test methods
     *
     * This section contains boolean methods for testing each public method
     * in the class ing tested
     *************************************************************************/

    /**
     * Test of the UsefulIndex method.
     */
    void testUsefulIndex();

    /**
     * Test of the Creator method.
     */
    void testCreator();

    /**
     * Test of the Weight method.
     */
    void testWeight();

    /**
     * Test of the Traverse method.
     */
    void testTraverse();

    /*************************************************************************
     *                      Test administration methods
     *************************************************************************/

    /**
     * Set up common stuff for all test methods.
     * This method is called immediately before each test method is called
     */
    bool setUp();

    /**
     * Tear down common stuff for all test methods.
     * This method is called immediately after each test method is called
     */
    void tearDown();

    typedef void (QueryParserTest::*tst_method_ptr)();
    using MethodContainer = std::map<std::string, tst_method_ptr>;
    MethodContainer test_methods_;
    void            init();

protected:
    /**
     * Since we are running within Emacs, the default behavior of
     * print_progress which includes backspace does not work.
     * We'll use a single '.' instead.
     */
    void print_progress() override { *m_osptr << '.' << std::flush; }

public:
    QueryParserTest() : Test("QueryParser"), test_methods_() { init(); }
    ~QueryParserTest() {}

    /*************************************************************************
     *                         main entry points
     *************************************************************************/
    void Run(MethodContainer::iterator& itr);
    void Run() override;
    void Run(const char* method);
    void Run(int argc, char* argv[]);
};

// Local Variables:
// mode:c++
// End:
