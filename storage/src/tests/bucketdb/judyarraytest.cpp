// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/judyarray.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <boost/assign.hpp>
#include <boost/random.hpp>
#include <cppunit/extensions/HelperMacros.h>
#include <map>
#include <vector>

namespace storage {

struct JudyArrayTest : public CppUnit::TestFixture {
    void testIterating();
    void testDualArrayFunctions();
    void testComparing();
    void testSize();
    void testStress();

    CPPUNIT_TEST_SUITE(JudyArrayTest);
    CPPUNIT_TEST(testIterating);
    CPPUNIT_TEST(testDualArrayFunctions);
    CPPUNIT_TEST(testSize);
    CPPUNIT_TEST(testStress);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(JudyArrayTest);

namespace {
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> >
    getJudyArrayContents(const JudyArray& array) {
        std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> > vals;
        for (JudyArray::const_iterator it = array.begin();
             it != array.end(); ++it)
        {
            vals.push_back(std::make_pair(it.key(), it.value()));
        }
        return vals;
    }
}

void
JudyArrayTest::testIterating()
{
    JudyArray array;
        // Test that things are sane for empty document
    CPPUNIT_ASSERT_EQUAL(array.begin(), array.end());
        // Add some values
    using namespace boost::assign;
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> > values
        = map_list_of(3,2)(5,12)(15,8)(13,10)(7,6)(9,4);
    for (uint32_t i=0; i<values.size(); ++i) {
        array.insert(values[i].first, values[i].second);
    }
        // Create expected result
    std::sort(values.begin(), values.end());
        // Test that we can iterate through const iterator
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> >
        foundVals = getJudyArrayContents(array);
    CPPUNIT_ASSERT_EQUAL(values, foundVals);

    {   // Test that we can alter through non-const iterator
        JudyArray::iterator it = array.begin();
        ++it;
        ++it;
        it.setValue(20);
        CPPUNIT_ASSERT_EQUAL((JudyArray::key_type) 7, it.key());
        CPPUNIT_ASSERT_EQUAL((JudyArray::data_type) 20, array[7]);
        it.remove();
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 5,
                             getJudyArrayContents(array).size());
        CPPUNIT_ASSERT_EQUAL(array.end(), array.find(7));
        values.erase(values.begin() + 2);
        CPPUNIT_ASSERT_EQUAL(values, getJudyArrayContents(array));
            // And that we can continue iterating after removing.
        ++it;
        CPPUNIT_ASSERT_EQUAL((JudyArray::key_type) 9, it.key());
        CPPUNIT_ASSERT_EQUAL((JudyArray::data_type) 4, array[9]);
    }
    {   // Test printing of iterators
        JudyArray::ConstIterator cit = array.begin();
        CPPUNIT_ASSERT_MATCH_REGEX(
                "^ConstIterator\\(Key: 3, Valp: 0x[0-9a-f]{1,16}, Val: 2\\)$",
                cit.toString());
        JudyArray::Iterator it = array.end();
        CPPUNIT_ASSERT_MATCH_REGEX(
                "^Iterator\\(Key: 0, Valp: 0\\)$",
                it.toString());
    }
}

void
JudyArrayTest::testDualArrayFunctions()
{
    JudyArray array1;
    JudyArray array2;
        // Add values to array1
    using namespace boost::assign;
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> > values1
        = map_list_of(3,2)(5,12)(15,8)(13,10)(7,6)(9,4);
    for (uint32_t i=0; i<values1.size(); ++i) {
        array1.insert(values1[i].first, values1[i].second);
    }
        // Add values to array2
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type> > values2
        = map_list_of(4,5)(9,40);
    for (uint32_t i=0; i<values2.size(); ++i) {
        array2.insert(values2[i].first, values2[i].second);
    }
        // Create expected result
    std::sort(values1.begin(), values1.end());
    std::sort(values2.begin(), values2.end());

    CPPUNIT_ASSERT_EQUAL(values1, getJudyArrayContents(array1));
    CPPUNIT_ASSERT_EQUAL(values2, getJudyArrayContents(array2));
    CPPUNIT_ASSERT(array2 < array1);
    CPPUNIT_ASSERT(array1 != array2);
    array1.swap(array2);
    CPPUNIT_ASSERT_EQUAL(values1, getJudyArrayContents(array2));
    CPPUNIT_ASSERT_EQUAL(values2, getJudyArrayContents(array1));
    CPPUNIT_ASSERT(array1 < array2);
    CPPUNIT_ASSERT(array1 != array2);

        // Test some operators
    JudyArray array3;
    for (uint32_t i=0; i<values1.size(); ++i) {
        array3.insert(values1[i].first, values1[i].second);
    }
    CPPUNIT_ASSERT(array1 != array3);
    CPPUNIT_ASSERT_EQUAL(array2, array3);
    CPPUNIT_ASSERT(!(array2 < array3));
}

void
JudyArrayTest::testSize()
{
    JudyArray array;
    CPPUNIT_ASSERT_EQUAL(array.begin(), array.end());
    CPPUNIT_ASSERT(array.empty());
    CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 0, array.size());
    CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 0, array.getMemoryUsage());

        // Test each method one can insert stuff into array
    array.insert(4, 3);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    array.insert(4, 7);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    if (sizeof(JudyArray::size_type) == 4) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 12, array.getMemoryUsage());
    } else if (sizeof(JudyArray::size_type) == 8) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 24, array.getMemoryUsage());
    } else CPPUNIT_FAIL("Unknown size of type");

    array[6] = 8;
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    array[6] = 10;
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    if (sizeof(JudyArray::size_type) == 4) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 20, array.getMemoryUsage());
    } else if (sizeof(JudyArray::size_type) == 8) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 40, array.getMemoryUsage());
    } else CPPUNIT_FAIL("Unknown size of type");

    bool preExisted;
    array.find(8, true, preExisted);
    CPPUNIT_ASSERT_EQUAL(false, preExisted);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    array.find(8, true, preExisted);
    CPPUNIT_ASSERT_EQUAL(true, preExisted);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 3, array.size());
    if (sizeof(JudyArray::size_type) == 4) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 28, array.getMemoryUsage());
    } else if (sizeof(JudyArray::size_type) == 8) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 56, array.getMemoryUsage());
    } else CPPUNIT_FAIL("Unknown size of type");

        // Test each method one can remove stuff in array with
    array.erase(8);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    array.erase(8);
    CPPUNIT_ASSERT_EQUAL(getJudyArrayContents(array).size(), array.size());
    CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 2, array.size());
    if (sizeof(JudyArray::size_type) == 4) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 20, array.getMemoryUsage());
    } else if (sizeof(JudyArray::size_type) == 8) {
        CPPUNIT_ASSERT_EQUAL((JudyArray::size_type) 40, array.getMemoryUsage());
    } else CPPUNIT_FAIL("Unknown size of type");
}

