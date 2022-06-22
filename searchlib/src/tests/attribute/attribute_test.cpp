// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/multienumattribute.hpp>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/multivalueattribute.hpp>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/singlenumericpostattribute.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/weighted_type_test_utils.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastos/file.h>
#include <cmath>
#include <filesystem>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("attribute_test");


using namespace document;
using std::shared_ptr;
using search::common::FileHeaderContext;
using search::index::DummyFileHeaderContext;
using search::attribute::BasicType;
using search::attribute::IAttributeVector;
using vespalib::stringref;
using vespalib::string;

namespace {

string tmpDir("tmp");
string clsDir("clstmp");
string asuDir("asutmp");

}

namespace search {

namespace {

string empty;

string make_scoped_trace_msg(string prefix, const search::attribute::Config &config)
{
    return prefix + ", basic type=" + config.basicType().asString() + ", collection type=" + config.collectionType().asString();
}

bool
isUnsignedSmallIntAttribute(const BasicType::Type &type)
{
    switch (type) {
    case BasicType::BOOL:
    case BasicType::UINT2:
    case BasicType::UINT4:
        return true;
    default:
        return false;
    }
}

bool
isUnsignedSmallIntAttribute(const AttributeVector &a)
{
    return isUnsignedSmallIntAttribute(a.getBasicType());
}

template <typename BufferType>
void
expectZero(const BufferType &b)
{
    EXPECT_EQ(0, b);
}

template <>
void
expectZero(const string &b)
{
    EXPECT_EQ(empty, b);
}

uint64_t
statSize(const string &fileName)
{
    FastOS_StatInfo statInfo;
    bool stat_result = true;
    EXPECT_TRUE(FastOS_File::Stat(fileName.c_str(), &statInfo)) << (stat_result = false, "");
    if (stat_result) {
        return statInfo._size;
    } else {
        return 0u;
    }
}

uint64_t
statSize(const AttributeVector &a)
{
    vespalib::string baseFileName = a.getBaseFileName();
    uint64_t resultSize = statSize(baseFileName + ".dat");
    if (a.hasMultiValue()) {
        resultSize += statSize(baseFileName + ".idx");
    }
    if (a.hasWeightedSetType()) {
        resultSize += statSize(baseFileName + ".weight");
    }
    if (a.hasEnum() && a.getEnumeratedSave()) {
        resultSize += statSize(baseFileName + ".udat");
    }
    return resultSize;
}


bool
preciseEstimatedSize(const AttributeVector &a)
{
    if (a.getBasicType() == BasicType::STRING) {
        return false; // Using average of string lengths, can be somewhat off
    }
    return true;
}

string
baseFileName(const string &attrName)
{
    return tmpDir + "/" + attrName;
}

AttributeVector::SP
createAttribute(stringref attrName, const search::attribute::Config &cfg)
{
    return search::AttributeFactory::createAttribute(baseFileName(attrName), cfg);
}

vespalib::string
replace_suffix(AttributeVector &v, const vespalib::string &suffix)
{
    vespalib::string name = v.getName();
    if (name.size() >= suffix.size()) {
        name.resize(name.size() - suffix.size());
    }
    return name + suffix;
}

template <typename Container, typename V>
bool contains(const Container& c, size_t elems, const V& value) {
    auto end = c.begin() + elems;
    return (std::find(c.begin(), end, value) != end);
}

template <typename Container, typename V>
bool contains_value(const Container& c, size_t elems, const V& value) {
    auto end = c.begin() + elems;
    return (std::find_if(c.begin(), end, [&value](const auto& ws_elem) {
        return (ws_elem.getValue() == value);
    }) != end);
}

}

using attribute::CollectionType;
using attribute::Config;

class AttributeTest : public ::testing::Test
{
protected:
    typedef AttributeVector::SP AttributePtr;

    void addDocs(const AttributePtr & v, size_t sz);
    void addClearedDocs(const AttributePtr & v, size_t sz);
    template <typename VectorType>
    void populateSimple(VectorType &ptr, uint32_t docIdLow, uint32_t docIdHigh);
    template <typename VectorType>
    void populate(VectorType & ptr, unsigned seed);
    template <typename VectorType, typename BufferType>
    void compare(VectorType & a, VectorType & b);

    void testReloadInt(const AttributePtr & a, size_t numDocs);
    void testReloadString(const AttributePtr & a, size_t numDocs);
    template <typename VectorType, typename BufferType>
    void testReload(const AttributePtr & a);
    void testMemorySaverInt(const AttributePtr & a, size_t numDocs);
    void testMemorySaverString(const AttributePtr & a, size_t numDocs);
    template <typename VectorType, typename BufferType>
    void testMemorySaver(const AttributePtr & a);

    void testReload();
    void testHasLoadData();
    void testMemorySaver();

    void commit(const AttributePtr & ptr);

    template <typename T>
    void fillNumeric(std::vector<T> & values, uint32_t numValues);
    void fillString(std::vector<string> & values, uint32_t numValues);
    template <typename VectorType, typename BufferType>
    bool appendToVector(VectorType & v, uint32_t doc, uint32_t valueCount,
                        const std::vector<BufferType> & values);
    template <typename BufferType>
    bool checkCount(const AttributePtr & ptr, uint32_t doc, uint32_t valueCount,
                    uint32_t numValues, const BufferType & value);
    template <typename BufferType>
    bool checkContent(const AttributePtr & ptr, uint32_t doc, uint32_t valueCount,
                      uint32_t range, const std::vector<BufferType> & values);

    // CollectionType::SINGLE
    template <typename VectorType, typename BufferType, typename BaseType>
    void testSingle(const AttributePtr & ptr, const std::vector<BufferType> & values);
    void testSingle();

    // CollectionType::ARRAY
    template <typename VectorType, typename BufferType>
    void testArray(const AttributePtr & ptr, const std::vector<BufferType> & values);
    void testArray();

    // CollectionType::WSET

    template <typename VectorType, typename BufferType>
    void testWeightedSet(const AttributePtr & ptr, const std::vector<BufferType> & values);
    void testWeightedSet();
    void testBaseName();

    template <typename VectorType, typename BufferType>
    void testArithmeticValueUpdate(const AttributePtr & ptr);
    void testArithmeticValueUpdate();

    template <typename VectorType, typename BaseType, typename BufferType>
    void testArithmeticWithUndefinedValue(const AttributePtr & ptr, BaseType before, BaseType after);
    void testArithmeticWithUndefinedValue();

    template <typename VectorType, typename BufferType>
    void testMapValueUpdate(const AttributePtr & ptr, BufferType initValue,
                            const FieldValue & initFieldValue, const FieldValue & nonExistant,
                            bool removeIfZero, bool createIfNonExistant);
    void testMapValueUpdate();

    void testStatus();
    void testNullProtection();
    void testGeneration(const AttributePtr & attr, bool exactStatus);
    void testGeneration();

    void testCreateSerialNum();

    void testPredicateHeaderTags();

    template <typename VectorType, typename BufferType>
    void
    testCompactLidSpace(const Config &config, bool fast_search);

    template <typename VectorType, typename BufferType>
    void
    testCompactLidSpace(const Config &config);

    void
    testCompactLidSpaceForPredicateAttribute(const Config &config);

    void
    testCompactLidSpace(const Config &config);

    void testCompactLidSpace();

    void test_default_value_ref_count_is_updated_after_shrink_lid_space();

    template <typename AttributeType>
    void requireThatAddressSpaceUsageIsReported(const Config &config, bool fastSearch);
    template <typename AttributeType>
    void requireThatAddressSpaceUsageIsReported(const Config &config);
    void requireThatAddressSpaceUsageIsReported();

    template <typename VectorType, typename BufferType>
    void testReaderDuringLastUpdate(const Config &config, bool fastSearch, bool compact);
    template <typename VectorType, typename BufferType>
    void testReaderDuringLastUpdate(const Config &config);
    void testReaderDuringLastUpdate();

    void testPendingCompaction();
    void testConditionalCommit();

    int test_paged_attribute(const vespalib::string& name, const vespalib::string& swapfile, const search::attribute::Config& cfg);
    void test_paged_attributes();

public:
    AttributeTest();
};

AttributeTest::AttributeTest() = default;

void AttributeTest::testBaseName()
{
    attribute::BaseName v("attr1");
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_TRUE(v.getDirName().empty());
    v = "attribute/attr1/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "attribute/attr1");
    v = "attribute/attr1/snapshot-X/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "attribute/attr1/snapshot-X");
    v = "/attribute/attr1/snapshot-X/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "/attribute/attr1/snapshot-X");
    v = "index.1/1.ready/attribute/attr1/snapshot-X/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "index.1/1.ready/attribute/attr1/snapshot-X");
    v = "/index.1/1.ready/attribute/attr1/snapshot-X/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "/index.1/1.ready/attribute/attr1/snapshot-X");
    v = "xxxyyyy/zzz/index.1/1.ready/attribute/attr1/snapshot-X/attr1";
    EXPECT_EQ(v.getAttributeName(), "attr1");
    EXPECT_EQ(v.getDirName(), "xxxyyyy/zzz/index.1/1.ready/attribute/attr1/snapshot-X");
}

void AttributeTest::addDocs(const AttributePtr & v, size_t sz)
{
    if (sz) {
        AttributeVector::DocId docId;
        for(size_t i(0); i< sz; i++) {
            EXPECT_TRUE( v->addDoc(docId) );
        }
        EXPECT_TRUE( docId+1 == sz );
        EXPECT_TRUE( v->getNumDocs() == sz );
        commit(v);
    }
}


void AttributeTest::addClearedDocs(const AttributePtr & v, size_t sz)
{
    if (sz) {
        AttributeVector::DocId docId;
        for(size_t i(0); i< sz; i++) {
            EXPECT_TRUE( v->addDoc(docId) );
            v->clearDoc(i);
        }
        EXPECT_TRUE( docId+1 == sz );
        EXPECT_TRUE( v->getNumDocs() == sz );
        commit(v);
    }
}


template <>
void AttributeTest::populate(IntegerAttribute & v, unsigned seed)
{
    srand(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = (rand() % 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rand(), weight) );
            }
        } else {
            EXPECT_TRUE( v.update(i, rand()) );
        }
    }
    v.commit();
}

template <>
void AttributeTest::populate(FloatingPointAttribute & v, unsigned seed)
{
    srand(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = (rand() % 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rand() * 1.25, weight) );
            }
        } else {
            EXPECT_TRUE( v.update(i, rand() * 1.25) );
        }
    }
    v.commit();
}

template <>
void AttributeTest::populate(StringAttribute & v, unsigned seed)
{
    RandomGenerator rnd(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = rnd.rand(0, 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rnd.getRandomString(2, 50), weight) );
            }
        } else {
            EXPECT_TRUE( v.update(i, rnd.getRandomString(2, 50)) );
        }
    }
    v.commit();
}

void populateSimpleUncommitted(IntegerAttribute & v, uint32_t docIdLow, uint32_t docIdHigh)
{
    for (uint32_t docId(docIdLow); docId < docIdHigh; ++docId) {
        v.clearDoc(docId);
        EXPECT_TRUE( v.update(docId, docId + 1) );
    }
}

template <>
void AttributeTest::populateSimple(IntegerAttribute & v, uint32_t docIdLow, uint32_t docIdHigh)
{
    populateSimpleUncommitted(v, docIdLow, docIdHigh);
    v.commit();
}

