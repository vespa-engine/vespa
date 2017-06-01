// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <list>
#include <string>
#include <algorithm>
#include <vespa/storage/storageutil/functor.h>

class Functor_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE(Functor_Test);
  CPPUNIT_TEST(testReplace);
  CPPUNIT_TEST(testDeletePointer);
  CPPUNIT_TEST_SUITE_END();

public:

protected:
  void testReplace();
  void testDeletePointer();
};

using namespace storage;
using namespace std;

CPPUNIT_TEST_SUITE_REGISTRATION(Functor_Test);

void Functor_Test::testReplace()
{
    string source("this.is.a.string.with.many.dots.");
    for_each(source.begin(), source.end(), Functor::Replace<char>('.', '_'));
    CPPUNIT_ASSERT_EQUAL(string("this_is_a_string_with_many_dots_"), source);
}

namespace {

    static int instanceCounter = 0;

    class TestClass {
    public:
        TestClass() { instanceCounter++; }
        ~TestClass() { instanceCounter--; }
    };
}

void Functor_Test::testDeletePointer()
{
    list<TestClass*> mylist;
    mylist.push_back(new TestClass());
    mylist.push_back(new TestClass());
    mylist.push_back(new TestClass());
    CPPUNIT_ASSERT_EQUAL(3, instanceCounter);
    for_each(mylist.begin(), mylist.end(), Functor::DeletePointer());
    CPPUNIT_ASSERT_EQUAL(0, instanceCounter);
}
