// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/enumstore.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/singlestringpostattribute.h>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/multistringpostattribute.h>

#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/searchlib/attribute/singlestringpostattribute.hpp>
#include <vespa/searchlib/attribute/multistringpostattribute.hpp>

#include <vespa/log/log.h>
LOG_SETUP("stringattribute_test");

using search::attribute::CollectionType;
using search::attribute::IAttributeVector;
using search::attribute::SearchContext;
using search::attribute::StringSearchHelper;
using vespalib::datastore::EntryRef;
using namespace search;

typedef ArrayStringAttribute ArrayStr;
typedef WeightedSetStringAttribute WeightedSetStr;
typedef ArrayStringPostingAttribute ArrayStrPosting;
typedef WeightedSetStringPostingAttribute WeightedSetStrPosting;
typedef attribute::Config Config;
typedef attribute::BasicType BasicType;

template <typename Attribute>
void
addDocs(Attribute & vec, uint32_t numDocs)
{
    for (uint32_t i = 0; i < numDocs; ++i) {
        IAttributeVector::DocId doc;
        EXPECT_TRUE(vec.addDoc(doc));
        EXPECT_TRUE(doc == i);
        EXPECT_TRUE(vec.getNumDocs() == i + 1);
        EXPECT_TRUE(vec.getValueCount(doc) == 0);
    }
    EXPECT_TRUE(vec.getNumDocs() == numDocs);
}

template <typename Attribute>
void
checkCount(Attribute & vec, uint32_t doc, uint32_t valueCount,
                                uint32_t numValues, const vespalib::string & value)
{
    std::vector<vespalib::string> buffer(valueCount);
    EXPECT_TRUE(static_cast<uint32_t>(vec.getValueCount(doc)) == valueCount);
    EXPECT_TRUE(vec.get(doc, buffer.data(), buffer.size()) == valueCount);
    EXPECT_TRUE(std::count(buffer.begin(), buffer.end(), value) == numValues);
}

namespace {

template <typename T0, typename T1>
auto zipped_and_sorted_by_first(const std::vector<T0>& a, const std::vector<T1>& b) -> std::vector<std::pair<T0, T1>> {
    std::vector<std::pair<T0, T1>> combined;
    assert(a.size() == b.size());
    for (size_t i = 0; i < a.size(); ++i) {
        combined.emplace_back(a[i], b[i]);
    }
    std::sort(combined.begin(), combined.end(), [](const auto& lhs, const auto& rhs){
        return (lhs.first < rhs.first);
    });
    return combined;
}

}

