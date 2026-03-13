// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributememoryfilebufferwriter.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributesaver.h>
#include <vespa/searchlib/attribute/i_enum_store_dictionary.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <limits>
#include <cmath>
#include <filesystem>
#include <sstream>
#include <tuple>

using search::AttributeFactory;
using search::AttributeVector;
using search::AttributeMemoryFileBufferWriter;
using search::BufferWriter;
using search::CommitParam;
using search::FloatingPointAttribute;
using search::IAttributeFileWriter;
using search::IntegerAttribute;
using search::ParseItem;
using search::RandomGenerator;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::fef::TermFieldMatchData;
using vespalib::TypifyResultType;
using vespalib::typify_invoke;

using SearchContextPtr = std::unique_ptr<SearchContext>;
using SearchBasePtr = std::unique_ptr<search::queryeval::SearchIterator>;

std::string test_dir = "test_data";

std::string make_attr_name(const std::string name) {
    return test_dir + "/" + name;
}

class MemAttrFileWriter : public IAttributeFileWriter
{
private:
    Buffer _buf;

public:
    MemAttrFileWriter()
        : _buf()
    {
    }

    Buffer allocBuf(size_t size) override {
        return std::make_unique<BufferBuf>(size, search::FileSettings::DIRECTIO_ALIGNMENT);
    }

    void writeBuf(Buffer buf_in) override {
        if (!_buf) {
            _buf = std::move(buf_in);
        } else {
            _buf->writeBytes(buf_in->getData(), buf_in->getDataLen());
        }
    }

    const Buffer &buf() const { return _buf; }

    std::unique_ptr<BufferWriter> allocBufferWriter() override;
};

std::unique_ptr<BufferWriter>
MemAttrFileWriter::allocBufferWriter()
{
    if (!_buf) {
        _buf = allocBuf(1);
    }
    return std::make_unique<AttributeMemoryFileBufferWriter>(*this);
}

class MemAttr : public search::IAttributeSaveTarget
{
private:
    MemAttrFileWriter _datWriter;
    MemAttrFileWriter _idxWriter;
    MemAttrFileWriter _weightWriter;
    MemAttrFileWriter _udatWriter;

public:
    using SP = std::shared_ptr<MemAttr>;

    MemAttr();
    ~MemAttr() override;

    bool setup() override { return true; }
    void close() override {}
    IAttributeFileWriter &datWriter() override { return _datWriter; }
    IAttributeFileWriter &idxWriter() override { return _idxWriter; }
    IAttributeFileWriter &weightWriter() override {
        return _weightWriter;
    }
    IAttributeFileWriter &udatWriter() override { return _udatWriter; }

    bool setup_writer(const std::string& file_suffix,
                      const std::string& desc) override {
        (void) file_suffix;
        (void) desc;
        abort();
    }
    IAttributeFileWriter& get_writer(const std::string& file_suffix) override {
        (void) file_suffix;
        abort();
    }

    bool bufEqual(const Buffer &lhs, const Buffer &rhs) const;

    bool operator==(const MemAttr &rhs) const;

    uint64_t size_on_disk() const noexcept override { return 0; }
};

MemAttr::MemAttr() = default;
MemAttr::~MemAttr() = default;

struct TypifyVectorType {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(BasicType value, F &&f) {
        switch (value.type()) {
            case BasicType::INT8:
            case BasicType::INT16:
            case BasicType::INT32:
            case BasicType::INT64:
                return f(Result<IntegerAttribute>());
            case BasicType::FLOAT:
            case BasicType::DOUBLE:
                return f(Result<FloatingPointAttribute>());
            case BasicType::STRING:
                return f(Result<StringAttribute>());
            default: break;
        }
        abort();
    }
};

template <typename VectorType>
struct BufferTypes;

template <>
struct BufferTypes<IntegerAttribute> {
    using Normal = IntegerAttribute::largeint_t;
    using Weighted = IntegerAttribute::WeightedInt;
};

template <>
struct BufferTypes<FloatingPointAttribute> {
    using Normal = double;
    using Weighted = FloatingPointAttribute::WeightedFloat;
};

template <>
struct BufferTypes<StringAttribute> {
    using Normal = std::string;
    using Weighted = StringAttribute::WeightedString;
};

namespace search::attribute {

void PrintTo(const BasicType& bt, std::ostream* os) {
    *os << bt.asString();
}

void PrintTo(const CollectionType& ct, std::ostream* os) {
    *os << ct.asString();
}

}

std::string
param_as_string(const testing::TestParamInfo<std::tuple<BasicType, CollectionType>>& info)
{
    std::ostringstream os;
    auto& param = info.param;
    os << std::get<0>(param).asString() << "_";
    os << std::get<1>(param).asString();
    return os.str();
}

class EnumeratedSaveTest : public ::testing::TestWithParam<std::tuple<BasicType, CollectionType>>
{
private:
    using AttributePtr = AttributeVector::SP;
    struct CallPopulate;
    struct CallCompare;
    struct CallGetSearch;

    template <typename VectorType>
    VectorType & as(AttributePtr &v);
    IntegerAttribute & asInt(AttributePtr &v);
    StringAttribute & asString(AttributePtr &v);
    FloatingPointAttribute & asFloat(AttributePtr &v);
    void addDocs(const AttributePtr &v, size_t sz);

    template <typename VectorType>
    void populate(VectorType &v, unsigned seed, BasicType bt);

    void populate(AttributePtr &v, unsigned seed, BasicType bt);

    template <typename VectorType, typename BufferType>
    void compare(VectorType &a, VectorType &b);

    void compare(AttributePtr& a, AttributePtr& b);

    void buildTermQuery(std::vector<char> & buffer, const std::string & index,
                        const std::string & term, bool prefix);

    template <typename V, typename T>
    SearchContextPtr getSearch(const V & vec, const T & term, bool prefix);

    template <typename V>
    SearchContextPtr getSearch(const V & vec);

    SearchContextPtr getSearch(AttributePtr& v);

    MemAttr::SP saveMem(AttributeVector &v);
    void saveMemDuringCompaction(AttributeVector &v);
    void checkMem(AttributeVector &v, const MemAttr &e, const std::string& label);
    MemAttr::SP saveBoth(AttributePtr v);
    AttributePtr make(Config cfg, const std::string &pref, bool fastSearch = false);
    void load(AttributePtr v, const std::string &name);

    AttributePtr checkLoad(Config cfg, const std::string &name, AttributePtr ev, const std::string& label);

    void testReload(AttributePtr v0, AttributePtr v1, AttributePtr v2,
                    MemAttr::SP mv0, MemAttr::SP mv1, MemAttr::SP mv2,
                    MemAttr::SP emv0, MemAttr::SP emv1, MemAttr::SP emv2,
                    Config cfg, const std::string &pref, bool fastSearch, search::DictionaryConfig dictionary_config);

protected:
    EnumeratedSaveTest();
    ~EnumeratedSaveTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    void test(BasicType bt, CollectionType ct, const std::string &pref);

};

struct EnumeratedSaveTest::CallPopulate {
    template <typename VectorType>
    static void invoke(EnumeratedSaveTest& test, AttributePtr& v, unsigned seed, BasicType bt) {
        test.populate(test.as<VectorType>(v), seed, bt);
    }
};

struct EnumeratedSaveTest::CallCompare {
    template <typename VectorType>
    static void invoke(EnumeratedSaveTest& test, AttributePtr& a, AttributePtr& b) {
        if (a->getConfig().collectionType() == CollectionType::WSET) {
            test.compare<VectorType,typename BufferTypes<VectorType>::Weighted>(test.as<VectorType>(a),
                test.as<VectorType>(b));
        } else {
            test.compare<VectorType,typename BufferTypes<VectorType>::Normal>(test.as<VectorType>(a),
                test.as<VectorType>(b));
        }
    }
};

struct EnumeratedSaveTest::CallGetSearch {
    template <typename VectorType>
    static SearchContextPtr invoke(EnumeratedSaveTest& test, AttributePtr& v) {
        return test.getSearch<VectorType>(test.as<VectorType>(v));
    }
};

EnumeratedSaveTest::EnumeratedSaveTest()
    : ::testing::TestWithParam<std::tuple<BasicType, CollectionType>>() {
}

EnumeratedSaveTest::~EnumeratedSaveTest() = default;

void
EnumeratedSaveTest::SetUpTestSuite()
{
    std::filesystem::remove_all(test_dir);
    std::filesystem::create_directory(test_dir);
}

void
EnumeratedSaveTest::TearDownTestSuite()
{
    std::filesystem::remove_all(test_dir);
}

bool
MemAttr::bufEqual(const Buffer &lhs, const Buffer &rhs) const
{
    bool success = true;
    EXPECT_TRUE((lhs.get() != nullptr) == (rhs.get() != nullptr)) << (success = false, "");
    if (!success) {
        return false;
    }
    if (lhs.get() == nullptr)
        return true;
    EXPECT_EQ(lhs->getDataLen(), rhs->getDataLen()) << (success = false, "");
    if (!success) {
        return false;
    }
    EXPECT_TRUE(vespalib::memcmp_safe(lhs->getData(), rhs->getData(), lhs->getDataLen()) == 0) << (success = false, "");
    return success;
}

bool
MemAttr::operator==(const MemAttr &rhs) const
{
    bool success = true;
    EXPECT_TRUE(bufEqual(_datWriter.buf(), rhs._datWriter.buf())) << (success = false, "");
    if (!success) {
        return false;
    }
    EXPECT_TRUE(bufEqual(_idxWriter.buf(), rhs._idxWriter.buf())) << (success = false, "");
    if (!success) {
        return false;
    }
    EXPECT_TRUE(bufEqual(_weightWriter.buf(), rhs._weightWriter.buf())) << (success = false, "");
    if (!success) {
        return false;
    }
    EXPECT_TRUE(bufEqual(_udatWriter.buf(), rhs._udatWriter.buf())) << (success = false, "");
    return success;
}


void
EnumeratedSaveTest::addDocs(const AttributePtr &v, size_t sz)
{
    if (sz) {
        AttributeVector::DocId docId;
        for(size_t i(0); i< sz; i++) {
            EXPECT_TRUE( v->addDoc(docId) );
        }
        EXPECT_TRUE( docId+1 == sz );
        EXPECT_TRUE( v->getNumDocs() == sz );
        v->commit(CommitParam::UpdateStats::FORCE);
    }
}


template <>
void
EnumeratedSaveTest::populate(IntegerAttribute &v, unsigned seed,
                             BasicType bt)
{
    vespalib::Rand48 rnd;
    IntegerAttribute::largeint_t mask(std::numeric_limits
                                      <IntegerAttribute::largeint_t>::max());
    switch (bt.type()) {
    case BasicType::INT8:
        mask = 0x7f;
        break;
    case BasicType::INT16:
        mask = 0x7fff;
        break;
    default:
        ;
    }
    rnd.srand48(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (i == 9) {
            continue;
        }
        if (i == 7) {
            if (v.hasMultiValue()) {
                v.append(i, -42, 27);
                v.append(i, -43, 14);
                v.append(i, -42, -3);
            } else {
                EXPECT_TRUE( v.update(i, -42) );
            }
            v.commit();
            continue;
        }
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = (rand() % 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rnd.lrand48() & mask, weight) );
            }
            v.commit();
            if (!v.hasWeightedSetType()) {
                EXPECT_EQ(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
                ASSERT_TRUE(static_cast<uint32_t>(v.getValueCount(i)) == i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, rnd.lrand48() & mask) );
        }
    }
    v.commit();
}


template <>
void
EnumeratedSaveTest::populate(FloatingPointAttribute &v, unsigned seed,
                             BasicType bt)
{
    (void) bt;
    vespalib::Rand48 rnd;
    rnd.srand48(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (i == 9) {
            continue;
        }
        if (i == 7) {
            if (v.hasMultiValue()) {
                v.append(i, -42.0, 27);
                v.append(i, -43.0, 14);
                v.append(i, -42.0, -3);
            } else {
                EXPECT_TRUE( v.update(i, -42.0) );
            }
            v.commit();
            continue;
        }
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = (rand() % 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rnd.lrand48(), weight) );
            }
            v.commit();
            if (!v.hasWeightedSetType()) {
                EXPECT_EQ(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
                ASSERT_TRUE(static_cast<uint32_t>(v.getValueCount(i)) == i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, rnd.lrand48()) );
        }
    }
    v.commit();
}


