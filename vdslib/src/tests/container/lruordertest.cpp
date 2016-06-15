// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/container/lruorder.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace lib {

struct LruOrderTest : public CppUnit::TestFixture {

    void testSimple();

    CPPUNIT_TEST_SUITE(LruOrderTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(LruOrderTest);

namespace {

    struct LruMap {
        struct Entry {
            std::string _value;
            LruOrder<int, LruMap>::EntryRef _order;
        };
        std::map<int, Entry> _map;
        LruOrder<int, LruMap> _order;

        LruMap(uint32_t size) : _order(size, *this) {}

        void removedFromOrder(int i) {
            _map.erase(i);
        }

        std::string& operator[](int i) {
            std::map<int, Entry>::iterator it(_map.find(i));
            if (it == _map.end()) {
                Entry e;
                e._order = _order.add(i);
                _map[i] = e;
                return _map[i]._value;
            } else {
                _order.moveToStart(it->second._order);
                return it->second._value;
            }
        }

        void remove(int i) {
            std::map<int, Entry>::iterator it(_map.find(i));
            if (it != _map.end()) {
                _order.remove(it->second._order);
            }
        }

        void clear() {
            _map.clear();
            _order.clear();
        }
    };
    
}

void
LruOrderTest::testSimple()
{
    LruMap map(3);
    CPPUNIT_ASSERT_EQUAL(std::string("[]"), map._order.toString());

    map[3] = "1";
    CPPUNIT_ASSERT_EQUAL(std::string("[3]"), map._order.toString());

    map[7] = "2";
    CPPUNIT_ASSERT_EQUAL(std::string("[7, 3]"), map._order.toString());

    map[9] = "3";
    CPPUNIT_ASSERT_EQUAL(std::string("[9, 7, 3]"), map._order.toString());

    map[13] = "4";
    CPPUNIT_ASSERT_EQUAL(std::string("[13, 9, 7]"), map._order.toString());

    map[9];
    CPPUNIT_ASSERT_EQUAL(std::string("[9, 13, 7]"), map._order.toString());

    map.remove(13);
    CPPUNIT_ASSERT_EQUAL(std::string("[9, 7]"), map._order.toString());

    map.clear();
    CPPUNIT_ASSERT_EQUAL(std::string("[]"), map._order.toString());

    map[4] = "3";
    CPPUNIT_ASSERT_EQUAL(std::string("[4]"), map._order.toString());

    map[2] = "4";
    CPPUNIT_ASSERT_EQUAL(std::string("[2, 4]"), map._order.toString());

    map[4] = "4";
    CPPUNIT_ASSERT_EQUAL(std::string("[4, 2]"), map._order.toString());

    map[7] = "4";
    CPPUNIT_ASSERT_EQUAL(std::string("[7, 4, 2]"), map._order.toString());

    map[8] = "4";
    CPPUNIT_ASSERT_EQUAL(std::string("[8, 7, 4]"), map._order.toString());
}


} // lib
} // storage
