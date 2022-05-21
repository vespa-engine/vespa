// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**************************************************************************
 * Author: Bård Kvalheim
 *
 * The test class of the testsuite. Written by Chuck Allison.
 * http://www.cuj.com/archive/1809/feature.html
 *
 * Apart for a trick the usage of the test class is very simple:
 *
 * mytest.h:
 * ----
 * #include <iosfwd>
 * #include <vespa/fastlib/testsuite/test.h>
 *
 * class MyTest : public Test
 * {
 * public:
 *   virtual void Run() {
 *     // do the tests _test is ok if the argument are true
 *     _test(expr);
 *   }
 *
 * };
 *
 * class MyTestApp
 * {
 * public:
 *   int Main();
 * };
 *
 *
 * ----
 *
 *
 * mytest.cpp:
 * ----
 * #include "mytest.h"
 *
 * int MyTestApp::Main()
 * {
 *   MyTest mt;
 *   mt.SetStream(&std::cout);
 *   mt.Run();
 *   mt.Report();
 *
 *   return 0;
 * }
 *
 *
 * ----
 *
 * The trick is that the all the code except the main function is in
 * the .h file. The reason for this is that it is simpler to integerate
 * the single test into a suite of tests.
 *
 *************************************************************************/

#pragma once

#include <string>
#include <iostream>
#include <typeinfo>
#include <vector>
#include <algorithm>
#include <iterator>

// The following have underscores because they are macros
// (and it's impolite to usurp other users' functions!).
// For consistency, _succeed() also has an underscore.
#define _test(cond) do_test((cond), #cond, __FILE__, __LINE__)
#define _test_equal(lhs, rhs)                                   \
    do_equality_test((lhs), (rhs),  #lhs, __FILE__, __LINE__)
#define _fail(str) do_fail((str), __FILE__, __LINE__)

namespace fast::testsuite {

class Test
{
public:
    explicit Test(std::ostream* osptr = 0, const char *name = NULL);
    explicit Test(const char *name);
    virtual ~Test(){}
    virtual void Run() = 0;

    const char *get_name() const;
    static const std::string& GetSourceDirectory();
    long GetNumPassed() const;
    long GetNumFailed() const;
    const std::ostream* GetStream() const;
    void SetStream(std::ostream* osptr);

    void _Succeed();
    long Report(int padSpaces = 1) const;
    virtual void Reset();

    void PushDesc(const std::string& desc);
    void PopDesc();

protected:
    std::ostream* m_osptr;
    const char *name_;

    bool do_test(bool cond, const std::string& lbl,
                 const char* fname, long lineno);
    bool do_fail(const std::string& lbl, const char* fname, long lineno,
                 bool addEndl = true);
    template <typename t1, typename t2>
    bool do_equality_test(const t1& lhs, const t2& rhs,
                          const char* lbl, const char* fname, long lineno);
    virtual void print_progress();

private:
    long m_nPass;
    long m_nFail;
    int m_index;
    char m_pchar[4];

    std::vector<std::string> m_description;

    size_t print_desc() const;

    // Disallowed:
    Test(const Test&);
    Test& operator=(const Test&);
};

template <typename t1, typename t2>
bool Test::do_equality_test(const t1& lhs, const t2& rhs, const char* lbl,
                            const char* fname, long lineno)
{
    if (lhs == rhs) {
        _Succeed();
        print_progress();
        return true;
    }
    do_fail(std::string(lbl), fname, lineno, false);
    if (m_osptr) {
        *m_osptr << "Equality test failed: "
                 << "Expected '" << rhs
                 << "' got '" << lhs << "'"
                 << std::endl;
        if (print_desc() > 0)
            *m_osptr << std::endl << std::endl;
    }
    return false;
}

}

using fast::testsuite::Test;
