// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**************************************************************************
 * @author B�rd Kvalheim
 * @version $Id;
 * @file
 *
 * The test class of the testsuite. Written by Chuck Allison.
 * http://www.cuj.com/archive/1809/feature.html
 *
 * @date Creation date: 2000-12-15
 * Copyright (c)    : 1997-2000 Fast Search & Transfer ASA
 *                    ALL RIGHTS RESERVED
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
 * class MyTestApp : public FastOS_Application
 * {
 * public:
 *   virtual int Main();
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
 * FASTOS_MAIN(MyTestApp)
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
#define _test_equal(lhs, rhs) \
  do_equality_test((lhs), (rhs),  #lhs, __FILE__, __LINE__)
#define _fail(str) do_fail((str), __FILE__, __LINE__)

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

inline
Test::Test(std::ostream* osptr, const char*name) :
  m_osptr(osptr),
  name_(name),
  m_nPass(0),
  m_nFail(0),
  m_index(0),
  m_description()
{
  m_pchar[0]= '|';
  m_pchar[1]= '-';
}

inline
Test::Test(const char*name) :
  Test(nullptr, name)
{
}

inline
const char *Test::get_name() const {
  return (name_ == NULL) ? "Test " : name_;
}

inline
const std::string& Test::GetSourceDirectory()
{
  static const std::string srcDir = [] () {
      std::string dir(".");
      const char* env = getenv("SOURCE_DIRECTORY");
      if (env) {
        dir = env;
      }
      if (*dir.rbegin() != '/') {
        dir += "/";
      }
      return dir;
  } ();
  return srcDir;
}

inline
long Test::GetNumPassed() const
{
  return m_nPass;
}

inline
long Test::GetNumFailed() const
{
  return m_nFail;
}

inline
const std::ostream* Test::GetStream() const
{
  return m_osptr;
}

inline
void Test::SetStream(std::ostream* osptr)
{
  m_osptr = osptr;
}

inline
void Test::_Succeed()
{
  ++m_nPass;
}

inline
void Test::Reset()
{
  m_nPass = m_nFail = 0;
}


inline
void Test::PushDesc(const std::string& desc)
{
  m_description.push_back(desc);
}

inline
void Test::PopDesc()
{
  m_description.pop_back();
}

inline
size_t Test::print_desc() const
{
  std::copy(m_description.begin(), m_description.end(),
            std::ostream_iterator<std::string>(*m_osptr));
  return m_description.size();
}

inline
void Test::print_progress() {
  ++m_index;
  m_index = m_index % 2;
  *m_osptr << '\b' <<'\b' <<'\b';
  *m_osptr <<' ' << m_pchar[m_index] << ' ' << std::flush;
}

inline
bool Test::do_fail(const std::string& lbl, const char* fname, long lineno,
                   bool addEndl)
{
  ++m_nFail;
  if (m_osptr) {
    *m_osptr << std::endl
             << fname << ':' << lineno << ": "
             << get_name() << " failure: (" << lbl << ")"
             << std::endl;
    if (addEndl && print_desc() > 0)
      *m_osptr << std::endl << std::endl;
  }
  return false;
}

inline
bool Test::do_test(bool cond, const std::string& lbl,
                   const char* fname, long lineno)
{
  if (!cond) {
    return do_fail(lbl, fname, lineno);
  }
  else {
    _Succeed();
    print_progress();
    return true;
  }
}

template <typename t1, typename t2>
inline
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

inline
long Test::Report(int padSpaces) const
{
  if (m_osptr) {
    *m_osptr << std::endl << get_name();

    // Pad the name with the given number of spaces
    for (int i= 0; i < padSpaces; ++i) *m_osptr << ' ';

    *m_osptr << "\tPassed: " << m_nPass
             << "\tFailed: " << m_nFail
             << std::endl;
  }
  return m_nFail;
}