template <>
void
EnumeratedSaveTest::populate(StringAttribute &v, unsigned seed,
                             BasicType bt)
{
    (void) bt;
    RandomGenerator rnd(seed);
    int weight = 1;
    for(size_t i(0), m(v.getNumDocs()); i < m; i++) {
        v.clearDoc(i);
        if (i == 9) {
            continue;
        }
        if (i == 7) {
            if (v.hasMultiValue()) {
                v.append(i, "foo", 27);
                v.append(i, "bar", 14);
                v.append(i, "foO", -3);
            } else {
                EXPECT_TRUE( v.update(i, "foo") );
            }
            v.commit();
            continue;
        }
        if (v.hasMultiValue()) {
            if (v.hasWeightedSetType()) {
                weight = rnd.rand(0, 256) - 128;
            }
            for (size_t j(0); j <= i; j++) {
                EXPECT_TRUE( v.append(i, rnd.getRandomString(2, 50), weight) );
            }
            v.commit();
            if (!v.hasWeightedSetType()) {
                EXPECT_EQ(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, rnd.getRandomString(2, 50)) );
        }
    }
    v.commit();
}

void EnumeratedSaveTest::populate(AttributePtr &v, unsigned seed, BasicType bt) {
    typify_invoke<1, TypifyVectorType, CallPopulate>(bt, *this, v, seed, bt);
}

namespace
{

template <typename T>
inline bool
equalsHelper(const T &lhs, const T &rhs)
{
    return lhs == rhs;
}

template <>
inline bool
equalsHelper<double>(const double &lhs, const double &rhs)
{
    if (std::isnan(lhs))
        return std::isnan(rhs);
    if (std::isnan(rhs))
        return false;
    return lhs == rhs;
}

constexpr auto zero_flush_duration = std::chrono::steady_clock::duration::zero();

}

template <typename VectorType, typename BufferType>
void
EnumeratedSaveTest::compare(VectorType &a, VectorType &b)
{
    EXPECT_EQ(a.getNumDocs(), b.getNumDocs());
    ASSERT_TRUE(a.getNumDocs() == b.getNumDocs());
    // EXPECT_EQ(a.getMaxValueCount(), b.getMaxValueCount());
    EXPECT_EQ(a.getCommittedDocIdLimit(), b.getCommittedDocIdLimit());
    uint32_t asz(a.getMaxValueCount());
    uint32_t bsz(b.getMaxValueCount());
    BufferType *av = new BufferType[asz];
    BufferType *bv = new BufferType[bsz];

    for (size_t i(0), m(a.getNumDocs()); i < m; i++) {
        ASSERT_TRUE(asz >= static_cast<uint32_t>(a.getValueCount(i)));
        ASSERT_TRUE(bsz >= static_cast<uint32_t>(b.getValueCount(i)));
        EXPECT_EQ(a.getValueCount(i), b.getValueCount(i));
        ASSERT_TRUE(a.getValueCount(i) == b.getValueCount(i));
        EXPECT_EQ(static_cast<const AttributeVector &>(a).get(i, av, asz), static_cast<uint32_t>(a.getValueCount(i)));
        EXPECT_EQ(static_cast<const AttributeVector &>(b).get(i, bv, bsz), static_cast<uint32_t>(b.getValueCount(i)));
        for(size_t j(0), k(std::min(a.getValueCount(i), b.getValueCount(i)));
            j < k; j++) {
            EXPECT_TRUE(equalsHelper(av[j], bv[j]));
        }
    }
    delete [] bv;
    delete [] av;
}

void
EnumeratedSaveTest::compare(AttributePtr& a, AttributePtr& b) {
    typify_invoke<1, TypifyVectorType, CallCompare>(a->getConfig().basicType(), *this, a, b);
}

template <typename VectorType>
VectorType &
EnumeratedSaveTest::as(AttributePtr &v)
{
    VectorType *res = dynamic_cast<VectorType *>(v.get());
    assert(res != nullptr);
    return *res;
}


IntegerAttribute &
EnumeratedSaveTest::asInt(AttributePtr &v)
{
    return as<IntegerAttribute>(v);
}


StringAttribute &
EnumeratedSaveTest::asString(AttributePtr &v)
{
    return as<StringAttribute>(v);
}


FloatingPointAttribute &
EnumeratedSaveTest::asFloat(AttributePtr &v)
{
    return as<FloatingPointAttribute>(v);
}