template <typename VectorType, typename BufferType>
void AttributeTest::compare(VectorType & a, VectorType & b)
{
    EXPECT_EQ(a.getNumDocs(), b.getNumDocs());
    ASSERT_TRUE(a.getNumDocs() == b.getNumDocs());
    uint32_t asz(a.getMaxValueCount());
    uint32_t bsz(b.getMaxValueCount());
    auto *av = new BufferType[asz];
    auto *bv = new BufferType[bsz];

    for (size_t i(0), m(a.getNumDocs()); i < m; i++) {
        ASSERT_TRUE(asz >= static_cast<uint32_t>(a.getValueCount(i)));
        ASSERT_TRUE(bsz >= static_cast<uint32_t>(b.getValueCount(i)));
        EXPECT_EQ(a.getValueCount(i), b.getValueCount(i));
        ASSERT_TRUE(a.getValueCount(i) == b.getValueCount(i));
        EXPECT_EQ(static_cast<const AttributeVector &>(a).get(i, av, asz), static_cast<uint32_t>(a.getValueCount(i)));
        EXPECT_EQ(static_cast<const AttributeVector &>(b).get(i, bv, bsz), static_cast<uint32_t>(b.getValueCount(i)));
        const size_t min_common_value_count = std::min(a.getValueCount(i), b.getValueCount(i));
        if (a.hasWeightedSetType()) {
            ASSERT_TRUE(b.hasWeightedSetType());
            std::sort(av, av + min_common_value_count, order_by_value());
            std::sort(bv, bv + min_common_value_count, order_by_value());
        }
        for(size_t j = 0; j < min_common_value_count; j++) {
            EXPECT_EQ(av[j], bv[j]);
        }
    }
    delete [] bv;
    delete [] av;
}

void AttributeTest::testReloadInt(const AttributePtr & a, size_t numDocs)
{
    addDocs(a, numDocs);
    populate(static_cast<IntegerAttribute &>(*a.get()), 17);
    if (a->hasWeightedSetType()) {
        testReload<IntegerAttribute, IntegerAttribute::WeightedInt>(a);
    } else {
        testReload<IntegerAttribute, IntegerAttribute::largeint_t>(a);
    }
}


void AttributeTest::testReloadString(const AttributePtr & a, size_t numDocs)
{
    addDocs(a, numDocs);
    populate(static_cast<StringAttribute &>(*a.get()), 17);
    if (a->hasWeightedSetType()) {
        testReload<StringAttribute, StringAttribute::WeightedString>(a);
    } else {
        testReload<StringAttribute, string>(a);
    }
}

template <typename VectorType, typename BufferType>
void AttributeTest::testReload(const AttributePtr & a)
{
    LOG(info, "testReload: vector '%s'", a->getName().c_str());

    auto b = createAttribute(replace_suffix(*a, "2"), a->getConfig());
    auto c = createAttribute(replace_suffix(*a, "3"), a->getConfig());

    a->setCreateSerialNum(43u);
    EXPECT_TRUE( a->save(b->getBaseFileName()) );
    a->commit(true);
    if (preciseEstimatedSize(*a)) {
        EXPECT_EQ(statSize(*b), a->getEstimatedSaveByteSize());
    } else {
        double estSize = a->getEstimatedSaveByteSize();
        double actSize = statSize(*b);
        EXPECT_LE(actSize * 1.0, estSize * 1.3);
        EXPECT_GE(actSize * 1.0, estSize * 0.7);
    }
    EXPECT_TRUE( a->save(c->getBaseFileName()) );
    if (preciseEstimatedSize(*a)) {
        EXPECT_EQ(statSize(*c), a->getEstimatedSaveByteSize());
    }
    EXPECT_TRUE( b->load() );
    EXPECT_EQ(43u, b->getCreateSerialNum());
    compare<VectorType, BufferType>
        (*(static_cast<VectorType *>(a.get())), *(static_cast<VectorType *>(b.get())));
    EXPECT_TRUE( c->load() );
    compare<VectorType, BufferType>
        (*(static_cast<VectorType *>(a.get())), *(static_cast<VectorType *>(c.get())));

    if (isUnsignedSmallIntAttribute(*a)) {
        return;
    }
    populate(static_cast<VectorType &>(*b.get()), 700);
    populate(static_cast<VectorType &>(*c.get()), 700);
    compare<VectorType, BufferType>
        (*(static_cast<VectorType *>(b.get())), *(static_cast<VectorType *>(c.get())));
}


void AttributeTest::testReload()
{
    // IntegerAttribute
    // CollectionType::SINGLE
    {
        AttributePtr iv1 = createAttribute("sint32_1", Config(BasicType::INT32, CollectionType::SINGLE));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("suint4_1", Config(BasicType::UINT4, CollectionType::SINGLE));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("suint2_1", Config(BasicType::UINT2, CollectionType::SINGLE));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("suint1_1", Config(BasicType::BOOL, CollectionType::SINGLE));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("sfsint32_1", cfg);
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    // CollectionType::ARRAY
    {
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("flag_1", cfg);
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("aint32_1", Config(BasicType::INT32, CollectionType::ARRAY));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("afsint32_1", cfg);
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    // CollectionType::WSET
    {
        AttributePtr iv1 = createAttribute("wint32_1", Config(BasicType::INT32, CollectionType::WSET));
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::WSET);
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("wfsint32_1", cfg);
        testReloadInt(iv1, 0);
        testReloadInt(iv1, 100);
    }


    // StringAttribute
    {
        AttributePtr iv1 = createAttribute("sstring_1", Config(BasicType::STRING, CollectionType::SINGLE));
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("astring_1", Config(BasicType::STRING, CollectionType::ARRAY));
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("wstring_1", Config(BasicType::STRING, CollectionType::WSET));
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
    {
        Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("sfsstring_1", cfg);
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
    {
        Config cfg(Config(BasicType::STRING, CollectionType::ARRAY));
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("afsstring_1", cfg);
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
    {
        Config cfg(Config(BasicType::STRING, CollectionType::WSET));
        cfg.setFastSearch(true);
        AttributePtr iv1 = createAttribute("wsfsstring_1", cfg);
        testReloadString(iv1, 0);
        testReloadString(iv1, 100);
    }
}

void AttributeTest::testHasLoadData()
{
    { // single value
        AttributePtr av = createAttribute("loaddata1", Config(BasicType::INT32));
        EXPECT_TRUE(!av->hasLoadData());
        av->save();
        EXPECT_TRUE(av->hasLoadData());
        av->save(baseFileName("loaddata2"));
        av = createAttribute("loaddata2", Config(BasicType::INT32));
        EXPECT_TRUE(av->hasLoadData());
        av->save(baseFileName("loaddata3"));
    }
    { // array
        AttributePtr av = createAttribute("loaddata3", Config(BasicType::INT32, CollectionType::ARRAY));
        EXPECT_TRUE(!av->hasLoadData());
        av->save();
        EXPECT_TRUE(av->hasLoadData());
        av->save(baseFileName("loaddata4"));
        av = createAttribute("loaddata4", Config(BasicType::INT32, CollectionType::ARRAY));
        EXPECT_TRUE(av->hasLoadData());
        av->save(baseFileName("loaddata5"));
    }
    { // wset
        AttributePtr av = createAttribute("loaddata5", Config(BasicType::INT32, CollectionType::WSET));
        EXPECT_TRUE(!av->hasLoadData());
        av->save();
        EXPECT_TRUE(av->hasLoadData());
        av->save(baseFileName("loaddata6"));
        av = createAttribute("loaddata6", Config(BasicType::INT32, CollectionType::WSET));
        EXPECT_TRUE(av->hasLoadData());
    }
}

void
AttributeTest::testMemorySaverInt(const AttributePtr & a, size_t numDocs)
{
    addDocs(a, numDocs);
    populate(static_cast<IntegerAttribute &>(*a.get()), 21);
    if (a->hasWeightedSetType()) {
        testMemorySaver<IntegerAttribute, IntegerAttribute::WeightedInt>(a);
    } else {
        testMemorySaver<IntegerAttribute, IntegerAttribute::largeint_t>(a);
    }
}

void
AttributeTest::testMemorySaverString(const AttributePtr & a, size_t numDocs)
{
    addDocs(a, numDocs);
    populate(static_cast<StringAttribute &>(*a.get()), 21);
    if (a->hasWeightedSetType()) {
        testMemorySaver<StringAttribute, StringAttribute::WeightedString>(a);
    } else {
        testMemorySaver<StringAttribute, string>(a);
    }
}

template <typename VectorType, typename BufferType>
void
AttributeTest::testMemorySaver(const AttributePtr & a)
{
    LOG(info, "testMemorySaver: vector '%s'", a->getName().c_str());

    auto b = createAttribute(replace_suffix(*a, "2ms"), a->getConfig());
    AttributeMemorySaveTarget saveTarget;
    EXPECT_TRUE(a->save(saveTarget, b->getBaseFileName()));
    FastOS_StatInfo statInfo;
    vespalib::string datFile = vespalib::make_string("%s.dat", b->getBaseFileName().c_str());
    EXPECT_TRUE(!FastOS_File::Stat(datFile.c_str(), &statInfo));
    EXPECT_TRUE(saveTarget.writeToFile(TuneFileAttributes(),
                                       DummyFileHeaderContext()));
    EXPECT_TRUE(FastOS_File::Stat(datFile.c_str(), &statInfo));
    EXPECT_TRUE(b->load());
    compare<VectorType, BufferType>
        (*(static_cast<VectorType *>(a.get())), *(static_cast<VectorType *>(b.get())));
}

void
AttributeTest::testMemorySaver()
{
    // CollectionType::SINGLE
    {
        AttributePtr iv1 = createAttribute("sint32_1ms", Config(BasicType::INT32, CollectionType::SINGLE));
        testMemorySaverInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("suint4_1ms", Config(BasicType::UINT4, CollectionType::SINGLE));
        testMemorySaverInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("sstr_1ms", Config(BasicType::STRING, CollectionType::SINGLE));
        testMemorySaverString(iv1, 100);
    }
    // CollectionType::ARRAY
    {
        AttributePtr iv1 = createAttribute("aint32_1ms", Config(BasicType::INT32, CollectionType::ARRAY));
        testMemorySaverInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("astr_1ms", Config(BasicType::STRING, CollectionType::ARRAY));
        testMemorySaverString(iv1, 100);
    }
    // CollectionType::WSET
    {
        AttributePtr iv1 = createAttribute("wint32_1ms", Config(BasicType::INT32, CollectionType::WSET));
        testMemorySaverInt(iv1, 100);
    }
    {
        AttributePtr iv1 = createAttribute("wstr_1ms", Config(BasicType::STRING, CollectionType::WSET));
        testMemorySaverString(iv1, 100);
    }
}

template <typename T>
void
AttributeTest::fillNumeric(std::vector<T> & values, uint32_t numValues)
{
    values.clear();
    values.reserve(numValues);
    for (uint32_t i = 0; i < numValues; ++i) {
        values.push_back(static_cast<T>(i));
    }
}

void
AttributeTest::fillString(std::vector<string> & values, uint32_t numValues)
{
    values.clear();
    values.reserve(numValues);
    for (uint32_t i = 0; i < numValues; ++i) {
        vespalib::asciistream ss;
        ss << "string" << (i < 10 ? "0" : "") << i;
        values.emplace_back(ss.str());
    }
}

template <typename VectorType, typename BufferType>
bool
AttributeTest::appendToVector(VectorType & v, uint32_t doc, uint32_t valueCount,
                              const std::vector<BufferType> & values)
{
    bool retval = true;
    for (uint32_t i = 0; i < valueCount; ++i) {
        EXPECT_TRUE((retval = retval && v.append(doc, values[i], 1)));
    }
    return retval;
}

template <typename BufferType>
bool
AttributeTest::checkCount(const AttributePtr & ptr, uint32_t doc, uint32_t valueCount,
                          uint32_t numValues, const BufferType & value)
{
    std::vector<BufferType> buffer(valueCount);
    bool result = true;
    EXPECT_EQ(valueCount, ptr->getValueCount(doc)) << (result = false, "");
    if (!result) {
        return false;
    }
    EXPECT_EQ(valueCount, ptr->get(doc, buffer.data(), buffer.size())) << (result = false, "");
    if (!result) {
        return false;
    }
    EXPECT_EQ(numValues, static_cast<uint32_t>(std::count(buffer.begin(), buffer.end(), value))) << (result = false, "");
    return result;
}

template <typename BufferType>
bool
AttributeTest::checkContent(const AttributePtr & ptr, uint32_t doc, uint32_t valueCount,
                            uint32_t range, const std::vector<BufferType> & values)
{
    std::vector<BufferType> buffer(valueCount);
    bool retval = true;
    EXPECT_TRUE((retval = retval && (static_cast<uint32_t>(ptr->getValueCount(doc)) == valueCount)));
    EXPECT_TRUE((retval = retval && (ptr->get(doc, buffer.data(), buffer.size()) == valueCount)));
    for (uint32_t i = 0; i < valueCount; ++i) {
        EXPECT_TRUE((retval = retval && (buffer[i] == values[i % range])));
    }
    return retval;
}


//-----------------------------------------------------------------------------
// CollectionType::SINGLE
//-----------------------------------------------------------------------------

template <typename VectorType, typename BufferType, typename BaseType>
void
AttributeTest::testSingle(const AttributePtr & ptr, const std::vector<BufferType> & values)
{
    LOG(info, "testSingle: vector '%s' with %u documents and %lu values",
        ptr->getName().c_str(), ptr->getNumDocs(), values.size());

    VectorType & v = *(static_cast<VectorType *>(ptr.get()));
    uint32_t numUniques = values.size();
    std::vector<BufferType> buffer(1);

    // test update()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        EXPECT_TRUE(ptr->getValueCount(doc) == 1);
        uint32_t i = doc % numUniques;
        uint32_t j = (doc + 1) % numUniques;

        EXPECT_TRUE(v.update(doc, values[i]));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 1, 1, values[i]));

        EXPECT_TRUE(v.update(doc, values[j]));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 1, 1, values[j]));
    }
    EXPECT_TRUE(!v.update(ptr->getNumDocs(), values[0]));

    // test append()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        EXPECT_TRUE(!v.append(doc, values[0], 1));
    }
    EXPECT_TRUE(!v.append(ptr->getNumDocs(), values[0], 1));

    // test remove()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        EXPECT_TRUE(!v.remove(doc, values[0], 1));
    }
    EXPECT_TRUE(!v.remove(ptr->getNumDocs(), values[0], 1));

    bool smallUInt = isUnsignedSmallIntAttribute(*ptr);
    // test clearDoc()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        uint32_t i = (doc + 2) % numUniques;

        EXPECT_TRUE(v.update(doc, values[i]));
        if (doc % 2 == 0) { // alternate clearing
            ptr->clearDoc(doc);
        }
        ptr->commit();
        EXPECT_EQ(1u, ptr->get(doc, buffer.data(), buffer.size()));
        if (doc % 2 == 0) {
            if (smallUInt) {
                expectZero(buffer[0]);
            } else {
                EXPECT_TRUE(attribute::isUndefined<BaseType>(buffer[0]));
            }
        } else {
            EXPECT_TRUE(!attribute::isUndefined<BaseType>(buffer[0]));
            EXPECT_EQ(values[i], buffer[0]);
        }
    }
    EXPECT_TRUE(!v.clearDoc(ptr->getNumDocs()));
}