namespace {
    template<typename T>
    std::string toString(const T& m) {
        std::cerr << "#";
        std::ostringstream ost;
        ost << m;
        return ost.str();
    }
}

void
JudyArrayTest::testStress()
{
    // Do a lot of random stuff to both judy array and std::map. Ensure equal
    // behaviour

    JudyArray judyArray;
    typedef std::map<JudyArray::key_type, JudyArray::data_type> StdMap;
    StdMap stdMap;

    boost::rand48 rnd(55);

    for (uint32_t checkpoint=0; checkpoint<50; ++checkpoint) {
        for (uint32_t opnr=0; opnr<500; ++opnr) {
            int optype = rnd() % 100;
            if (optype < 30) { // Insert
                JudyArray::key_type key(rnd() % 500);
                JudyArray::key_type value(rnd());
                judyArray.insert(key, value);
                stdMap[key] = value;
                //std::pair<StdMap::iterator, bool> result
                //        = stdMap.insert(std::make_pair(key, value));
                //if (!result.second) result.first->second = value;
            } else if (optype < 50) { // operator[]
                JudyArray::key_type key(rnd() % 500);
                JudyArray::key_type value(rnd());
                judyArray[key] = value;
                stdMap[key] = value;
            } else if (optype < 70) { // erase()
                JudyArray::key_type key(rnd() % 500);
                CPPUNIT_ASSERT_EQUAL_MSG(
                        toString(judyArray) + toString(stdMap),
                        stdMap.erase(key), judyArray.erase(key));
            } else if (optype < 75) { // size()
                CPPUNIT_ASSERT_EQUAL_MSG(
                        toString(judyArray) + toString(stdMap),
                        stdMap.size(), judyArray.size());
            } else if (optype < 78) { // empty()
                CPPUNIT_ASSERT_EQUAL_MSG(
                        toString(judyArray) + toString(stdMap),
                        stdMap.empty(), judyArray.empty());
            } else { // find()
                JudyArray::key_type key(rnd() % 500);
                JudyArray::iterator it = judyArray.find(key);
                StdMap::iterator it2 = stdMap.find(key);
                CPPUNIT_ASSERT_EQUAL_MSG(
                        toString(judyArray) + toString(stdMap),
                        it2 == stdMap.end(), it == judyArray.end());
                if (it != judyArray.end()) {
                    CPPUNIT_ASSERT_EQUAL_MSG(
                            toString(judyArray) + toString(stdMap),
                            it.key(), it2->first);
                    CPPUNIT_ASSERT_EQUAL_MSG(
                            toString(judyArray) + toString(stdMap),
                            it.value(), it2->second);
                }
            }
        }
            // Ensure judy array contents is equal to std::map's at this point
        StdMap tmpMap;
        for (JudyArray::const_iterator it = judyArray.begin();
             it != judyArray.end(); ++it)
        {
            tmpMap[it.key()] = it.value();
        }
        CPPUNIT_ASSERT_EQUAL(stdMap, tmpMap);
    }
}

} // storage