template <typename Attribute>
void
testMultiValue(Attribute & attr, uint32_t numDocs)
{
    EXPECT_TRUE(attr.getNumDocs() == 0);

    // generate two sets of unique strings
    std::vector<vespalib::string> uniqueStrings;
    uniqueStrings.reserve(numDocs - 1);
    for (uint32_t i = 0; i < numDocs - 1; ++i) {
        char unique[16];
        sprintf(unique, i < 10 ? "enum0%u" : "enum%u", i);
        uniqueStrings.emplace_back(unique);
    }
    ASSERT_TRUE(std::is_sorted(uniqueStrings.begin(), uniqueStrings.end()));

    std::vector<vespalib::string> newUniques;
    newUniques.reserve(numDocs - 1);
    for (uint32_t i = 0; i < numDocs - 1; ++i) {
        char unique[16];
        sprintf(unique, i < 10 ? "unique0%u" : "unique%u", i);
        newUniques.emplace_back(unique);
    }

    // add docs
    addDocs(attr, numDocs);

    // insert values
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = doc;
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(attr.append(doc, uniqueStrings[j], 1));
        }
        attr.commit();
    }

    // check values and enums
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = attr.getValueCount(doc);
        EXPECT_TRUE(valueCount == doc);

        // test get first
        if (valueCount == 0) {
            EXPECT_TRUE(attr.get(doc) == nullptr);
            EXPECT_TRUE(attr.getEnum(doc) == std::numeric_limits<uint32_t>::max());
        } else if (!attr.hasWeightedSetType()) {
            EXPECT_EQUAL(vespalib::string(attr.get(doc)), uniqueStrings[0]);
            uint32_t e;
            EXPECT_TRUE(attr.findEnum(uniqueStrings[0].c_str(), e));
            EXPECT_EQUAL(1u, attr.findFoldedEnums(uniqueStrings[0].c_str()).size());
            EXPECT_EQUAL(e, attr.findFoldedEnums(uniqueStrings[0].c_str())[0]);
            EXPECT_TRUE(attr.getEnum(doc) == e);
        }

        // test get all
        std::vector<vespalib::string> values(valueCount);
        ASSERT_TRUE(attr.get(doc, values.data(), valueCount) == valueCount);

        std::vector<uint32_t> enums(valueCount);
        ASSERT_TRUE((static_cast<search::attribute::IAttributeVector &>(attr)).get(doc, enums.data(), valueCount) == valueCount);

        auto combined = zipped_and_sorted_by_first(values, enums);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(combined[j].first == uniqueStrings[j]);
            uint32_t e = 100;
            EXPECT_TRUE(attr.findEnum(combined[j].first.c_str(), e));
            EXPECT_TRUE(combined[j].second == e);
        }
    }

    // check for correct refcounts
    for (uint32_t i = 0; i < uniqueStrings.size(); ++i) {
        enumstore::Index idx;
        EXPECT_TRUE(attr.getEnumStore().find_index(uniqueStrings[i].c_str(), idx));
        uint32_t expectedUsers = numDocs - 1 - i;
        EXPECT_EQUAL(expectedUsers, attr.getEnumStore().get_ref_count(idx));
    }

    // clear and insert new unique strings
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t oldValueCount = doc;
        uint32_t valueCount = numDocs - 1 - doc;
        //LOG(info, "clear and insert: doc = %u, valueCount = %u", doc, valueCount);
        EXPECT_TRUE(attr.clearDoc(doc) == oldValueCount);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(attr.append(doc, newUniques[j], 1));
        }
        attr.commit();
    }

    // check values and enums
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = attr.getValueCount(doc);
        uint32_t expectedValueCount = numDocs - 1 - doc;
        EXPECT_TRUE(valueCount == expectedValueCount);

        // test get all
        std::vector<vespalib::string> values(valueCount);
        EXPECT_TRUE(attr.get(doc, values.data(), valueCount) == valueCount);

        std::vector<uint32_t> enums(valueCount);
        EXPECT_TRUE((static_cast<search::attribute::IAttributeVector &>(attr)).get(doc, enums.data(), valueCount) == valueCount);

        auto combined = zipped_and_sorted_by_first(values, enums);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(combined[j].first == newUniques[j]);
            uint32_t e = 100;
            EXPECT_TRUE(attr.findEnum(combined[j].first.c_str(), e));
            EXPECT_TRUE(combined[j].second == e);
        }
    }

    // check that enumXX strings are removed
    for (uint32_t i = 0; i < uniqueStrings.size(); ++i) {
        uint32_t e;
        EXPECT_TRUE(!attr.findEnum(uniqueStrings[i].c_str(), e));
    }

    // check for correct refcounts
    for (uint32_t i = 0; i < newUniques.size(); ++i) {
        enumstore::Index idx;
        EXPECT_TRUE(attr.getEnumStore().find_index(newUniques[i].c_str(), idx));
        uint32_t expectedUsers = numDocs - 1 - i;
        EXPECT_EQUAL(expectedUsers, attr.getEnumStore().get_ref_count(idx));
    }
}

TEST("testMultiValue")
{
    uint32_t numDocs = 16;

    { // Array String Attribute
        ArrayStr attr("a-string");
        testMultiValue(attr, numDocs);
    }
    { // Weighted Set String Attribute
        WeightedSetStr attr("ws-string",
                            Config(BasicType::STRING, CollectionType::WSET));
        testMultiValue(attr, numDocs);
    }
    { // Array String Posting Attribute
        Config cfg(BasicType::STRING, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        ArrayStrPosting attr("a-fs-string", cfg);
        testMultiValue(attr, numDocs);
    }
    { // Weighted Set String Posting Attribute
        Config cfg(BasicType::STRING, CollectionType::WSET);
        cfg.setFastSearch(true);
        WeightedSetStrPosting attr("ws-fs-string", cfg);
        testMultiValue(attr, numDocs);
    }
}

TEST("testMultiValueMultipleClearDocBetweenCommit")
{
    // This is also tested for all array attributes in attribute unit test
    ArrayStr mvsa("a-string");
    uint32_t numDocs = 50;
    addDocs(mvsa, numDocs);
    std::vector<vespalib::string> buffer(numDocs);

    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = doc;
        EXPECT_TRUE(mvsa.clearDoc(doc) == 0);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(mvsa.append(doc, "first", 1));
        }
        EXPECT_TRUE(mvsa.clearDoc(doc) == 0);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(mvsa.append(doc, "second", 1));
        }
        mvsa.commit();

        // check for correct values
        checkCount(mvsa, doc, valueCount, valueCount, "second");
    }
}