void
AttributeTest::testSingle()
{
    uint32_t numDocs = 1000;
    uint32_t numUniques = 50;
    uint32_t numUniqueNibbles = 9;
    {
        std::vector<AttributeVector::largeint_t> values;
        fillNumeric(values, numUniques);
        std::vector<AttributeVector::largeint_t> nibbleValues;
        fillNumeric(nibbleValues, numUniqueNibbles);
        {
            AttributePtr ptr = createAttribute("sv-int32", Config(BasicType::INT32, CollectionType::SINGLE));
            addDocs(ptr, numDocs);
            testSingle<IntegerAttribute, AttributeVector::largeint_t, int32_t>(ptr, values);
        }
        {
            AttributePtr ptr = createAttribute("sv-uint4", Config(BasicType::UINT4, CollectionType::SINGLE));
            addDocs(ptr, numDocs);
            testSingle<IntegerAttribute, AttributeVector::largeint_t, int8_t>(ptr, nibbleValues);
        }
        {
            Config cfg(BasicType::INT32, CollectionType::SINGLE);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("sv-post-int32", cfg);
            addDocs(ptr, numDocs);
            testSingle<IntegerAttribute, AttributeVector::largeint_t, int32_t>(ptr, values);
        }
    }
    {
        std::vector<double> values;
        fillNumeric(values, numUniques);
        {
            AttributePtr ptr = createAttribute("sv-float", Config(BasicType::FLOAT, CollectionType::SINGLE));
            addDocs(ptr, numDocs);
            testSingle<FloatingPointAttribute, double, float>(ptr, values);
        }
        {
            Config cfg(BasicType::FLOAT, CollectionType::SINGLE);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("sv-post-float", cfg);
            addDocs(ptr, numDocs);
            testSingle<FloatingPointAttribute, double, float>(ptr, values);
        }

    }
    {
        std::vector<string> values;
        fillString(values, numUniques);
        {
            AttributePtr ptr = createAttribute("sv-string", Config(BasicType::STRING, CollectionType::SINGLE));
            addDocs(ptr, numDocs);
            testSingle<StringAttribute, string, string>(ptr, values);
        }
        {
            Config cfg(Config(BasicType::STRING, CollectionType::SINGLE));
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("sv-fs-string", cfg);
            addDocs(ptr, numDocs);
            testSingle<StringAttribute, string, string>(ptr, values);
        }
    }
}


//-----------------------------------------------------------------------------
// CollectionType::ARRAY
//-----------------------------------------------------------------------------

template <typename VectorType, typename BufferType>
void
AttributeTest::testArray(const AttributePtr & ptr, const std::vector<BufferType> & values)
{
    LOG(info, "testArray: vector '%s' with %i documents and %lu values",
        ptr->getName().c_str(), ptr->getNumDocs(), values.size());

    VectorType & v = *(static_cast<VectorType *>(ptr.get()));
    uint32_t numUniques = values.size();
    ASSERT_TRUE(numUniques >= 6);


    // test update()
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 0u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);
    size_t sumAppends(0);
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        uint32_t valueCount = doc % numUniques;
        ptr->clearDoc(doc);

        EXPECT_TRUE(appendToVector(v, doc, valueCount, values));
        ptr->commit();
        sumAppends += valueCount;

        uint32_t i = doc % numUniques;
        EXPECT_TRUE(v.update(doc, values[i]));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 1, 1, values[i]));
    }
    EXPECT_TRUE(!v.update(ptr->getNumDocs(), values[0]));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), (1 + 2)*ptr->getNumDocs() + sumAppends);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), sumAppends);


    // test append()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        uint32_t valueCount = doc % numUniques;
        ptr->clearDoc(doc);

        // append unique values
        EXPECT_TRUE(appendToVector(v, doc, valueCount, values));
        ptr->commit();
        EXPECT_TRUE(checkContent(ptr, doc, valueCount, valueCount, values));

        // append duplicates
        EXPECT_TRUE(appendToVector(v, doc, valueCount, values));
        ptr->commit();
        EXPECT_TRUE(checkContent(ptr, doc, valueCount * 2, valueCount, values));
    }
    EXPECT_TRUE(!v.append(ptr->getNumDocs(), values[0], 1));


    // test remove()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        ptr->clearDoc(doc);

        EXPECT_TRUE(v.append(doc, values[1], 1));
        for (uint32_t i = 0; i < 3; ++i) {
            EXPECT_TRUE(v.append(doc, values[3], 1));
        }
        for (uint32_t i = 0; i < 5; ++i) {
            EXPECT_TRUE(v.append(doc, values[5], 1));
        }

        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 9, 1, values[1]));
        EXPECT_TRUE(checkCount(ptr, doc, 9, 3, values[3]));
        EXPECT_TRUE(checkCount(ptr, doc, 9, 5, values[5]));

        EXPECT_TRUE(v.remove(doc, values[0], 1));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 9, 1, values[1]));
        EXPECT_TRUE(checkCount(ptr, doc, 9, 3, values[3]));
        EXPECT_TRUE(checkCount(ptr, doc, 9, 5, values[5]));

        EXPECT_TRUE(v.remove(doc, values[1], 1));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 8, 0, values[1]));
        EXPECT_TRUE(checkCount(ptr, doc, 8, 3, values[3]));
        EXPECT_TRUE(checkCount(ptr, doc, 8, 5, values[5]));

        EXPECT_TRUE(v.remove(doc, values[5], 1));
        ptr->commit();
        EXPECT_TRUE(checkCount(ptr, doc, 3, 0, values[1]));
        EXPECT_TRUE(checkCount(ptr, doc, 3, 3, values[3]));
        EXPECT_TRUE(checkCount(ptr, doc, 3, 0, values[5]));
    }
    EXPECT_TRUE(!v.remove(ptr->getNumDocs(), values[0], 1));

    // test clearDoc()
    for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
        uint32_t valueCount = doc % numUniques;

        ptr->clearDoc(doc);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(v.append(doc, values[0], 1));
        }
        ptr->clearDoc(doc);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(v.append(doc, values[1], 1));
        }
        ptr->commit();

        EXPECT_TRUE(checkCount(ptr, doc, valueCount, valueCount, values[1]));
    }
    EXPECT_TRUE(!v.clearDoc(ptr->getNumDocs()));
}

void
AttributeTest::testArray()
{
    uint32_t numDocs = 100;
    uint32_t numUniques = 50;
    { // IntegerAttribute
        std::vector<AttributeVector::largeint_t> values;
        fillNumeric(values, numUniques);
        {
            AttributePtr ptr = createAttribute("a-int32", Config(BasicType::INT32, CollectionType::ARRAY));
            addDocs(ptr, numDocs);
            testArray<IntegerAttribute, AttributeVector::largeint_t>(ptr, values);
        }
        {
            Config cfg(BasicType::INT8, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("flags", cfg);
            addDocs(ptr, numDocs);
            testArray<IntegerAttribute, AttributeVector::largeint_t>(ptr, values);
        }
        {
            Config cfg(BasicType::INT32, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("a-fs-int32", cfg);
            addDocs(ptr, numDocs);
            testArray<IntegerAttribute, AttributeVector::largeint_t>(ptr, values);
        }
    }
    { // FloatingPointAttribute
        std::vector<double> values;
        fillNumeric(values, numUniques);
        {
            AttributePtr ptr = createAttribute("a-float", Config(BasicType::FLOAT, CollectionType::ARRAY));
            addDocs(ptr, numDocs);
            testArray<FloatingPointAttribute, double>(ptr, values);
        }
        {
            Config cfg(BasicType::FLOAT, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("a-fs-float", cfg);
            addDocs(ptr, numDocs);
            testArray<FloatingPointAttribute, double>(ptr, values);
        }
    }
    { // StringAttribute
        std::vector<string> values;
        fillString(values, numUniques);
        {
            AttributePtr ptr = createAttribute("a-string", Config(BasicType::STRING, CollectionType::ARRAY));
            addDocs(ptr, numDocs);
            testArray<StringAttribute, string>(ptr, values);
        }
        {
            Config cfg(BasicType::STRING, CollectionType::ARRAY);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("afs-string", cfg);
            addDocs(ptr, numDocs);
            testArray<StringAttribute, string>(ptr, values);
        }
    }
}


//-----------------------------------------------------------------------------
// CollectionType::WSET
//-----------------------------------------------------------------------------

// This function makes the assumption that weights are unique, so that is has a way
// of creating a deterministic comparison ordering of weighted sets without caring about
// the templated values themselvecs.
template <typename VectorType, typename BufferType>
void
AttributeTest::testWeightedSet(const AttributePtr & ptr, const std::vector<BufferType> & values)
{
    LOG(info, "testWeightedSet: vector '%s' with %u documents and %lu values",
        ptr->getName().c_str(), ptr->getNumDocs(),values.size());

    VectorType & v = *(static_cast<VectorType *>(ptr.get()));
    uint32_t numDocs = v.getNumDocs();
    ASSERT_TRUE(values.size() >= numDocs + 10);
    uint32_t bufferSize = numDocs + 10;
    std::vector<BufferType> buffer(bufferSize);

    std::vector<BufferType> ordered_values(values.begin(), values.end());
    std::sort(ordered_values.begin(), ordered_values.end(), order_by_weight());

    // fill and check
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 0u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = doc;
        v.clearDoc(doc);
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(v.append(doc, values[j].getValue(), values[j].getWeight()));
        }
        commit(ptr);
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount);
        std::sort(buffer.begin(), buffer.begin() + valueCount, order_by_weight());
        for (uint32_t j = 0; j < valueCount; ++j) {
            EXPECT_TRUE(buffer[j].getValue() == ordered_values[j].getValue());
            EXPECT_TRUE(buffer[j].getWeight() == ordered_values[j].getWeight());
        }
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), numDocs + (numDocs*(numDocs-1))/2);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);

    // test append()
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = doc;

        // append non-existent value
        EXPECT_TRUE(v.append(doc, values[doc].getValue(), values[doc].getWeight()));
        commit(ptr);
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount + 1);
        EXPECT_TRUE(contains(buffer, valueCount + 1, values[doc]));

        // append existent value
        EXPECT_TRUE(v.append(doc, values[doc].getValue(), values[doc].getWeight() + 10));
        commit(ptr);
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount + 1);
        EXPECT_TRUE(contains(buffer, valueCount + 1, BufferType(values[doc].getValue(), values[doc].getWeight() + 10)));

        // append non-existent value two times
        EXPECT_TRUE(v.append(doc, values[doc + 1].getValue(), values[doc + 1].getWeight()));
        EXPECT_TRUE(v.append(doc, values[doc + 1].getValue(), values[doc + 1].getWeight() + 10));
        commit(ptr);
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount + 2);
        EXPECT_TRUE(contains(buffer, valueCount + 2, BufferType(values[doc + 1].getValue(), values[doc + 1].getWeight() + 10)));
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), numDocs + (numDocs*(numDocs-1))/2 + numDocs*4);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);

    // test remove()
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t valueCount = doc;

        // remove non-existent value
        EXPECT_TRUE(static_cast<uint32_t>(v.getValueCount(doc)) == valueCount + 2);
        EXPECT_TRUE(v.remove(doc, values[doc + 2].getValue(), 0));
        commit(ptr);
        EXPECT_TRUE(static_cast<uint32_t>(v.getValueCount(doc)) == valueCount + 2);

        // remove existent value
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount + 2);
        EXPECT_TRUE(contains_value(buffer, valueCount + 2, values[doc + 1].getValue()));
        EXPECT_TRUE(v.remove(doc, values[doc + 1].getValue(), 0));
        commit(ptr);
        ASSERT_TRUE(ptr->get(doc, buffer.data(), buffer.size()) == valueCount + 1);
        EXPECT_FALSE(contains_value(buffer, valueCount + 1, values[doc + 1].getValue()));
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), numDocs + (numDocs*(numDocs-1))/2 + numDocs*4 + numDocs * 2);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);
}

