// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author Knut Omang
 */
#pragma once

#include "testenv.h"
#include "test.h"
#include <vespa/juniper/mcand.h>
#include <map>

/**
 * The MatchCandidateTest class holds
 * the unit tests for the MatchCandidate class.
 *
 * @sa      MatchCandidate
 * @author  Knut Omang
 */
class MatchCandidateTest : public Test {

    /*************************************************************************
     *                      Test methods
     *
     * This section contains boolean methods for testing each public method
     * in the class ing tested
     *************************************************************************/

    /**
     * Test of the SetDocid method.
     */
    void testSetDocid();


    /**
     * Test of the log method.
     */
    void testLog();


    /**
     * Test of the dump method.
     */
    void testDump();


    /**
     * Test of the (order method.
     */
    void testorder();


    /**
     * Test of the matches_limit method.
     */
    void testMatches_limit();


    /**
     * Test of the accept method.
     */
    void testAccept();


    /**
     * Test of the rank method.
     */
    void testRank();


    /**
     * Test of the make_keylist method.
     */
    void testMake_keylist();


    /**
     * Test of the add_to_keylist method.
     */
    void testAdd_to_keylist();


    /**
     * Test of the length method.
     */
    void testLength();

    /**
     * Test that the max number of match candidates can be controlled.
     */
    void requireThatMaxNumberOfMatchCandidatesCanBeControlled();

    /**
     * Test of the order method.
     */
    void testOrder();


    /**
     * Test of the size method.
     */
    void testSize();


    /**
     * Test of the endpos method.
     */
    void testEndpos();


    /**
     * Test of the ctxt_startpos method.
     */
    void testCtxt_startpos();


    /**
     * Test of the starttoken method.
     */
    void testStarttoken();


    /**
     * Test of the word_distance method.
     */
    void testWord_distance();


    /**
     * Test of the distance method.
     */
    void testDistance();


    /**
     * Test of the elem_store_sz method.
     */
    void testElem_store_sz();


    /**
     * Test of the elems method.
     */
    void testElems();


    /**
     * Test of the distance method.
     */
    void testDistance1();


    /**
     * Test of the set_valid method.
     */
    void testSet_valid();


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

    typedef void(MatchCandidateTest::* tst_method_ptr) ();
    typedef std::map<std::string, tst_method_ptr> MethodContainer;
    MethodContainer test_methods_;
    void init();
protected:

    /**
     * Since we are running within Emacs, the default behavior of
     * print_progress which includes backspace does not work.
     * We'll use a single '.' instead.
     */
    void print_progress() override { *m_osptr << '.' << std::flush; }

public:

    MatchCandidateTest();
    ~MatchCandidateTest() {}

    /*************************************************************************
     *                         main entry points
     *************************************************************************/
    void Run(MethodContainer::iterator &itr);
    void Run() override;
    void Run(const char *method);
    void Run(int argc, char* argv[]);
};


// Local Variables:
// mode:c++
// End:
