// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "test_kit.h"
#include <vespa/fastos/app.h>

#undef TEST_MASTER
#define TEST_MASTER vespalib::TestApp::master

#define TEST_INIT(name) do { ReportInit(name); TEST_MASTER.init(name); } while(false)
#define TEST_DONE() do { ReportConclusion(); return TEST_MASTER.fini() ? 0 : 1; } while(false)

#define TEST_APPHOOK(app) \
  int main(int argc, char **argv) \
  { \
    app myapp; \
    return myapp.Entry(argc, argv); \
  }
#define TEST_SETUP(test) \
  class test : public vespalib::TestApp \
  { \
    public: int Main() override; \
  }; \
  TEST_APPHOOK(test)
#define TEST_SETUP_WITHPROCESSPROXY(test) \
  class test : public vespalib::TestApp \
  { \
    public: \
        int Main(); \
        virtual bool useProcessStarter() const { return true; } \
  }; \
  TEST_APPHOOK(test)

namespace vespalib {

/**
 * @brief TestApp is used to create executable unit tests
 *
 * TestApp is a subclass of FastOS_Application that is tailored to
 * create small executable programs containing unit tests. The idea is
 * that you create a class that subclasses TestApp and use a set of
 * macros to test your code. It is similar to the concept used in
 * cppunit except that your tests become programs rather than library
 * code.
 *
 * Below follows an explanation of all the available macros. Note that
 * while you can also invoke the methods in this class directly, most
 * tests will only need to use macros. Also, some features like
 * grabbing the text making up a boolean expression, the file name and
 * the line number of a statement is only possible with macros.
 *
 * <table>
 * <tr><td><b>TEST_INIT(name)</b></td><td>
 *   Invokes the ReportInit method. <i>name</i> is the name of this
 *   test.
 * </td></tr>
 * <tr><td><b>TEST_GROUP(val)</b></td><td>
 *   Invokes the SetGroupModulo method. <i>val</i> is an integer
 *   defining the number of successful tests that may be logged at
 *   once. If <i>val</i> is 0, all consecutive successful test cases
 *   will be reported at once. If <i>val</i> is 1, all tests will be
 *   reported by separate log messages (this is the default).
 * </td></tr>
 * <tr><td><b>TEST_DEBUG(lhsFile, rhsFile)</b></td><td>
 *   Invokes the OpenDebugFiles method. <i>lhs</i> and <i>rhs</i> are
 *   file names for files where we want the left hand side and right
 *   hand side values to be stored. Diffing these files will simplify
 *   debugging of failed tests.
 * </td></tr>
 * <tr><td><b>TEST_PUSH_STATE(msg)</b></td><td>
 *   Invokes the PushState function. <i>msg</i> is pushed on the
 *   internal state stack together with the current file and line
 *   number. The state stack is dumped when a test fails, unless the
 *   DumpState function is overridden.
 * </td></tr>
 * <tr><td><b>TEST_POP_STATE()</b></td><td>
 *   Invokes the PopState function. The topmost item on the state stack
 *   will be popped. If the state stack is empty, nothing will happen.
 * </td></tr>
 * <tr><td><b>TEST_DO(statement)</b></td><td>
 *   Pushes <i>statement</i> on the state stack, performs
 *   <i>statement</i> and finally pops the state stack. The intended
 *   use is to wrap function calls within TEST_DO in order to produce
 *   something that looks like a stack trace when the state stack is
 *   dumped.
 * </td></tr>
 * <tr><td><b>EXPECT_TRUE(rc)</b></td><td>
 *   Test that <i>rc</i> evaluates to true. <i>rc</i> must be an
 *   expression that can be evaluated as a boolean. This automatically
 *   creates a test case. The macro invokes the ReportTestResult
 *   method.
 * </td></tr>
 * <tr><td><b>ASSERT_TRUE(rc)</b></td><td>
 *   Does the same as EXPECT_TRUE except that the application is
 *   aborted if the test fails. This macro may be used to test that
 *   setup vital to the rest of the tests does not fail.
 * </td></tr>
 * <tr><td><b>EXPECT_EQUAL(a, b)</b></td><td>
 *   Test that <i>a</i> == <i>b</i>.
 * </td></tr>
 * <tr><td><b>EXPECT_NOT_EQUAL(a, b)</b></td><td>
 *   Test that <i>a</i> != <i>b</i>.
 * </td></tr>
 * <tr><td><b>EXPECT_LESS(a, b)</b></td><td>
 *   Test that <i>a</i> &lt; <i>b</i>.
 * </td></tr>
 * <tr><td><b>EXPECT_LESS_EQUAL(a, b)</b></td><td>
 *   Test that <i>a</i> &lt;= <i>b</i>.
 * </td></tr>
 * <tr><td><b>EXPECT_GREATER(a, b)</b></td><td>
 *   Test that <i>a</i> &gt; <i>b</i>.
 * </td></tr>
 * <tr><td><b>EXPECT_GREATER_EQUAL(a, b)</b></td><td>
 *   Test that <i>a</i> &gt;= <i>b</i>.
 * </td></tr>
 * <tr><td><b>TEST_FLUSH()</b></td><td>
 *   Invokes the FlushReport method. This is used to force the logging
 *   of successful test cases that are waiting to be logged due to
 *   grouping (see TEST_GROUP).
 * </td></tr>
 * <tr><td><b>TEST_DONE()</b></td><td>
 *   Invokes the ReportConclusion method and returns 0 if all test
 *   cases passed, 1 otherwise. This should be the last thing in your
 *   Main method.
 * </td></tr>
 * <tr><td><b>TEST_APPHOOK(app)</b></td><td>
 *   Creates the code needed to run the class <i>app</i> that must be
 *   a subclass of FastOS_Application. This will create a canonical
 *   main method launching your fastos application.
 * </td></tr>
 * <tr><td><b>TEST_SETUP(test)</b></td><td>
 *   Does the same as TEST_APPHOOK, but also creates a simple class
 *   named <i>test</i> that is a subclass of TestApp and contains only
 *   the Main method. If you want to add some helper methods to your
 *   test, use TEST_APPHOOK, otherwise use this one.
 * </td></tr>
 * <tr><td><b>TEST_THREADS()</b></td><td>
 *   This macro is used in tests that require thread support. If no
 *   thread support is available (fastos was compiled without
 *   threads), this macro will invoke the ReportNoThreads and call the
 *   TEST_DONE macro to exit the test. If thread support is available
 *   this macro does nothing.
 * </td></tr>
 * </table>
 *
 * Simple test example:
 * <pre>
 * \#include <vespa/vespalib/testkit/testapp.h>
 *
 * TEST_SETUP(Test)
 *
 * int
 * Test::Main()
 * {
 *     TEST_INIT("true_test");
 *     EXPECT_TRUE(true);
 *     TEST_DONE();
 * }
 * </pre>
 **/
class TestApp : public FastOS_Application
{
private:
    std::string _name;

public:
    static TestMaster &master;

    TestApp();
    virtual ~TestApp();

    /**
     * @brief Obtain the name of this test
     *
     * @return test name
     **/
    const char *GetName() { return _name.c_str(); }

    /**
     * @brief Report test initialization
     *
     * @param name the name of this test
     **/
    virtual void ReportInit(const char *name);

    /**
     * @brief Report test summary
     *
     * @return true if all test cases passed
     **/
    virtual bool ReportConclusion();
};

} // namespace vespalib