TEST("testMultiValueRemove")
{
    // This is also tested for all array attributes in attribute unit test
    ArrayStr mvsa("a-string");
    uint32_t numDocs = 50;
    addDocs(mvsa, numDocs);
    std::vector<vespalib::string> buffer(9);

    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        EXPECT_TRUE(mvsa.append(doc, "one", 1));
        for (uint32_t i = 0; i < 3; ++i) {
            EXPECT_TRUE(mvsa.append(doc, "three", 1));
        }
        for (uint32_t i = 0; i < 5; ++i) {
            EXPECT_TRUE(mvsa.append(doc, "five", 1));
        }

        mvsa.commit();
        checkCount(mvsa, doc, 9, 1, "one");
        checkCount(mvsa, doc, 9, 3, "three");
        checkCount(mvsa, doc, 9, 5, "five");

        EXPECT_TRUE(mvsa.remove(doc, "zero", 1));
        mvsa.commit();
        checkCount(mvsa, doc, 9, 1, "one");
        checkCount(mvsa, doc, 9, 3, "three");
        checkCount(mvsa, doc, 9, 5, "five");

        EXPECT_TRUE(mvsa.remove(doc, "one", 1));
        mvsa.commit();
        checkCount(mvsa, doc, 8, 0, "one");
        checkCount(mvsa, doc, 8, 3, "three");
        checkCount(mvsa, doc, 8, 5, "five");

        EXPECT_TRUE(mvsa.remove(doc, "five", 1));
        mvsa.commit();
        checkCount(mvsa, doc, 3, 0, "one");
        checkCount(mvsa, doc, 3, 3, "three");
        checkCount(mvsa, doc, 3, 0, "five");
    }
}

void
testDefaultValueOnAddDoc(AttributeVector & v)
{
    EXPECT_EQUAL(0u, v.getNumDocs());
    v.addReservedDoc();
    EXPECT_EQUAL(1u, v.getNumDocs());
    EXPECT_TRUE( IEnumStore::Index(EntryRef(v.getEnum(0))).valid() );
    uint32_t doc(7);
    EXPECT_TRUE( v.addDoc(doc) );
    EXPECT_EQUAL(1u, doc);
    EXPECT_EQUAL(2u, v.getNumDocs());
    EXPECT_TRUE( IEnumStore::Index(EntryRef(v.getEnum(doc))).valid() );
    EXPECT_EQUAL(0u, strlen(v.getString(doc, NULL, 0)));
}