void
AttributeTest::testWeightedSet()
{
    uint32_t numDocs = 100;
    uint32_t numValues = numDocs + 10;
    { // IntegerAttribute
        std::vector<AttributeVector::WeightedInt> values;
        values.reserve(numValues);
        for (uint32_t i = 0; i < numValues; ++i) {
            values.emplace_back(i, i + numValues);
        }

        {
            AttributePtr ptr = createAttribute("wsint32", Config(BasicType::INT32, CollectionType::WSET));
            addDocs(ptr, numDocs);
            testWeightedSet<IntegerAttribute, AttributeVector::WeightedInt>(ptr, values);
        }
        {
            Config cfg(BasicType::INT32, CollectionType::WSET);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("ws-fs-int32", cfg);
            addDocs(ptr, numDocs);
            testWeightedSet<IntegerAttribute, AttributeVector::WeightedInt>(ptr, values);
            IAttributeVector::EnumHandle e;
            EXPECT_TRUE(ptr->findEnum("1", e));
            EXPECT_EQ(1u, ptr->findFoldedEnums("1").size());
            EXPECT_EQ(e, ptr->findFoldedEnums("1")[0]);

        }
    }
    { // FloatingPointAttribute
        std::vector<AttributeVector::WeightedFloat> values;
        values.reserve(numValues);
        for (uint32_t i = 0; i < numValues; ++i) {
            values.emplace_back(i, i + numValues);
        }

        {
            Config cfg(BasicType::FLOAT, CollectionType::WSET);
            AttributePtr ptr = createAttribute("ws-float", cfg);
            addDocs(ptr, numDocs);
            testWeightedSet<FloatingPointAttribute, AttributeVector::WeightedFloat>(ptr, values);
        }
        {
            Config cfg(BasicType::FLOAT, CollectionType::WSET);
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("ws-fs-float", cfg);
            addDocs(ptr, numDocs);
            testWeightedSet<FloatingPointAttribute, AttributeVector::WeightedFloat>(ptr, values);
            IAttributeVector::EnumHandle e;
            EXPECT_TRUE(ptr->findEnum("1", e));
            EXPECT_EQ(1u, ptr->findFoldedEnums("1").size());
            EXPECT_EQ(e, ptr->findFoldedEnums("1")[0]);
        }
    }
    { // StringAttribute
        std::vector<AttributeVector::WeightedString> values;
        values.reserve(numValues);
        for (uint32_t i = 0; i < numValues; ++i) {
            vespalib::asciistream ss;
            ss << "string" << (i < 10 ? "0" : "") << i;
            values.emplace_back(ss.str(), i + numValues);
        }

        {
            AttributePtr ptr = createAttribute("wsstr", Config(BasicType::STRING, CollectionType::WSET));
            addDocs(ptr, numDocs);
            testWeightedSet<StringAttribute, AttributeVector::WeightedString>(ptr, values);
        }
        {
            Config cfg(Config(BasicType::STRING, CollectionType::WSET));
            cfg.setFastSearch(true);
            AttributePtr ptr = createAttribute("wsfsstr", cfg);
            addDocs(ptr, numDocs);
            testWeightedSet<StringAttribute, AttributeVector::WeightedString>(ptr, values);
            IAttributeVector::EnumHandle e;
            EXPECT_TRUE(ptr->findEnum("string00", e));
            EXPECT_EQ(1u, ptr->findFoldedEnums("StRiNg00").size());
            EXPECT_EQ(e, ptr->findFoldedEnums("StRiNg00")[0]);
        }
    }
}

template <typename VectorType, typename BufferType>
void
AttributeTest::testArithmeticValueUpdate(const AttributePtr & ptr)
{
    LOG(info, "testArithmeticValueUpdate: vector '%s'", ptr->getName().c_str());

    typedef document::ArithmeticValueUpdate Arith;
    auto & vec = static_cast<VectorType &>(*ptr.get());
    addDocs(ptr, 13);
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 0u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);
    for (uint32_t doc = 0; doc < 13; ++doc) {
        ASSERT_TRUE(vec.update(doc, 100));
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 13u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);
    ptr->commit();

    EXPECT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
    EXPECT_TRUE(vec.apply(1, Arith(Arith::Add, -10)));
    EXPECT_TRUE(vec.apply(2, Arith(Arith::Sub, 10)));
    EXPECT_TRUE(vec.apply(3, Arith(Arith::Sub, -10)));
    EXPECT_TRUE(vec.apply(4, Arith(Arith::Mul, 10)));
    EXPECT_TRUE(vec.apply(5, Arith(Arith::Mul, -10)));
    EXPECT_TRUE(vec.apply(6, Arith(Arith::Div, 10)));
    EXPECT_TRUE(vec.apply(7, Arith(Arith::Div, -10)));
    EXPECT_TRUE(vec.apply(8, Arith(Arith::Add, 10.5)));
    EXPECT_TRUE(vec.apply(9, Arith(Arith::Sub, 10.5)));
    EXPECT_TRUE(vec.apply(10, Arith(Arith::Mul, 1.2)));
    EXPECT_TRUE(vec.apply(11, Arith(Arith::Mul, 0.8)));
    EXPECT_TRUE(vec.apply(12, Arith(Arith::Div, 0.8)));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 26u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 13u);
    ptr->commit();

    std::vector<BufferType> buf(1);
    ptr->get(0, &buf[0], 1);
    EXPECT_EQ(buf[0], 110);
    ptr->get(1, &buf[0], 1);
    EXPECT_EQ(buf[0], 90);
    ptr->get(2, &buf[0], 1);
    EXPECT_EQ(buf[0], 90);
    ptr->get(3, &buf[0], 1);
    EXPECT_EQ(buf[0], 110);
    ptr->get(4, &buf[0], 1);
    EXPECT_EQ(buf[0], 1000);
    ptr->get(5, &buf[0], 1);
    EXPECT_EQ(buf[0], -1000);
    ptr->get(6, &buf[0], 1);
    EXPECT_EQ(buf[0], 10);
    ptr->get(7, &buf[0], 1);
    EXPECT_EQ(buf[0], -10);
    if (ptr->getBasicType() == BasicType::INT32) {
        ptr->get(8, &buf[0], 1);
        EXPECT_EQ(buf[0], 110);
        ptr->get(9, &buf[0], 1);
        EXPECT_EQ(buf[0], 90);
    } else if (ptr->getBasicType() == BasicType::FLOAT ||
               ptr->getBasicType() == BasicType::DOUBLE)
    {
        ptr->get(8, &buf[0], 1);
        EXPECT_EQ(buf[0], 110.5);
        ptr->get(9, &buf[0], 1);
        EXPECT_EQ(buf[0], 89.5);
    } else {
        ASSERT_TRUE(false);
    }
    ptr->get(10, &buf[0], 1);
    EXPECT_EQ(buf[0], 120);
    ptr->get(11, &buf[0], 1);
    EXPECT_EQ(buf[0], 80);
    ptr->get(12, &buf[0], 1);
    EXPECT_EQ(buf[0], 125);


    // try several arithmetic operations on the same document in a single commit
    ASSERT_TRUE(vec.update(0, 1100));
    ASSERT_TRUE(vec.update(1, 1100));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 28u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 13u);
    for (uint32_t i = 0; i < 10; ++i) {
        ASSERT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
        ASSERT_TRUE(vec.apply(1, Arith(Arith::Add, 10)));
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 48u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 33u);
    ptr->commit();
    ptr->get(0, &buf[0], 1);
    EXPECT_EQ(buf[0], 1200);
    ptr->get(1, &buf[0], 1);
    EXPECT_EQ(buf[0], 1200);

    ASSERT_TRUE(vec.update(0, 10));
    ASSERT_TRUE(vec.update(1, 10));
    ASSERT_TRUE(vec.update(2, 10));
    ASSERT_TRUE(vec.update(3, 10));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 52u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 33u);
    for (uint32_t i = 0; i < 8; ++i) {
        EXPECT_TRUE(vec.apply(0, Arith(Arith::Mul, 1.2)));
        EXPECT_TRUE(vec.apply(1, Arith(Arith::Mul, 2.3)));
        EXPECT_TRUE(vec.apply(2, Arith(Arith::Mul, 3.4)));
        EXPECT_TRUE(vec.apply(3, Arith(Arith::Mul, 5.6)));
        ptr->commit();
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 84u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 65u);


    // try divide by zero
    ASSERT_TRUE(vec.update(0, 100));
    EXPECT_TRUE(vec.apply(0, Arith(Arith::Div, 0)));
    ptr->commit();
    if (ptr->isFloatingPointType()) {
        EXPECT_EQ(ptr->getStatus().getUpdateCount(), 86u);
        EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 66u);
    } else { // does not apply for interger attributes
        EXPECT_EQ(ptr->getStatus().getUpdateCount(), 85u);
        EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 65u);
    }
    ptr->get(0, &buf[0], 1);
    if (ptr->getBasicType() == BasicType::INT32) {
        EXPECT_EQ(buf[0], 100);
    }

    // try divide by zero with empty change vector
    EXPECT_TRUE(vec.apply(0, Arith(Arith::Div, 0)));
    ptr->commit();
    if (ptr->isFloatingPointType()) {
        EXPECT_EQ(ptr->getStatus().getUpdateCount(), 87u);
        EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 67u);
    } else { // does not apply for interger attributes
        EXPECT_EQ(ptr->getStatus().getUpdateCount(), 85u);
        EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 65u);
    }
}