void
EnumeratedSaveTest::buildTermQuery(std::vector<char> &buffer,
                                   const std::string &index,
                                   const std::string &term,
                                   bool prefix)
{
    uint32_t indexLen = index.size();
    uint32_t termLen = term.size();
    uint32_t queryPacketSize = 1 + 2 * 4 + indexLen + termLen;
    uint32_t p = 0;
    buffer.resize(queryPacketSize);
    buffer[p++] = prefix ? ParseItem::ITEM_PREFIXTERM : ParseItem::ITEM_TERM;
    p += vespalib::compress::Integer::compressPositive(indexLen, &buffer[p]);
    memcpy(&buffer[p], index.c_str(), indexLen);
    p += indexLen;
    p += vespalib::compress::Integer::compressPositive(termLen, &buffer[p]);
    memcpy(&buffer[p], term.c_str(), termLen);
    p += termLen;
    buffer.resize(p);
}


template <typename V, typename T>
SearchContextPtr
EnumeratedSaveTest::getSearch(const V &vec, const T &term, bool prefix)
{
    std::vector<char> query;
    vespalib::asciistream ss;
    ss << term;
    buildTermQuery(query, vec.getName(), ss.str(), prefix);

    return (static_cast<const AttributeVector &>(vec)).
        getSearch(std::string_view(query.data(), query.size()),
                  SearchContextParams());
}


template <>
SearchContextPtr
EnumeratedSaveTest::getSearch<IntegerAttribute>(const IntegerAttribute &v)
{
    return getSearch<IntegerAttribute>(v, "[-42;-42]", false);
}

template <>
SearchContextPtr
EnumeratedSaveTest::getSearch<FloatingPointAttribute>(const FloatingPointAttribute &v)
{
    return getSearch<FloatingPointAttribute>(v, "[-42.0;-42.0]", false);
}

template <>
SearchContextPtr
EnumeratedSaveTest::getSearch<StringAttribute>(const StringAttribute &v)
{
    return getSearch<StringAttribute, const std::string &>
        (v, "foo", false);
}

SearchContextPtr
EnumeratedSaveTest::getSearch(AttributePtr& v) {
   return typify_invoke<1, TypifyVectorType, CallGetSearch>(v->getConfig().basicType(), *this, v);
}

MemAttr::SP
EnumeratedSaveTest::saveMem(AttributeVector &v)
{
    MemAttr::SP res(new MemAttr);
    EXPECT_TRUE(v.save(*res, v.getBaseFileName()));
    return res;
}

void
EnumeratedSaveTest::saveMemDuringCompaction(AttributeVector &v)
{
    MemAttr::SP res(new MemAttr);
    auto *enum_store_base = v.getEnumStoreBase();
    if (enum_store_base != nullptr) {
        auto saver = v.onInitSave(v.getBaseFileName());
        // Simulate compaction
        enum_store_base->inc_compaction_count();
        auto save_result = saver->save(*res);
        EXPECT_EQ(!v.hasMultiValue(), save_result);
    }
}

void
EnumeratedSaveTest::checkMem(AttributeVector &v, const MemAttr &e, const std::string& label)
{
    SCOPED_TRACE(label);
    auto *esb = v.getEnumStoreBase();
    if (esb == nullptr || esb->get_dictionary().get_has_btree_dictionary()) {
        MemAttr m;
        EXPECT_TRUE(v.save(m, v.getBaseFileName()));
        ASSERT_TRUE(m == e);
    } else {
        // Save without sorting unique values, load into temporary
        // attribute vector with sorted dictionary and save again
        // to verify data.
        search::AttributeMemorySaveTarget ms;
        search::TuneFileAttributes tune;
        search::index::DummyFileHeaderContext fileHeaderContext;
        EXPECT_TRUE(v.save(ms, make_attr_name("convert")));
        EXPECT_TRUE(ms.writeToFile(tune, fileHeaderContext));
        EXPECT_NE(0u, ms.size_on_disk());
        EXPECT_NE(zero_flush_duration, v.last_flush_duration());
        auto cfg = v.getConfig();
        cfg.set_dictionary_config(search::DictionaryConfig(search::DictionaryConfig::Type::BTREE));
        auto v2 = AttributeFactory::createAttribute(make_attr_name("convert"), cfg);
        EXPECT_TRUE(v2->load());
        EXPECT_NE(0u, v2->size_on_disk());
        EXPECT_NE(zero_flush_duration, v2->last_flush_duration());
        MemAttr m2;
        EXPECT_TRUE(v2->save(m2, v.getBaseFileName()));
        ASSERT_TRUE(m2 == e);
        auto v3 = AttributeFactory::createAttribute(make_attr_name("convert"), v.getConfig());
        EXPECT_TRUE(v3->load());
        EXPECT_NE(0u, v3->size_on_disk());
        EXPECT_NE(zero_flush_duration, v3->last_flush_duration());
    }
}