template <typename Attribute>
void
testSingleValue(Attribute & svsa, Config &cfg)
{
    StringAttribute & v = svsa;
    const char * t = "not defined";
    uint32_t doc = 2000;
    uint32_t e1 = 2000;
    uint32_t e2 = 2000;
    uint32_t numDocs = 1000;
    char tmp[32];

    // add docs
    for (uint32_t i = 0; i < numDocs; ++i) {
        EXPECT_TRUE( v.addDoc(doc) );
        EXPECT_TRUE( doc == i );
        EXPECT_TRUE( v.getNumDocs() == i + 1 );
        EXPECT_TRUE( v.getValueCount(doc) == 1 );
        EXPECT_TRUE( ! IEnumStore::Index(EntryRef(v.getEnum(doc))).valid() );
    }

    std::map<vespalib::string, uint32_t> enums;
    // 10 unique strings
    for (uint32_t i = 0; i < numDocs; ++i) {
        sprintf(tmp, "enum%u", i % 10);
        EXPECT_TRUE( v.update(i, tmp) );
        EXPECT_TRUE( v.getValueCount(i) == 1 );
        EXPECT_TRUE( ! IEnumStore::Index(EntryRef(v.getEnum(i))).valid() );
        if ((i % 10) == 9) {
            v.commit();
            for (uint32_t j = i - 9; j <= i; ++j) {
                sprintf(tmp, "enum%u", j % 10);
                EXPECT_TRUE( strcmp(t = v.get(j), tmp) == 0 );
                e1 = v.getEnum(j);
                EXPECT_TRUE( v.findEnum(t, e2) );
                EXPECT_TRUE( e1 == e2 );
                if (enums.count(vespalib::string(t)) == 0) {
                    enums[vespalib::string(t)] = e1;
                } else {
                    EXPECT_TRUE( e1 == enums[vespalib::string(t)]);
                    EXPECT_TRUE( e2 == enums[vespalib::string(t)]);
                }
            }
        }
    }

    // 1000 unique strings
    for (uint32_t i = 0; i < numDocs; ++i) {
        sprintf(tmp, "unique%u", i);
        EXPECT_TRUE( v.update(i, tmp) );
        sprintf(tmp, "enum%u", i % 10);
        EXPECT_TRUE( strcmp(v.get(i), tmp) == 0 );
        if ((i % 10) == 9) {
            //LOG(info, "commit: i = %u", i);
            v.commit();
            for (uint32_t j = i - 9; j <= i; ++j) {
                sprintf(tmp, "unique%u", j);
                EXPECT_TRUE( strcmp(t = v.get(j), tmp) == 0 );
                e1 = v.getEnum(j);
                EXPECT_TRUE( v.findEnum(t, e2) );
                EXPECT_TRUE( e1 == e2 );
            }
        }
    }

    // check that enumX strings are removed (
    for (uint32_t i = 0; i < 10; ++i) {
        sprintf(tmp, "enum%u", i);
        EXPECT_TRUE( !v.findEnum(tmp, e1) );
    }


    Attribute load("load", cfg);
    svsa.save(load.getBaseFileName());
    load.load();
}