void
AttributeTest::testArithmeticValueUpdate()
{
    {
        AttributePtr ptr = createAttribute("sint32", Config(BasicType::INT32, CollectionType::SINGLE));
        testArithmeticValueUpdate<IntegerAttribute, IntegerAttribute::largeint_t>(ptr);
    }
    {
        AttributePtr ptr = createAttribute("sfloat", Config(BasicType::FLOAT, CollectionType::SINGLE));
        testArithmeticValueUpdate<FloatingPointAttribute, double>(ptr);
    }
    {
        Config cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("sfsint32", cfg);
        testArithmeticValueUpdate<IntegerAttribute, IntegerAttribute::largeint_t>(ptr);
    }
    {
        Config cfg(BasicType::FLOAT, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("sfsfloat", cfg);
        testArithmeticValueUpdate<FloatingPointAttribute, double>(ptr);
    }
    {
        Config cfg(BasicType::DOUBLE, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("sfsdouble", cfg);
        testArithmeticValueUpdate<FloatingPointAttribute, double>(ptr);
    }
}


template <typename VectorType, typename BaseType, typename BufferType>
void
AttributeTest::testArithmeticWithUndefinedValue(const AttributePtr & ptr, BaseType before, BaseType after)
{
    LOG(info, "testArithmeticWithUndefinedValue: vector '%s'", ptr->getName().c_str());

    typedef document::ArithmeticValueUpdate Arith;
    auto & vec = static_cast<VectorType &>(*ptr.get());
    addDocs(ptr, 1);
    ASSERT_TRUE(vec.update(0, before));
    ptr->commit();

    EXPECT_TRUE(vec.apply(0, Arith(Arith::Add, 10)));
    ptr->commit();

    std::vector<BufferType> buf(1);
    ptr->get(0, &buf[0], 1);

    if (ptr->isFloatingPointType()) {
        EXPECT_TRUE(std::isnan(buf[0]));
    } else {
        EXPECT_EQ(buf[0], after);
    }
}

void
AttributeTest::testArithmeticWithUndefinedValue()
{
    {
        AttributePtr ptr = createAttribute("sint32", Config(BasicType::INT32, CollectionType::SINGLE));
        testArithmeticWithUndefinedValue<IntegerAttribute, int32_t, IntegerAttribute::largeint_t>
            (ptr, std::numeric_limits<int32_t>::min(), std::numeric_limits<int32_t>::min());
    }
    {
        AttributePtr ptr = createAttribute("sfloat", Config(BasicType::FLOAT, CollectionType::SINGLE));
        testArithmeticWithUndefinedValue<FloatingPointAttribute, float, double>
            (ptr, std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::quiet_NaN());
    }
    {
        AttributePtr ptr = createAttribute("sdouble", Config(BasicType::DOUBLE, CollectionType::SINGLE));
        testArithmeticWithUndefinedValue<FloatingPointAttribute, double, double>
            (ptr, std::numeric_limits<double>::quiet_NaN(), std::numeric_limits<double>::quiet_NaN());
    }
}


template <typename VectorType, typename BufferType>
void
AttributeTest::testMapValueUpdate(const AttributePtr & ptr, BufferType initValue,
                                  const FieldValue & initFieldValue, const FieldValue & nonExistant,
                                  bool removeIfZero, bool createIfNonExistant)
{
    LOG(info, "testMapValueUpdate: vector '%s'", ptr->getName().c_str());
    typedef MapValueUpdate MapVU;
    typedef ArithmeticValueUpdate ArithVU;
    auto & vec = static_cast<VectorType &>(*ptr.get());

    addDocs(ptr, 7);
    for (uint32_t doc = 0; doc < 7; ++doc) {
        ASSERT_TRUE(vec.append(doc, initValue.getValue(), 100));
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 7u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 0u);

    EXPECT_TRUE(ptr->apply(0, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Add, 10))));
    EXPECT_TRUE(ptr->apply(1, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Sub, 10))));
    EXPECT_TRUE(ptr->apply(2, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Mul, 10))));
    EXPECT_TRUE(ptr->apply(3, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Div, 10))));
    EXPECT_TRUE(ptr->apply(6, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(70)))));
    ptr->commit();
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 12u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 5u);

    std::vector<BufferType> buf(2);
    ptr->get(0, &buf[0], 2);
    EXPECT_EQ(buf[0].getWeight(), 110);
    ptr->get(1, &buf[0], 2);
    EXPECT_EQ(buf[0].getWeight(), 90);
    ptr->get(2, &buf[0], 2);
    EXPECT_EQ(buf[0].getWeight(), 1000);
    ptr->get(3, &buf[0], 2);
    EXPECT_EQ(buf[0].getWeight(), 10);
    ptr->get(6, &buf[0], 2);
    EXPECT_EQ(buf[0].getWeight(), 70);

    // removeifzero
    EXPECT_TRUE(ptr->apply(4, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Sub, 100))));
    ptr->commit();
    if (removeIfZero) {
        EXPECT_EQ(ptr->get(4, &buf[0], 2), uint32_t(0));
    } else {
        EXPECT_EQ(ptr->get(4, &buf[0], 2), uint32_t(1));
        EXPECT_EQ(buf[0].getWeight(), 0);
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 13u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 6u);

    // createifnonexistant
    EXPECT_TRUE(ptr->apply(5, MapVU(std::unique_ptr<FieldValue>(nonExistant.clone()), std::make_unique<ArithVU>(ArithVU::Add, 10))));
    ptr->commit();
    if (createIfNonExistant) {
        EXPECT_EQ(ptr->get(5, &buf[0], 2), uint32_t(2));
        std::sort(buf.begin(), buf.begin() + 2, order_by_weight());
        EXPECT_EQ(buf[0].getWeight(), 10);
        EXPECT_EQ(buf[1].getWeight(), 100);
    } else {
        EXPECT_EQ(ptr->get(5, &buf[0], 2), uint32_t(1));
        EXPECT_EQ(buf[0].getWeight(), 100);
    }
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 14u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 7u);


    // try divide by zero (should be ignored)
    vec.clearDoc(0);
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 15u);
    ASSERT_TRUE(vec.append(0, initValue.getValue(), 12345));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 16u);
    EXPECT_TRUE(ptr->apply(0, MapVU(std::unique_ptr<FieldValue>(initFieldValue.clone()), std::make_unique<ArithVU>(ArithVU::Div, 0))));
    EXPECT_EQ(ptr->getStatus().getUpdateCount(), 16u);
    EXPECT_EQ(ptr->getStatus().getNonIdempotentUpdateCount(), 7u);
    ptr->commit();
    ptr->get(0, &buf[0], 1);
    EXPECT_EQ(buf[0].getWeight(), 12345);
}

void
AttributeTest::testMapValueUpdate()
{
    { // regular set
        AttributePtr ptr = createAttribute("wsint32", Config(BasicType::INT32, CollectionType::WSET));
        testMapValueUpdate<IntegerAttribute, AttributeVector::WeightedInt>
            (ptr, AttributeVector::WeightedInt(64, 1), IntFieldValue(64), IntFieldValue(32), false, false);
    }
    { // remove if zero
        AttributePtr ptr = createAttribute("wsint32", Config(BasicType::INT32, CollectionType(CollectionType::WSET, true, false)));
        testMapValueUpdate<IntegerAttribute, AttributeVector::WeightedInt>
            (ptr, AttributeVector::WeightedInt(64, 1), IntFieldValue(64), IntFieldValue(32), true, false);
    }
    { // create if non existant
        AttributePtr ptr = createAttribute("wsint32", Config(BasicType::INT32,
                                                             CollectionType(CollectionType::WSET, false, true)));
        testMapValueUpdate<IntegerAttribute, AttributeVector::WeightedInt>
            (ptr, AttributeVector::WeightedInt(64, 1), IntFieldValue(64), IntFieldValue(32), false, true);
    }

    Config setCfg(Config(BasicType::STRING, CollectionType::WSET));
    Config setRemoveCfg(Config(BasicType::STRING, CollectionType(CollectionType::WSET, true, false)));
    Config setCreateCfg(Config(BasicType::STRING, CollectionType(CollectionType::WSET, false, true)));

    { // regular set
        AttributePtr ptr = createAttribute("wsstr", setCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), false, false);
    }
    { // remove if zero
        AttributePtr ptr = createAttribute("wsstr", setRemoveCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), true, false);
    }
    { // create if non existant
        AttributePtr ptr = createAttribute("wsstr", setCreateCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), false, true);
    }

    // fast-search - posting lists
    { // regular set
        setCfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("wsfsstr", setCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), false, false);
    }
    { // remove if zero
        setRemoveCfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("wsfsstr", setRemoveCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), true, false);
    }
    { // create if non existant
        setCreateCfg.setFastSearch(true);
        AttributePtr ptr = createAttribute("wsfsstr", setCreateCfg);
        testMapValueUpdate<StringAttribute, AttributeVector::WeightedString>
            (ptr, AttributeVector::WeightedString("first", 1), StringFieldValue("first"), StringFieldValue("second"), false, true);
    }
}

void
AttributeTest::commit(const AttributePtr & ptr)
{
    ptr->commit();
}

void
AttributeTest::testStatus()
{
    std::vector<string> values;
    fillString(values, 16);
    uint32_t numDocs = 100;
    // No posting list
    static constexpr size_t LeafNodeSize = 4 + sizeof(IEnumStore::Index) * EnumTreeTraits::LEAF_SLOTS;
    static constexpr size_t InternalNodeSize =
        8 + (sizeof(IEnumStore::Index) + sizeof(vespalib::datastore::EntryRef)) * EnumTreeTraits::INTERNAL_SLOTS;
    static constexpr size_t NestedVectorSize = 24; // sizeof(vespalib::Array)

    {
        Config cfg(BasicType::STRING, CollectionType::ARRAY);
        AttributePtr ptr = createAttribute("as", cfg);
        addDocs(ptr, numDocs);
        auto & sa = *(static_cast<StringAttribute *>(ptr.get()));
        for (uint32_t i = 0; i < numDocs; ++i) {
            EXPECT_TRUE(appendToVector(sa, i, 1, values));
        }
        ptr->commit(true);
        EXPECT_EQ(ptr->getStatus().getNumDocs(), 100u);
        EXPECT_EQ(ptr->getStatus().getNumValues(), 100u);
        EXPECT_EQ(ptr->getStatus().getNumUniqueValues(), 1u);
        size_t expUsed = 0;
        expUsed += 1 * InternalNodeSize + 1 * LeafNodeSize; // enum store tree
        expUsed += 1 * 32; // enum store (uniquevalues * bytes per entry)
        // multi value mapping (numdocs * sizeof(MappingIndex) + numvalues * sizeof(EnumIndex))
        expUsed += 100 * sizeof(vespalib::datastore::EntryRef) + 100 * 4;
        EXPECT_GE(ptr->getStatus().getUsed(), expUsed);
        EXPECT_GE(ptr->getStatus().getAllocated(), expUsed);
    }

    {
        Config cfg(BasicType::STRING, CollectionType::ARRAY);
        AttributePtr ptr = createAttribute("as", cfg);
        addDocs(ptr, numDocs);
        auto & sa = *(static_cast<StringAttribute *>(ptr.get()));
        const size_t numValuesPerDoc(values.size());
        const size_t numUniq(numValuesPerDoc);
        for (uint32_t i = 0; i < numDocs; ++i) {
            EXPECT_TRUE(appendToVector(sa, i, numValuesPerDoc, values));
        }
        ptr->commit(true);
        EXPECT_EQ(ptr->getStatus().getNumDocs(), numDocs);
        EXPECT_EQ(ptr->getStatus().getNumValues(), numDocs*numValuesPerDoc);
        EXPECT_EQ(ptr->getStatus().getNumUniqueValues(), numUniq);
        size_t expUsed = 0;
        expUsed += 1 * InternalNodeSize + 1 * LeafNodeSize; // Approximate enum store tree
        expUsed += 272; // TODO Approximate... enum store (16 unique values, 17 bytes per entry)
        // multi value mapping (numdocs * sizeof(MappingIndex) + numvalues * sizeof(EnumIndex) +
        // 32 + numdocs * sizeof(Array<EnumIndex>) (due to vector vector))
        expUsed += 32 + numDocs * sizeof(vespalib::datastore::EntryRef) + numDocs * numValuesPerDoc * sizeof(IEnumStore::Index) + ((numValuesPerDoc > 1024) ? numDocs * NestedVectorSize : 0);
        EXPECT_GE(ptr->getStatus().getUsed(), expUsed);
        EXPECT_GE(ptr->getStatus().getAllocated(), expUsed);
    }
}