MemAttr::SP
EnumeratedSaveTest::saveBoth(AttributePtr v)
{
    EXPECT_TRUE(v->save());
    EXPECT_NE(0u, v->size_on_disk());
    EXPECT_NE(zero_flush_duration, v->last_flush_duration());
    std::string basename = v->getBaseFileName();
    AttributePtr v2 = make(v->getConfig(), basename, true);
    EXPECT_TRUE(v2->load());
    EXPECT_EQ(v->size_on_disk(), v2->size_on_disk());
    EXPECT_NE(zero_flush_duration, v2->last_flush_duration());
    EXPECT_TRUE(v2->save(basename + "_e"));

    search::AttributeMemorySaveTarget ms;
    search::TuneFileAttributes tune;
    search::index::DummyFileHeaderContext fileHeaderContext;
    EXPECT_TRUE(v2->save(ms, basename + "_ee"));
    EXPECT_TRUE(ms.writeToFile(tune, fileHeaderContext));

    return saveMem(*v2);
}


EnumeratedSaveTest::AttributePtr
EnumeratedSaveTest::make(Config cfg, const std::string &pref, bool fastSearch)
{
    cfg.setFastSearch(fastSearch);
    AttributePtr v = AttributeFactory::createAttribute(pref, cfg);
    return v;
}


void
EnumeratedSaveTest::load(AttributePtr v, const std::string &name)
{
    v->setBaseFileName(name);
    EXPECT_TRUE(v->load());
    EXPECT_NE(0u, v->size_on_disk());
    EXPECT_NE(zero_flush_duration, v->last_flush_duration());
}

EnumeratedSaveTest::AttributePtr
EnumeratedSaveTest::checkLoad(Config cfg, const std::string &name,
                              AttributePtr ev, const std::string& label)
{
    SCOPED_TRACE(label);
    AttributePtr v = AttributeFactory::createAttribute(make_attr_name(name), cfg);
    EXPECT_TRUE(v->load());
    EXPECT_NE(0u, v->size_on_disk());
    EXPECT_NE(zero_flush_duration, v->last_flush_duration());
    compare(v, ev);;
    return v;
}


void
EnumeratedSaveTest::testReload(AttributePtr v0,
                               AttributePtr v1,
                               AttributePtr v2,
                               MemAttr::SP mv0,
                               MemAttr::SP mv1,
                               MemAttr::SP mv2,
                               MemAttr::SP emv0,
                               MemAttr::SP emv1,
                               MemAttr::SP emv2,
                               Config cfg,
                               const std::string &pref,
                               bool fastSearch,
                               search::DictionaryConfig dictionary_config)
{
    std::ostringstream os;
    os << "testReload fs=" << std::boolalpha << fastSearch << ", dictionary_config=" << dictionary_config;
    SCOPED_TRACE(os.str());
    bool flagAttr =
        cfg.collectionType() == CollectionType::ARRAY &&
        cfg.basicType() == BasicType::INT8 &&
        fastSearch;
    bool supportsEnumerated = (fastSearch ||
                               cfg.basicType() == BasicType::STRING) &&
                              !flagAttr;


    Config check_cfg(cfg);
    check_cfg.setFastSearch(fastSearch);
    check_cfg.set_dictionary_config(dictionary_config);
    checkLoad(check_cfg, pref + "0", v0, "0");
    checkLoad(check_cfg, pref + "1", v1, "1");
    checkLoad(check_cfg, pref + "2", v2, "2");

    AttributePtr v;
    v = checkLoad(check_cfg, pref + "0", v0, "2nd 0");
    checkMem(*v, supportsEnumerated ? *emv0 : *mv0, "0");
    v = checkLoad(check_cfg, pref + "1", v1, "2nd 1");
    checkMem(*v, supportsEnumerated ? *emv1 : *mv1, "1");
    v = checkLoad(check_cfg, pref + "2", v2, "2nd 2");
    checkMem(*v, supportsEnumerated ? *emv2 : *mv2, "2");

    checkLoad(check_cfg, pref + "0_e", v0, "0_e");
    checkLoad(check_cfg, pref + "1_e", v1, "1_e");
    checkLoad(check_cfg, pref + "2_e", v2, "2_e");

    v = checkLoad(check_cfg, pref + "0_e", v0, "2nd 0_e");
    checkMem(*v, supportsEnumerated ? *emv0 : *mv0, "2nd 0");
    v = checkLoad(check_cfg, pref + "1_e", v1, "2nd 1_e");
    checkMem(*v, supportsEnumerated ? *emv1 : *mv1, "2nd 1");
    v = checkLoad(check_cfg, pref + "2_e", v2, "2nd 2_e");
    checkMem(*v, supportsEnumerated ? *emv2 : *mv2, "2nd 2");

    saveMemDuringCompaction(*v);

    TermFieldMatchData md;
    SearchContextPtr sc = getSearch(v);
    sc->fetchPostings(search::queryeval::ExecuteInfo::FULL, true);
    SearchBasePtr sb = sc->createIterator(&md, true);
    sb->initFullRange();
    sb->seek(1u);
    EXPECT_EQ(7u, sb->getDocId());
    sb->unpack(7u);
    EXPECT_TRUE(md.has_ranking_data(7u));
    if (v->getCollectionType() == CollectionType::SINGLE || flagAttr) {
        EXPECT_EQ(md.getWeight(), 1);
    } else if (v->getCollectionType() == CollectionType::ARRAY) {
        EXPECT_EQ(md.getWeight(), 2);
    } else {
        if (cfg.basicType() == BasicType::STRING) {
            EXPECT_EQ(md.getWeight(), 24);
        } else {
            EXPECT_EQ(md.getWeight(), -3);
        }
    }
}