TEST("testSingleValue")
{
    EXPECT_EQUAL(24u, sizeof(SearchContext));
    EXPECT_EQUAL(32u, sizeof(StringSearchHelper));
    EXPECT_EQUAL(80u, sizeof(attribute::SingleStringEnumSearchContext));
    {
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        SingleValueStringAttribute svsa("svsa", cfg);
        testSingleValue(svsa, cfg);

        SingleValueStringAttribute svsb("svsa", cfg);
        testDefaultValueOnAddDoc(svsb);
    }
    {
        Config cfg(BasicType::STRING, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        SingleValueStringPostingAttribute svsa("svspb", cfg);
        testSingleValue(svsa, cfg);

        SingleValueStringPostingAttribute svsb("svspb", cfg);
        testDefaultValueOnAddDoc(svsb);
    }
}

TEST("test uncased match") {
    QueryTermUCS4 xyz("xyz", QueryTermSimple::Type::WORD);
    StringSearchHelper helper(xyz, false);
    EXPECT_FALSE(helper.isCased());
    EXPECT_FALSE(helper.isPrefix());
    EXPECT_FALSE(helper.isRegex());
    EXPECT_FALSE(helper.isMatch("axyz"));
    EXPECT_FALSE(helper.isMatch("xyza"));
    EXPECT_TRUE(helper.isMatch("xyz"));
    EXPECT_TRUE(helper.isMatch("XyZ"));
    EXPECT_FALSE(helper.isMatch("Xy"));
}

TEST("test uncased prefix match") {
    QueryTermUCS4 xyz("xyz", QueryTermSimple::Type::PREFIXTERM);
    StringSearchHelper helper(xyz, false);
    EXPECT_FALSE(helper.isCased());
    EXPECT_TRUE(helper.isPrefix());
    EXPECT_FALSE(helper.isRegex());
    EXPECT_FALSE(helper.isMatch("axyz"));
    EXPECT_TRUE(helper.isMatch("xyza"));
    EXPECT_TRUE(helper.isMatch("xYza"));
    EXPECT_TRUE(helper.isMatch("xyz"));
    EXPECT_TRUE(helper.isMatch("XyZ"));
    EXPECT_FALSE(helper.isMatch("Xy"));
}

TEST("test cased match") {
    QueryTermUCS4 xyz("XyZ", QueryTermSimple::Type::WORD);
    StringSearchHelper helper(xyz, true);
    EXPECT_TRUE(helper.isCased());
    EXPECT_FALSE(helper.isPrefix());
    EXPECT_FALSE(helper.isRegex());
    EXPECT_FALSE(helper.isMatch("aXyZ"));
    EXPECT_FALSE(helper.isMatch("XyZa"));
    EXPECT_FALSE(helper.isMatch("xyz"));
    EXPECT_FALSE(helper.isMatch("Xyz"));
    EXPECT_TRUE(helper.isMatch("XyZ"));
    EXPECT_FALSE(helper.isMatch("Xy"));
}

TEST("test cased prefix match") {
    QueryTermUCS4 xyz("XyZ", QueryTermSimple::Type::PREFIXTERM);
    StringSearchHelper helper(xyz, true);
    EXPECT_TRUE(helper.isCased());
    EXPECT_TRUE(helper.isPrefix());
    EXPECT_FALSE(helper.isRegex());
    EXPECT_FALSE(helper.isMatch("aXyZ"));
    EXPECT_TRUE(helper.isMatch("XyZa"));
    EXPECT_FALSE(helper.isMatch("xyZa"));
    EXPECT_FALSE(helper.isMatch("xyz"));
    EXPECT_FALSE(helper.isMatch("Xyz"));
    EXPECT_TRUE(helper.isMatch("XyZ"));
    EXPECT_FALSE(helper.isMatch("Xy"));
}

TEST("test uncased regex match") {
    QueryTermUCS4 xyz("x[yY]+Z", QueryTermSimple::Type::REGEXP);
    StringSearchHelper helper(xyz, false);
    EXPECT_FALSE(helper.isCased());
    EXPECT_FALSE(helper.isPrefix());
    EXPECT_TRUE(helper.isRegex());
    EXPECT_TRUE(helper.isMatch("axyZ"));
    EXPECT_TRUE(helper.isMatch("xyZa"));
    EXPECT_TRUE(helper.isMatch("xyZ"));
    EXPECT_TRUE(helper.isMatch("xyz"));
    EXPECT_FALSE(helper.isMatch("xyaZ"));
    EXPECT_FALSE(helper.isMatch("xy"));
}

TEST("test cased regex match") {
    QueryTermUCS4 xyz("x[Y]+Z", QueryTermSimple::Type::REGEXP);
    StringSearchHelper helper(xyz, true);
    EXPECT_TRUE(helper.isCased());
    EXPECT_FALSE(helper.isPrefix());
    EXPECT_TRUE(helper.isRegex());
    EXPECT_TRUE(helper.isMatch("axYZ"));
    EXPECT_TRUE(helper.isMatch("xYZa"));
    EXPECT_FALSE(helper.isMatch("xyZ"));
    EXPECT_TRUE(helper.isMatch("xYZ"));
    EXPECT_FALSE(helper.isMatch("xYz"));
    EXPECT_FALSE(helper.isMatch("xaYZ"));
    EXPECT_FALSE(helper.isMatch("xY"));
}

TEST("test fuzzy match") {
    QueryTermUCS4 xyz("xyz", QueryTermSimple::Type::FUZZYTERM);
    StringSearchHelper helper(xyz, false);
    EXPECT_FALSE(helper.isCased());
    EXPECT_FALSE(helper.isPrefix());
    EXPECT_FALSE(helper.isRegex());
    EXPECT_TRUE(helper.isFuzzy());
    EXPECT_TRUE(helper.isMatch("xyz"));
    EXPECT_TRUE(helper.isMatch("xyza"));
    EXPECT_TRUE(helper.isMatch("xyv"));
    EXPECT_TRUE(helper.isMatch("xy"));
    EXPECT_TRUE(helper.isMatch("x"));
    EXPECT_TRUE(helper.isMatch("xvv"));
    EXPECT_FALSE(helper.isMatch("vvv"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