void
AttributeTest::testNullProtection()
{
    size_t len1 = strlen("evil");
    size_t len2 = strlen("string");
    size_t len  = len1 + 1 + len2;
    string good("good");
    string evil("evil string");
    string pureEvil("evil");
    EXPECT_EQ(strlen(evil.data()),  len);
    EXPECT_EQ(strlen(evil.c_str()), len);
    evil[len1] = 0; // replace space with '\0'
    EXPECT_EQ(strlen(evil.data()),  len1);
    EXPECT_EQ(strlen(evil.c_str()), len1);
    EXPECT_EQ(strlen(evil.data()  + len1), 0u);
    EXPECT_EQ(strlen(evil.c_str() + len1), 0u);
    EXPECT_EQ(strlen(evil.data()  + len1 + 1), len2);
    EXPECT_EQ(strlen(evil.c_str() + len1 + 1), len2);
    EXPECT_EQ(evil.size(), len);
    { // string
        AttributeVector::DocId docId;
        std::vector<string> buf(16);
        AttributePtr attr = createAttribute("string", Config(BasicType::STRING, CollectionType::SINGLE));
        StringAttribute &v = static_cast<StringAttribute &>(*attr.get());
        EXPECT_TRUE(v.addDoc(docId));
        EXPECT_TRUE(v.update(docId, evil));
        v.commit();
        size_t n = static_cast<const AttributeVector &>(v).get(docId, &buf[0], buf.size());
        EXPECT_EQ(n, 1u);
        EXPECT_EQ(buf[0], pureEvil);
    }
    { // string array
        AttributeVector::DocId docId;
        std::vector<string> buf(16);
        AttributePtr attr = createAttribute("string", Config(BasicType::STRING, CollectionType::ARRAY));
        auto &v = static_cast<StringAttribute &>(*attr.get());
        EXPECT_TRUE(v.addDoc(docId));
        EXPECT_TRUE(v.append(0, good, 1));
        EXPECT_TRUE(v.append(0, evil, 1));
        EXPECT_TRUE(v.append(0, good, 1));
        v.commit();
        size_t n = static_cast<const AttributeVector &>(v).get(0, &buf[0], buf.size());
        EXPECT_EQ(n, 3u);
        EXPECT_EQ(buf[0], good);
        EXPECT_EQ(buf[1], pureEvil);
        EXPECT_EQ(buf[2], good);
    }
    { // string set
        AttributeVector::DocId docId;
        std::vector<StringAttribute::WeightedString> buf(16);
        AttributePtr attr = createAttribute("string", Config(BasicType::STRING, CollectionType::WSET));
        auto &v = static_cast<StringAttribute &>(*attr.get());
        EXPECT_TRUE(v.addDoc(docId));
        EXPECT_TRUE(v.append(0, good, 10));
        EXPECT_TRUE(v.append(0, evil, 20));
        v.commit();
        size_t n = static_cast<const AttributeVector &>(v).get(0, &buf[0], buf.size());
        EXPECT_EQ(n, 2u);
        if (buf[0].getValue() != good) {
            std::swap(buf[0], buf[1]);
        }
        EXPECT_EQ(buf[0].getValue(), good);
        EXPECT_EQ(buf[0].getWeight(), 10);
        EXPECT_EQ(buf[1].getValue(), pureEvil);
        EXPECT_EQ(buf[1].getWeight(), 20);

        // remove
        EXPECT_TRUE(v.remove(0, evil, 20));
        v.commit();
        n = static_cast<const AttributeVector &>(v).get(0, &buf[0], buf.size());
        EXPECT_EQ(n, 1u);
        EXPECT_EQ(buf[0].getValue(), good);
        EXPECT_EQ(buf[0].getWeight(), 10);
    }
}

void
AttributeTest::testGeneration(const AttributePtr & attr, bool exactStatus)
{
    LOG(info, "testGeneration(%s)", attr->getName().c_str());
    auto & ia = static_cast<IntegerAttribute &>(*attr.get());
    // add docs to trigger inc generation when data vector is full
    AttributeVector::DocId docId;
    EXPECT_EQ(0u, ia.getCurrentGeneration());
    EXPECT_TRUE(ia.addDoc(docId));
    EXPECT_EQ(0u, ia.getCurrentGeneration());
    EXPECT_TRUE(ia.addDoc(docId));
    EXPECT_EQ(0u, ia.getCurrentGeneration());
    ia.commit(true);
    EXPECT_EQ(1u, ia.getCurrentGeneration());
    uint64_t lastAllocated;
    uint64_t lastOnHold;
    vespalib::MemoryUsage changeVectorMemoryUsage(attr->getChangeVectorMemoryUsage());
    size_t changeVectorAllocated = changeVectorMemoryUsage.allocatedBytes();
    if (exactStatus) {
        EXPECT_EQ(2u + changeVectorAllocated, ia.getStatus().getAllocated());
        EXPECT_EQ(0u, ia.getStatus().getOnHold());
    } else {
        EXPECT_LT(0u + changeVectorAllocated, ia.getStatus().getAllocated());
        EXPECT_EQ(0u, ia.getStatus().getOnHold());
        lastAllocated = ia.getStatus().getAllocated();
        lastOnHold = ia.getStatus().getOnHold();
    }
    {
        AttributeGuard ag(attr); // guard on generation 1
        EXPECT_TRUE(ia.addDoc(docId)); // inc gen
        EXPECT_EQ(2u, ia.getCurrentGeneration());
        ia.commit(true);
        EXPECT_EQ(3u, ia.getCurrentGeneration());
        if (exactStatus) {
            EXPECT_EQ(6u + changeVectorAllocated, ia.getStatus().getAllocated());
            EXPECT_EQ(2u, ia.getStatus().getOnHold()); // no cleanup due to guard
        } else {
            EXPECT_LT(lastAllocated, ia.getStatus().getAllocated());
            EXPECT_LT(lastOnHold, ia.getStatus().getOnHold());
            lastAllocated = ia.getStatus().getAllocated();
            lastOnHold = ia.getStatus().getOnHold();
        }
    }
    EXPECT_TRUE(ia.addDoc(docId));
    EXPECT_EQ(3u, ia.getCurrentGeneration());
    {
        AttributeGuard ag(attr); // guard on generation 3
        ia.commit(true);
        EXPECT_EQ(4u, ia.getCurrentGeneration());
        if (exactStatus) {
            EXPECT_EQ(4u + changeVectorAllocated, ia.getStatus().getAllocated());
            EXPECT_EQ(0u, ia.getStatus().getOnHold()); // cleanup at end of addDoc()
        } else {
            EXPECT_GT(lastAllocated, ia.getStatus().getAllocated());
            EXPECT_GT(lastOnHold, ia.getStatus().getOnHold());
            lastAllocated = ia.getStatus().getAllocated();
            lastOnHold = ia.getStatus().getOnHold();
        }
    }
    {
        AttributeGuard ag(attr); // guard on generation 4
        EXPECT_TRUE(ia.addDoc(docId)); // inc gen
        EXPECT_EQ(5u, ia.getCurrentGeneration());
        ia.commit();
        EXPECT_EQ(6u, ia.getCurrentGeneration());
        if (exactStatus) {
            EXPECT_EQ(10u + changeVectorAllocated, ia.getStatus().getAllocated());
            EXPECT_EQ(4u, ia.getStatus().getOnHold()); // no cleanup due to guard
        } else {
            EXPECT_LT(lastAllocated, ia.getStatus().getAllocated());
            EXPECT_LT(lastOnHold, ia.getStatus().getOnHold());
            lastAllocated = ia.getStatus().getAllocated();
            lastOnHold = ia.getStatus().getOnHold();
        }
    }
    ia.commit(true);
    EXPECT_EQ(7u, ia.getCurrentGeneration());
    if (exactStatus) {
        EXPECT_EQ(6u + changeVectorAllocated, ia.getStatus().getAllocated());
        EXPECT_EQ(0u, ia.getStatus().getOnHold()); // cleanup at end of commit()
    } else {
        EXPECT_GT(lastAllocated, ia.getStatus().getAllocated());
        EXPECT_GT(lastOnHold, ia.getStatus().getOnHold());
    }
}

