// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**************************************************************************
 * Author: Bård Kvalheim
 *
 * A test suite. Modified from the suite written by Chuck Allison.
 * http://www.cuj.com/archive/1809/feature.html
 *
 * Licensed to Yahoo, and relicensed under the terms of the Apache 2.0 license
 *
 * The usage of suite is simple:
 *
 * mysuite.h:
 * -----
 *
 * #include <iosfwd>
 * #include <vespa/fastlib/testsuite/suite.h>
 *
 * class MySuite : public Suite
 * {
 * public:
 *   MySuite() :
 *     Suite("My test suite. ", &cout)
 *   {
 *     AddTest(new MyTest1());
 *     AddTest(new MyTest2());
 *   }
 * };
 *
 *
 *
 * class MySuiteApp
 * {
 * public:
 *   int Main();
 * };
 *
 *
 * ---
 *
 * mysuite.cpp:
 * -----
 *
 * #include "mysuite.h"
 *
 *
 * int MyTestApp::Main() {
 *   MyTestSuite mts;
 *   mts.Run();
 *   mts.Report();
 *   mts.Free();
 * }
 *
 * ---
 *
 **************************************************************************/

#pragma once

#include "test.h"   // includes <string>, <iosfwd>


#include <vector>

#include <iostream>
#include <stdexcept>
#include <cassert>


namespace fast::testsuite {

class TestSuiteError;

class Suite
{
public:
    Suite(const std::string& name, std::ostream* osptr = 0);

    std::string    GetName() const;
    long           GetNumPassed() const;
    long           GetNumFailed() const;
    const std::ostream* GetStream() const;
    void           SetStream(std::ostream* osptr);

    void AddTest(Test* t);       //throw (TestSuiteError);
    void AddSuite(const Suite&); //throw(TestSuiteError);
    void Run();     // Calls Test::run() repeatedly
    long Report() const;
    void Free();    // deletes tests
    virtual ~Suite(void) { }

private:
    std::string m_name;
    std::ostream* m_osptr;
    std::vector<Test*> m_tests;
    void Reset();
    int GetLongestName() const;

    // Disallowed ops:
    Suite(const Suite&);
    Suite& operator=(const Suite&);
};

inline
Suite::Suite(const std::string& name, std::ostream* osptr)
    : m_name(name),
      m_osptr(osptr),
      m_tests()
{
}

inline
std::string Suite::GetName() const
{
    return m_name;
}

inline
const std::ostream* Suite::GetStream() const
{
    return m_osptr;
}

inline
void Suite::SetStream(std::ostream* osptr)
{
    m_osptr = osptr;
}


/*class TestSuiteError : public logic_error
  {
  public:
  TestSuiteError(const std::string& s = "")
  : logic_error(s)
  {}
  };*/

void Suite::AddTest(Test* t) //throw(TestSuiteError)
{
    // Make sure test has a stream:
    if (t == 0) {}
    //throw TestSuiteError("Null test in Suite::addTest");
    else if (m_osptr != 0 && t->GetStream() == 0)
        t->SetStream(m_osptr);

    m_tests.push_back(t);
    t->Reset();
}

void Suite::AddSuite(const Suite& s) //throw(TestSuiteError)
{
    for (size_t i = 0; i < s.m_tests.size(); ++i)
        AddTest(s.m_tests[i]);
}

void Suite::Free()
{
    // This is not a destructor because tests
    // don't have to be on the heap.
    for (size_t i = 0; i < m_tests.size(); ++i)
    {
        delete m_tests[i];
        m_tests[i] = 0;
    }
}

void Suite::Run()
{
    Reset();
    int longestName = GetLongestName();
    const char *nm;
    int x = 0;
    for (size_t i = 0; i < m_tests.size(); ++i) {
        assert(m_tests[i]);
        nm = m_tests[i]->get_name();
        if (nm) {
            *m_osptr << std::endl << nm << ": ";
            for (x = longestName - strlen(nm); x > 0; --x)
                *m_osptr << ' ';
            *m_osptr << std::flush;
        }
        m_tests[i]->Run();
    }
}


// Find the longest test name
int Suite::GetLongestName() const
{
    int longestName = 0, len = 0;
    const char *nm;
    for (size_t i = 0; i < m_tests.size(); ++i) {
        assert(m_tests[i]);
        nm = m_tests[i]->get_name();
        if ( nm != NULL && (len = strlen(nm)) > longestName )
            longestName = len;
    }
    return longestName;
}

long Suite::Report() const
{
    if (m_osptr) {
        int longestName = GetLongestName();
        int lineLength = longestName + 8 + 16 + 10;
        long totFail = 0;
        int x = 0;
        *m_osptr << std::endl << std::endl
                 << "Suite \"" << m_name << "\"" << std::endl;
        for (x = 0; x < lineLength; ++x)
            *m_osptr << '=';
        *m_osptr << "=";

        // Write the individual reports
        for (size_t i = 0; i < m_tests.size(); ++i) {
            assert(m_tests[i]);
            const char *nm = m_tests[i]->get_name();
            totFail += m_tests[i]->Report(longestName -
                                          (nm ? strlen(nm) : longestName));
        }

        for (x = 0; x < lineLength; ++x)
            *m_osptr << '=';
        *m_osptr << "=\n";
        return totFail;
    }
    else
        return GetNumFailed();
}

long Suite::GetNumPassed() const
{
    long totPass = 0;
    for (size_t i = 0; i < m_tests.size(); ++i)
    {
        assert(m_tests[i]);
        totPass += m_tests[i]->GetNumPassed();
    }
    return totPass;
}

long Suite::GetNumFailed() const
{
    long totFail = 0;
    for (size_t i = 0; i < m_tests.size(); ++i)
    {
        assert(m_tests[i]);
        totFail += m_tests[i]->GetNumFailed();
    }
    return totFail;
}

void Suite::Reset()
{
    for (size_t i = 0; i < m_tests.size(); ++i)
    {
        assert(m_tests[i]);
        m_tests[i]->Reset();
    }
}

}

using fast::testsuite::Suite;