void
EnumeratedSaveTest::test(BasicType bt, CollectionType ct,
                         const std::string &pref)
{
    Config cfg(bt, ct);
    AttributePtr v0 = AttributeFactory::createAttribute(make_attr_name(pref) + "0", cfg);
    AttributePtr v1 = AttributeFactory::createAttribute(make_attr_name(pref) + "1", cfg);
    AttributePtr v2 = AttributeFactory::createAttribute(make_attr_name(pref) + "2", cfg);

    addDocs(v0, 0);
    addDocs(v1, 10);
    addDocs(v2, 30);

    populate(v0, 0, bt);
    populate(v1, 10, bt);
    populate(v2, 30, bt);

    MemAttr::SP mv0 = saveMem(*v0);
    MemAttr::SP mv1 = saveMem(*v1);
    MemAttr::SP mv2 = saveMem(*v2);

    MemAttr::SP emv0 = saveBoth(v0);
    MemAttr::SP emv1 = saveBoth(v1);
    MemAttr::SP emv2 = saveBoth(v2);

    Config check_cfg(cfg);
    check_cfg.setFastSearch(true);
    checkLoad(check_cfg, pref + "0_ee", v0, "0_ee");
    checkLoad(check_cfg, pref + "1_ee", v1, "1_ee");
    checkLoad(check_cfg, pref + "2_ee", v2, "2_ee");

    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, false,
               search::DictionaryConfig(search::DictionaryConfig::Type::BTREE));
    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, true,
               search::DictionaryConfig(search::DictionaryConfig::Type::BTREE));
    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, false,
               search::DictionaryConfig(search::DictionaryConfig::Type::BTREE_AND_HASH));
    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, true,
               search::DictionaryConfig(search::DictionaryConfig::Type::BTREE_AND_HASH));
    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, false,
               search::DictionaryConfig(search::DictionaryConfig::Type::HASH));
    testReload(v0, v1, v2, mv0, mv1, mv2, emv0, emv1, emv2, cfg, pref, true,
               search::DictionaryConfig(search::DictionaryConfig::Type::HASH));
}

TEST_P(EnumeratedSaveTest, enumerated_save) {
    const auto& param = GetParam();
    auto bt = std::get<0>(param);
    auto ct = std::get<1>(param);
    this->test(bt, ct, std::string(bt.asString()) + "_" + ct.asString());
}

auto test_values = testing::Combine(testing::Values(BasicType::INT8, BasicType::INT16, BasicType::INT32,
                                                    BasicType::INT64, BasicType::FLOAT, BasicType::DOUBLE,
                                                    BasicType::STRING),
                                    testing::Values(CollectionType::SINGLE, CollectionType::ARRAY,
                                                    CollectionType::WSET));

INSTANTIATE_TEST_SUITE_P(EnumeratedSaveMultiTest, EnumeratedSaveTest, test_values, param_as_string);

GTEST_MAIN_RUN_ALL_TESTS()