void
AttributeTest::testGeneration()
{
    { // single value attribute
        Config cfg(BasicType::INT8);
        cfg.setGrowStrategy(GrowStrategy::make(2, 0, 2));
        AttributePtr attr = createAttribute("int8", cfg);
        testGeneration(attr, true);
    }
    { // enum attribute (with fast search)
        Config cfg(BasicType::INT8);
        cfg.setFastSearch(true);
        cfg.setGrowStrategy(GrowStrategy::make(2, 0, 2));
        AttributePtr attr = createAttribute("faint8", cfg);
        testGeneration(attr, false);
    }
    { // multi value attribute
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setGrowStrategy(GrowStrategy::make(2, 0, 2));
        AttributePtr attr = createAttribute("aint8", cfg);
        testGeneration(attr, false);
    }
    { // multi value enum attribute (with fast search)
        Config cfg(BasicType::INT8, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        cfg.setGrowStrategy(GrowStrategy::make(2, 0, 2));
        AttributePtr attr = createAttribute("faaint8", cfg);
        testGeneration(attr, false);
    }
}


void
AttributeTest::testCreateSerialNum()
{
    Config cfg(BasicType::INT32);
    AttributePtr attr = createAttribute("int32", cfg);
    attr->setCreateSerialNum(42u);
    EXPECT_TRUE(attr->save());
    AttributePtr attr2 = createAttribute("int32", cfg);
    EXPECT_TRUE(attr2->load());
    EXPECT_EQ(42u, attr2->getCreateSerialNum());
}

void
AttributeTest::testPredicateHeaderTags()
{
    Config cfg(BasicType::PREDICATE);
    AttributePtr attr = createAttribute("predicate", cfg);
    attr->addReservedDoc();
    EXPECT_TRUE(attr->save());
    auto df = search::FileUtil::openFile(baseFileName("predicate.dat"));
    vespalib::FileHeader datHeader;
    datHeader.readFile(*df);
    EXPECT_TRUE(datHeader.hasTag("predicate.arity"));
    EXPECT_TRUE(datHeader.hasTag("predicate.lower_bound"));
    EXPECT_TRUE(datHeader.hasTag("predicate.upper_bound"));
    EXPECT_EQ(8u, datHeader.getTag("predicate.arity").asInteger());
}

template <typename VectorType, typename BufferType>
void
AttributeTest::testCompactLidSpace(const Config &config,
                                   bool fast_search)
{
    uint32_t highDocs = 100;
    uint32_t trimmedDocs = 30;
    vespalib::string bts = config.basicType().asString();
    vespalib::string cts = config.collectionType().asString();
    vespalib::string fas = fast_search ? "-fs" : "";
    Config cfg = config;
    cfg.setFastSearch(fast_search);
    
    vespalib::string name = clsDir + "/" + bts + "-" + cts + fas;
    LOG(info, "testCompactLidSpace(%s)", name.c_str());
    AttributePtr attr = AttributeFactory::createAttribute(name, cfg);
    auto &v = static_cast<VectorType &>(*attr.get());
    attr->addDocs(highDocs);
    populate(v, 17);
    AttributePtr attr2 = AttributeFactory::createAttribute(name, cfg);
    auto &v2 = static_cast<VectorType &>(*attr2.get());
    attr2->addDocs(trimmedDocs);
    populate(v2, 17);
    EXPECT_EQ(trimmedDocs, attr2->getNumDocs());
    EXPECT_EQ(trimmedDocs, attr2->getCommittedDocIdLimit());
    EXPECT_EQ(highDocs, attr->getNumDocs());
    EXPECT_EQ(highDocs, attr->getCommittedDocIdLimit());
    attr->compactLidSpace(trimmedDocs);
    EXPECT_EQ(highDocs, attr->getNumDocs());
    EXPECT_EQ(trimmedDocs, attr->getCommittedDocIdLimit());
    EXPECT_TRUE(attr->save());
    EXPECT_EQ(highDocs, attr->getNumDocs());
    EXPECT_EQ(trimmedDocs, attr->getCommittedDocIdLimit());
    AttributePtr attr3 = AttributeFactory::createAttribute(name, cfg);
    EXPECT_TRUE(attr3->load());
    EXPECT_EQ(trimmedDocs, attr3->getNumDocs());
    EXPECT_EQ(trimmedDocs, attr3->getCommittedDocIdLimit());
    auto &v3 = static_cast<VectorType &>(*attr3.get());
    compare<VectorType, BufferType>(v2, v3);
    attr->shrinkLidSpace();
    EXPECT_EQ(trimmedDocs, attr->getNumDocs());
    EXPECT_EQ(trimmedDocs, attr->getCommittedDocIdLimit());
    compare<VectorType, BufferType>(v, v3);
}

template <typename VectorType, typename BufferType>
void
AttributeTest::testCompactLidSpace(const Config &config)
{
    testCompactLidSpace<VectorType, BufferType>(config, false);
    bool smallUInt = isUnsignedSmallIntAttribute(config.basicType().type());
    if (smallUInt) {
        return;
    }
    testCompactLidSpace<VectorType, BufferType>(config, true);
}

void
AttributeTest::testCompactLidSpaceForPredicateAttribute(const Config &config)
{
    vespalib::string name = clsDir + "/predicate-single";
    LOG(info, "testCompactLidSpace(%s)", name.c_str());
    AttributePtr attr = AttributeFactory::createAttribute(name, config);
    attr->addDocs(10);
    attr->compactLidSpace(10);
    attr->clearDoc(10);
    attr->compactLidSpace(11);
}

void
AttributeTest::testCompactLidSpace(const Config &config)
{
    SCOPED_TRACE(make_scoped_trace_msg("compact lid space", config));
    switch (config.basicType().type()) {
    case BasicType::BOOL:
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
    case BasicType::INT16:
    case BasicType::INT32:
    case BasicType::INT64:
        if (config.collectionType() == CollectionType::WSET) {
            testCompactLidSpace<IntegerAttribute, IntegerAttribute::WeightedInt>(config);
        } else {
            testCompactLidSpace<IntegerAttribute, IntegerAttribute::largeint_t>(config);
        }
        break;
    case BasicType::FLOAT:
    case BasicType::DOUBLE:
        if (config.collectionType() == CollectionType::WSET) {
            testCompactLidSpace<FloatingPointAttribute, FloatingPointAttribute::WeightedFloat>(config);
        } else {
            testCompactLidSpace<FloatingPointAttribute, double>(config);
        }
        break;
    case BasicType::STRING:
        if (config.collectionType() == CollectionType::WSET) {
            testCompactLidSpace<StringAttribute, StringAttribute::WeightedString>(config);
        } else {
            testCompactLidSpace<StringAttribute, string>(config);
        }
        break;
    case BasicType::PREDICATE:
        testCompactLidSpaceForPredicateAttribute(config);
        break;
    default:
        LOG_ABORT("should not be reached");
    }
}

void
AttributeTest::testCompactLidSpace()
{
    testCompactLidSpace(Config(BasicType::BOOL, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::UINT2, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::UINT4, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::INT8, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::INT8, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::INT8, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::INT16, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::INT16, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::INT16, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::INT32, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::INT32, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::INT32, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::INT64, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::INT64, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::INT64, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::FLOAT, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::FLOAT, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::FLOAT, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::DOUBLE, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::DOUBLE, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::DOUBLE, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::STRING, CollectionType::SINGLE));
    testCompactLidSpace(Config(BasicType::STRING, CollectionType::ARRAY));
    testCompactLidSpace(Config(BasicType::STRING, CollectionType::WSET));
    testCompactLidSpace(Config(BasicType::PREDICATE, CollectionType::SINGLE));
}

namespace {

uint32_t
get_default_value_ref_count(AttributeVector &attr, int32_t defaultValue)
{
    auto *enum_store_base = attr.getEnumStoreBase();
    auto &enum_store = dynamic_cast<EnumStoreT<int32_t> &>(*enum_store_base);
    IAttributeVector::EnumHandle default_value_handle(0);
    if (enum_store.find_enum(defaultValue, default_value_handle)) {
        vespalib::datastore::EntryRef default_value_ref(default_value_handle);
        assert(default_value_ref.valid());
        return enum_store.get_ref_count(default_value_ref);
    } else {
        return 0u;
    }
}

}


void
AttributeTest::test_default_value_ref_count_is_updated_after_shrink_lid_space()
{
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    vespalib::string name = "shrink";
    AttributePtr attr = AttributeFactory::createAttribute(name, cfg);
    const auto & iattr = dynamic_cast<const search::IntegerAttributeTemplate<int32_t> &>(*attr);
    attr->addReservedDoc();
    attr->addDocs(10);
    EXPECT_EQ(11u, get_default_value_ref_count(*attr, iattr.defaultValue()));
    attr->compactLidSpace(6);
    EXPECT_EQ(11u, get_default_value_ref_count(*attr, iattr.defaultValue()));
    attr->shrinkLidSpace();
    EXPECT_EQ(6u, attr->getNumDocs());
    EXPECT_EQ(6u, get_default_value_ref_count(*attr, iattr.defaultValue()));
}

template <typename AttributeType>
void
AttributeTest::requireThatAddressSpaceUsageIsReported(const Config &config, bool fastSearch)
{
    uint32_t numDocs = 10;
    vespalib::string attrName = asuDir + "/" + config.basicType().asString() + "-" +
            config.collectionType().asString() + (fastSearch ? "-fs" : "");
    Config cfg = config;
    cfg.setFastSearch(fastSearch);

    AttributePtr attrPtr = AttributeFactory::createAttribute(attrName, cfg);
    addDocs(attrPtr, numDocs);
    AddressSpaceUsage before = attrPtr->getAddressSpaceUsage();
    populate(static_cast<AttributeType &>(*attrPtr.get()), 5);
    AddressSpaceUsage after = attrPtr->getAddressSpaceUsage();
    if (attrPtr->hasEnum()) {
        LOG(info, "requireThatAddressSpaceUsageIsReported(%s): Has enum", attrName.c_str());
        EXPECT_EQ(before.enum_store_usage().used(), 1u);
        EXPECT_EQ(before.enum_store_usage().dead(), 1u);
        EXPECT_GT(after.enum_store_usage().used(), before.enum_store_usage().used());
        EXPECT_GE(after.enum_store_usage().limit(), before.enum_store_usage().limit());
        EXPECT_GT(after.enum_store_usage().limit(), 4200000000u);
    } else {
        LOG(info, "requireThatAddressSpaceUsageIsReported(%s): NOT enum", attrName.c_str());
        EXPECT_EQ(before.enum_store_usage().used(), 0u);
        EXPECT_EQ(before.enum_store_usage().dead(), 0u);
        EXPECT_EQ(after.enum_store_usage(), before.enum_store_usage());
        EXPECT_EQ(AddressSpaceComponents::default_enum_store_usage(), after.enum_store_usage());
    }
    if (attrPtr->hasMultiValue()) {
        LOG(info, "requireThatAddressSpaceUsageIsReported(%s): Has multi-value", attrName.c_str());
        EXPECT_EQ(before.multi_value_usage().used(), 1u);
        EXPECT_EQ(before.multi_value_usage().dead(), 1u);
        EXPECT_GE(after.multi_value_usage().used(), before.multi_value_usage().used());
        EXPECT_GT(after.multi_value_usage().limit(), before.multi_value_usage().limit());
        EXPECT_GT((1ull << 32), after.multi_value_usage().limit());
    } else {
        LOG(info, "requireThatAddressSpaceUsageIsReported(%s): NOT multi-value", attrName.c_str());
        EXPECT_EQ(before.multi_value_usage().used(), 0u);
        EXPECT_EQ(after.multi_value_usage(), before.multi_value_usage());
        EXPECT_EQ(AddressSpaceComponents::default_multi_value_usage(), after.multi_value_usage());
    }
}

template <typename AttributeType>
void
AttributeTest::requireThatAddressSpaceUsageIsReported(const Config &config)
{
    SCOPED_TRACE(make_scoped_trace_msg("address space is reported", config));
    requireThatAddressSpaceUsageIsReported<AttributeType>(config, false);
    requireThatAddressSpaceUsageIsReported<AttributeType>(config, true);
}

void
AttributeTest::requireThatAddressSpaceUsageIsReported()
{
    requireThatAddressSpaceUsageIsReported<IntegerAttribute>(Config(BasicType::INT32, CollectionType::SINGLE));
    requireThatAddressSpaceUsageIsReported<IntegerAttribute>(Config(BasicType::INT32, CollectionType::ARRAY));
    requireThatAddressSpaceUsageIsReported<FloatingPointAttribute>(Config(BasicType::FLOAT, CollectionType::SINGLE));
    requireThatAddressSpaceUsageIsReported<FloatingPointAttribute>(Config(BasicType::FLOAT, CollectionType::ARRAY));
    requireThatAddressSpaceUsageIsReported<StringAttribute>(Config(BasicType::STRING, CollectionType::SINGLE));
    requireThatAddressSpaceUsageIsReported<StringAttribute>(Config(BasicType::STRING, CollectionType::ARRAY));
}

template <typename AttributeType, typename BufferType>
void
AttributeTest::testReaderDuringLastUpdate(const Config &config, bool fs, bool compact)
{
    vespalib::asciistream ss;
    ss << "fill-" << config.basicType().asString() << "-" <<
        config.collectionType().asString() <<
        (fs ? "-fs" : "") <<
        (compact ? "-compact" : "");
    string name(ss.str());
    Config cfg = config;
    cfg.setFastSearch(fs);
    cfg.setGrowStrategy(GrowStrategy::make(100, 0.5, 0));

    LOG(info, "testReaderDuringLastUpdate(%s)", name.c_str());
    AttributePtr attr = AttributeFactory::createAttribute(name, cfg);
    auto &v = static_cast<AttributeType &>(*attr.get());
    constexpr uint32_t numDocs = 200;
    AttributeGuard guard;
    if (!compact) {
        // Hold read guard while populating attribute to keep data on hold list
        guard = AttributeGuard(attr);
    }
    addDocs(attr, numDocs);
    populate(v, numDocs);
    if (compact) {
        for (uint32_t i = 4; i < numDocs; ++i) {
            attr->clearDoc(i);
        }
        attr->commit();
        attr->incGeneration();
        attr->compactLidSpace(4);
        attr->commit();
        attr->incGeneration();
        // Hold read guard when shrinking lid space to keep data on hold list
        guard = AttributeGuard(attr);
        attr->shrinkLidSpace();
    }
}

template <typename AttributeType, typename BufferType>
void
AttributeTest::testReaderDuringLastUpdate(const Config &config)
{
    SCOPED_TRACE(make_scoped_trace_msg("reader during last update", config));
    testReaderDuringLastUpdate<AttributeType, BufferType>(config, false, false);
    testReaderDuringLastUpdate<AttributeType, BufferType>(config, true, false);
    testReaderDuringLastUpdate<AttributeType, BufferType>(config, false, true);
    testReaderDuringLastUpdate<AttributeType, BufferType>(config, true, true);
}

void
AttributeTest::testReaderDuringLastUpdate()
{
    testReaderDuringLastUpdate<IntegerAttribute,AttributeVector::largeint_t>(Config(BasicType::INT32, CollectionType::SINGLE));
    testReaderDuringLastUpdate<IntegerAttribute,AttributeVector::largeint_t>(Config(BasicType::INT32, CollectionType::ARRAY));
    testReaderDuringLastUpdate<IntegerAttribute,AttributeVector::WeightedInt>(Config(BasicType::INT32, CollectionType::WSET));
    testReaderDuringLastUpdate<FloatingPointAttribute,double>(Config(BasicType::FLOAT, CollectionType::SINGLE));
    testReaderDuringLastUpdate<FloatingPointAttribute,double>(Config(BasicType::FLOAT, CollectionType::ARRAY));
    testReaderDuringLastUpdate<FloatingPointAttribute,FloatingPointAttribute::WeightedFloat>(Config(BasicType::FLOAT, CollectionType::WSET));
    testReaderDuringLastUpdate<StringAttribute,string>(Config(BasicType::STRING, CollectionType::SINGLE));
    testReaderDuringLastUpdate<StringAttribute,string>(Config(BasicType::STRING, CollectionType::ARRAY));
    testReaderDuringLastUpdate<StringAttribute,StringAttribute::WeightedString>(Config(BasicType::STRING, CollectionType::WSET));
}

void
AttributeTest::testPendingCompaction()
{
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    AttributePtr v = createAttribute("sfsint32_pc", cfg);
    auto &iv = static_cast<IntegerAttribute &>(*v.get());
    addClearedDocs(v, 1000);   // first compaction, success
    AttributeGuard guard1(v);
    populateSimple(iv, 1, 3);  // 2nd compaction, success
    AttributeGuard guard2(v);
    populateSimple(iv, 3, 6);  // 3rd compaction, fail => fallbackResize
    guard1 = AttributeGuard(); // allow next compaction to succeed
    populateSimple(iv, 6, 10); // 4th compaction, success
    populateSimple(iv, 1, 2);  // should not trigger new compaction
}

void
AttributeTest::testConditionalCommit() {
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    cfg.setFastSearch(true);
    cfg.setMaxUnCommittedMemory(70000);
    AttributePtr v = createAttribute("sfsint32_cc", cfg);
    addClearedDocs(v, 1000);
    auto &iv = static_cast<IntegerAttribute &>(*v.get());
    EXPECT_EQ(0x8000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(0u, iv.getChangeVectorMemoryUsage().usedBytes());
    AttributeGuard guard1(v);
    populateSimpleUncommitted(iv, 1, 3);
    EXPECT_EQ(0x8000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(128u, iv.getChangeVectorMemoryUsage().usedBytes());
    populateSimpleUncommitted(iv, 1, 1000);
    EXPECT_EQ(0x10000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(64064u, iv.getChangeVectorMemoryUsage().usedBytes());
    EXPECT_FALSE(v->commitIfChangeVectorTooLarge());
    EXPECT_EQ(0x10000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(64064u, iv.getChangeVectorMemoryUsage().usedBytes());
    populateSimpleUncommitted(iv, 1, 200);
    EXPECT_EQ(0x20000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(76800u, iv.getChangeVectorMemoryUsage().usedBytes());
    EXPECT_TRUE(v->commitIfChangeVectorTooLarge());
    EXPECT_EQ(0x2000u, iv.getChangeVectorMemoryUsage().allocatedBytes());
    EXPECT_EQ(0u, iv.getChangeVectorMemoryUsage().usedBytes());
}

int
AttributeTest::test_paged_attribute(const vespalib::string& name, const vespalib::string& swapfile, const search::attribute::Config& cfg)
{
    int result = 1;
    size_t rounded_size = vespalib::round_up_to_page_size(1);
    size_t lid_mapping_size = 1200;
    size_t sv_maxlid = 1200;
    if (rounded_size == 64_Ki) {
        lid_mapping_size = 17000;
        sv_maxlid = 1500;
    }
    if (cfg.basicType() == search::attribute::BasicType::Type::BOOL) {
        lid_mapping_size = rounded_size * 8 + 100;
    }
    LOG(info, "test_paged_attribute '%s'", name.c_str());
    auto av = createAttribute(name, cfg);
    auto v = std::dynamic_pointer_cast<IntegerAttribute>(av);
    bool failed = false;
    EXPECT_TRUE(v || (!cfg.collectionType().isMultiValue() && !cfg.fastSearch())) << (failed = true, "");
    if (failed) {
        return 0;
    }
    auto size1 = std::filesystem::file_size(std::filesystem::path(swapfile));
    // Grow mapping from lid to value or multivalue index
    addClearedDocs(av, lid_mapping_size);
    auto size2 = std::filesystem::file_size(std::filesystem::path(swapfile));
    auto size3 = size2;
    EXPECT_LT(size1, size2);
    if (cfg.collectionType().isMultiValue()) {
        // Grow multi value mapping
        for (uint32_t lid = 1; lid < 100; ++lid) {
            av->clearDoc(lid);
            for (uint32_t i = 0; i < 50; ++i) {
                EXPECT_TRUE(v->append(lid, 0, 1));
            }
            av->commit();
        }
        size3 = std::filesystem::file_size(std::filesystem::path(swapfile));
        EXPECT_LT(size2, size3);
        result += 2;
    }
    if (cfg.fastSearch()) {
        // Grow enum store
        uint32_t maxlid = cfg.collectionType().isMultiValue() ? 100 : sv_maxlid;
        for (uint32_t lid = 1; lid < maxlid; ++lid) {
            av->clearDoc(lid);
            if (cfg.collectionType().isMultiValue()) {
                for (uint32_t i = 0; i < 50; ++i) {
                    EXPECT_TRUE(v->append(lid, lid * 100 + i, 1));
                }
            } else {
                EXPECT_TRUE(v->update(lid, lid * 100));
            }
            av->commit();
        }
        auto size4 = std::filesystem::file_size(std::filesystem::path(swapfile));
        EXPECT_LT(size3, size4);
        result += 4;
    }
    return result;
}

void
AttributeTest::test_paged_attributes()
{
    vespalib::string basedir("mmap-file-allocator-factory-dir");
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup(basedir);
    search::attribute::Config cfg1(BasicType::INT32, CollectionType::SINGLE);
    cfg1.setPaged(true);
    EXPECT_EQ(1, test_paged_attribute("std-int-sv-paged", basedir + "/0.std-int-sv-paged/swapfile", cfg1));
    search::attribute::Config cfg2(BasicType::INT32, CollectionType::ARRAY);
    cfg2.setPaged(true);
    EXPECT_EQ(3, test_paged_attribute("std-int-mv-paged", basedir + "/1.std-int-mv-paged/swapfile", cfg2));
    search::attribute::Config cfg3(BasicType::INT32, CollectionType::SINGLE);
    cfg3.setPaged(true);
    cfg3.setFastSearch(true);
    EXPECT_EQ(5, test_paged_attribute("fs-int-sv-paged", basedir + "/2.fs-int-sv-paged/swapfile", cfg3));
    search::attribute::Config cfg4(BasicType::INT32, CollectionType::ARRAY);
    cfg4.setPaged(true);
    cfg4.setFastSearch(true);
    EXPECT_EQ(7, test_paged_attribute("fs-int-mv-paged", basedir + "/3.fs-int-mv-paged/swapfile", cfg4));
    search::attribute::Config cfg5(BasicType::BOOL, CollectionType::SINGLE);
    cfg5.setPaged(true);
    EXPECT_EQ(1, test_paged_attribute("std-bool-sv-paged", basedir + "/4.std-bool-sv-paged/swapfile", cfg5));
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup("");
    std::filesystem::remove_all(std::filesystem::path(basedir));
}

void testNamePrefix() {
    Config cfg(BasicType::INT32, CollectionType::SINGLE);
    AttributeVector::SP vFlat = createAttribute("sfsint32_pc", cfg);
    AttributeVector::SP vS1 = createAttribute("sfsint32_pc.abc", cfg);
    AttributeVector::SP vS2 = createAttribute("sfsint32_pc.xyz", cfg);
    AttributeVector::SP vSS1 = createAttribute("sfsint32_pc.xyz.abc", cfg);
    EXPECT_EQ("sfsint32_pc", vFlat->getName());
    EXPECT_EQ("sfsint32_pc", vFlat->getNamePrefix());
    EXPECT_EQ("sfsint32_pc.abc", vS1->getName());
    EXPECT_EQ("sfsint32_pc", vS1->getNamePrefix());
    EXPECT_EQ("sfsint32_pc.xyz", vS2->getName());
    EXPECT_EQ("sfsint32_pc", vS2->getNamePrefix());
    EXPECT_EQ("sfsint32_pc.xyz.abc", vSS1->getName());
    EXPECT_EQ("sfsint32_pc", vSS1->getNamePrefix());
}

class MyMultiValueAttribute : public ArrayStringAttribute {
public:
    MyMultiValueAttribute(const vespalib::string& name)
        : ArrayStringAttribute(name, Config(BasicType::STRING, CollectionType::ARRAY))
    {
    }
    bool has_free_lists_enabled() const { return this->_mvMapping.has_free_lists_enabled(); }
};

void
test_multi_value_mapping_has_free_lists_enabled()
{
    MyMultiValueAttribute attr("mvtest");
    EXPECT_TRUE(attr.has_free_lists_enabled());
}

TEST_F(AttributeTest, base_name)
{
    testBaseName();
}

TEST_F(AttributeTest, reload)
{
    testReload();
}

TEST_F(AttributeTest, has_load_data)
{
    testHasLoadData();
}

TEST_F(AttributeTest, memory_saver)
{
    testMemorySaver();
}

TEST_F(AttributeTest, single_value_attributes)
{
    testSingle();
}

TEST_F(AttributeTest, array_attributes)
{
    testArray();
}

TEST_F(AttributeTest, weighted_set_attributes)
{
    testWeightedSet();
}

TEST_F(AttributeTest, arithmetic_value_update)
{
    testArithmeticValueUpdate();
}

TEST_F(AttributeTest, arithmetic_with_undefined_value)
{
    testArithmeticWithUndefinedValue();
}

TEST_F(AttributeTest, map_value_udpate)
{
    testMapValueUpdate();
}

TEST_F(AttributeTest, status)
{
    testStatus();
}

TEST_F(AttributeTest, null_protection)
{
    testNullProtection();
}

TEST_F(AttributeTest, generation)
{
    testGeneration();
}

TEST_F(AttributeTest, create_serial_num)
{
    testCreateSerialNum();
}

TEST_F(AttributeTest, predicate_header_tags)
{
    testPredicateHeaderTags();
}

TEST_F(AttributeTest, compact_lid_space)
{
    testCompactLidSpace();
}

TEST_F(AttributeTest, default_value_ref_count_is_updated_after_shrink_lid_space)
{
    test_default_value_ref_count_is_updated_after_shrink_lid_space();
}

TEST_F(AttributeTest, address_space_usage_is_reported)
{
    requireThatAddressSpaceUsageIsReported();
}

TEST_F(AttributeTest, reader_during_last_update)
{
    testReaderDuringLastUpdate();
}

TEST_F(AttributeTest, pending_compaction)
{
    testPendingCompaction();
}

TEST_F(AttributeTest, conditional_commit)
{
    testConditionalCommit();
}

TEST_F(AttributeTest, name_prefix)
{
    testNamePrefix();
}

TEST_F(AttributeTest, multi_value_mapping_has_free_lists_enabled)
{
    test_multi_value_mapping_has_free_lists_enabled();
}

TEST_F(AttributeTest, paged_attributes)
{
    test_paged_attributes();
}

}

void
deleteDataDirs()
{
    std::filesystem::remove_all(std::filesystem::path(tmpDir));
    std::filesystem::remove_all(std::filesystem::path(clsDir));
    std::filesystem::remove_all(std::filesystem::path(asuDir));
}

void
createDataDirs()
{
    std::filesystem::create_directories(std::filesystem::path(tmpDir));
    std::filesystem::create_directories(std::filesystem::path(clsDir));
    std::filesystem::create_directories(std::filesystem::path(asuDir));
}

int
main(int argc, char* argv[])
{
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    ::testing::InitGoogleTest(&argc, argv);
    deleteDataDirs();
    createDataDirs();
    auto result = RUN_ALL_TESTS();
    deleteDataDirs();
    return result;
}
