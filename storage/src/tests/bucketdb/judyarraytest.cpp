// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/bucketdb/judyarray.h>
#include <boost/random.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <map>
#include <vector>

using namespace ::testing;

namespace storage {

namespace {
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type>>
    getJudyArrayContents(const JudyArray& array) {
        std::vector<std::pair<JudyArray::key_type, JudyArray::data_type>> vals;
        for (JudyArray::const_iterator it = array.begin();
             it != array.end(); ++it)
        {
            vals.push_back(std::make_pair(it.key(), it.value()));
        }
        return vals;
    }
}

TEST(JudyArrayTest, iterating) {
    JudyArray array;
    // Test that things are sane for empty document
    ASSERT_EQ(array.begin(), array.end());
    // Add some values
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type>> values({
        {3, 2}, {5, 12}, {15, 8}, {13, 10}, {7, 6}, {9, 4}
    });
    for (uint32_t i=0; i<values.size(); ++i) {
        array.insert(values[i].first, values[i].second);
    }
    // Create expected result
    std::sort(values.begin(), values.end());
    // Test that we can iterate through const iterator
    auto foundVals = getJudyArrayContents(array);
    ASSERT_EQ(values, foundVals);

    {   // Test that we can alter through non-const iterator
        JudyArray::iterator it = array.begin();
        ++it;
        ++it;
        it.setValue(20);
        ASSERT_EQ((JudyArray::key_type) 7, it.key());
        ASSERT_EQ((JudyArray::data_type) 20, array[7]);
        it.remove();
        ASSERT_EQ((JudyArray::size_type) 5, getJudyArrayContents(array).size());
        ASSERT_EQ(array.end(), array.find(7));
        values.erase(values.begin() + 2);
        ASSERT_EQ(values, getJudyArrayContents(array));
        // And that we can continue iterating after removing.
        ++it;
        ASSERT_EQ((JudyArray::key_type) 9, it.key());
        ASSERT_EQ((JudyArray::data_type) 4, array[9]);
    }
    {   // Test printing of iterators
        JudyArray::ConstIterator cit = array.begin();
        EXPECT_THAT(cit.toString(), MatchesRegex("^ConstIterator\\(Key: 3, Valp: 0x[0-9a-f]{1,16}, Val: 2\\)$"));
        JudyArray::Iterator it = array.end();
        EXPECT_THAT(it.toString(), MatchesRegex("^Iterator\\(Key: 0, Valp: (0x)?0\\)$"));
    }
}

TEST(JudyArrayTest, dual_array_functions) {
    JudyArray array1;
    JudyArray array2;
    // Add values to array1
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type>> values1({
        {3, 2}, {5, 12}, {15, 8}, {13, 10}, {7, 6}, {9, 4}
    });
    for (uint32_t i=0; i<values1.size(); ++i) {
        array1.insert(values1[i].first, values1[i].second);
    }
    // Add values to array2
    std::vector<std::pair<JudyArray::key_type, JudyArray::data_type>> values2({
        {4, 5}, {9, 40}
    });
    for (uint32_t i=0; i<values2.size(); ++i) {
        array2.insert(values2[i].first, values2[i].second);
    }
    // Create expected result
    std::sort(values1.begin(), values1.end());
    std::sort(values2.begin(), values2.end());

    EXPECT_EQ(values1, getJudyArrayContents(array1));
    EXPECT_EQ(values2, getJudyArrayContents(array2));
    EXPECT_LT(array2, array1);
    EXPECT_NE(array1, array2);
    array1.swap(array2);
    EXPECT_EQ(values1, getJudyArrayContents(array2));
    EXPECT_EQ(values2, getJudyArrayContents(array1));
    EXPECT_LT(array1, array2);
    EXPECT_NE(array1, array2);

    // Test some operators
    JudyArray array3;
    for (uint32_t i=0; i<values1.size(); ++i) {
        array3.insert(values1[i].first, values1[i].second);
    }
    EXPECT_NE(array1, array3);
    EXPECT_EQ(array2, array3);
    EXPECT_FALSE(array2 < array3);
}

TEST(JudyArrayTest, size) {
    JudyArray array;
    EXPECT_EQ(array.begin(), array.end());
    EXPECT_TRUE(array.empty());
    EXPECT_EQ((JudyArray::size_type) 0, array.size());
    EXPECT_EQ((JudyArray::size_type) 0, array.getMemoryUsage());

    // Test each method one can insert stuff into array
    array.insert(4, 3);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    array.insert(4, 7);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    EXPECT_EQ((JudyArray::size_type) 24, array.getMemoryUsage());

    array[6] = 8;
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    array[6] = 10;
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    EXPECT_EQ((JudyArray::size_type) 40, array.getMemoryUsage());

    bool preExisted;
    array.find(8, true, preExisted);
    EXPECT_EQ(false, preExisted);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    array.find(8, true, preExisted);
    EXPECT_EQ(true, preExisted);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    EXPECT_EQ((JudyArray::size_type) 3, array.size());
    EXPECT_EQ((JudyArray::size_type) 56, array.getMemoryUsage());

    // Test each method one can remove stuff in array with
    array.erase(8);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    array.erase(8);
    EXPECT_EQ(getJudyArrayContents(array).size(), array.size());
    EXPECT_EQ((JudyArray::size_type) 2, array.size());
    EXPECT_EQ((JudyArray::size_type) 40, array.getMemoryUsage());
}

TEST(JudyArrayTest, stress) {
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
            } else if (optype < 50) { // operator[]
                JudyArray::key_type key(rnd() % 500);
                JudyArray::key_type value(rnd());
                judyArray[key] = value;
                stdMap[key] = value;
            } else if (optype < 70) { // erase()
                JudyArray::key_type key(rnd() % 500);
                EXPECT_EQ(stdMap.erase(key), judyArray.erase(key));
            } else if (optype < 75) { // size()
                EXPECT_EQ(stdMap.size(), judyArray.size());
            } else if (optype < 78) { // empty()
                EXPECT_EQ(stdMap.empty(), judyArray.empty());
            } else { // find()
                JudyArray::key_type key(rnd() % 500);
                auto it = judyArray.find(key);
                auto it2 = stdMap.find(key);
                EXPECT_EQ(it2 == stdMap.end(), it == judyArray.end());
                if (it != judyArray.end()) {
                    EXPECT_EQ(it.key(), it2->first);
                    EXPECT_EQ(it.value(), it2->second);
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
        EXPECT_EQ(stdMap, tmpMap);
    }
}

} // storage
