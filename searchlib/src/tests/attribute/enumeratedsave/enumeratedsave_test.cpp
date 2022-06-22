// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <limits>
#include <cmath>

using search::AttributeFactory;
using search::AttributeVector;
using search::AttributeMemoryFileBufferWriter;
using search::BufferWriter;
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

using SearchContextPtr = std::unique_ptr<SearchContext>;
typedef std::unique_ptr<search::queryeval::SearchIterator> SearchBasePtr;


class MemAttrFileWriter : public IAttributeFileWriter
{
private:
    Buffer _buf;

public:
    MemAttrFileWriter()
        : _buf()
    {
    }

    virtual Buffer allocBuf(size_t size) override {
        return std::make_unique<BufferBuf>(size, search::FileSettings::DIRECTIO_ALIGNMENT);
    }

    virtual void writeBuf(Buffer buf_in) override {
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
    typedef std::shared_ptr<MemAttr> SP;

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

    bool setup_writer(const vespalib::string& file_suffix,
                      const vespalib::string& desc) override {
        (void) file_suffix;
        (void) desc;
        abort();
    }
    IAttributeFileWriter& get_writer(const vespalib::string& file_suffix) override {
        (void) file_suffix;
        abort();
    }

    bool bufEqual(const Buffer &lhs, const Buffer &rhs) const;
 
    bool operator==(const MemAttr &rhs) const;
};

MemAttr::MemAttr() = default;
MemAttr::~MemAttr() = default;

class EnumeratedSaveTest
{
private:
    typedef AttributeVector::SP AttributePtr;

    template <typename VectorType>
    VectorType & as(AttributePtr &v);
    IntegerAttribute & asInt(AttributePtr &v);
    StringAttribute & asString(AttributePtr &v);
    FloatingPointAttribute & asFloat(AttributePtr &v);
    void addDocs(const AttributePtr &v, size_t sz);

    template <typename VectorType>
    void populate(VectorType &v, unsigned seed, BasicType bt);

    template <typename VectorType, typename BufferType>
    void compare(VectorType &a, VectorType &b);

    void buildTermQuery(std::vector<char> & buffer, const vespalib::string & index,
                        const vespalib::string & term, bool prefix);

    template <typename V, typename T>
    SearchContextPtr getSearch(const V & vec, const T & term, bool prefix);

    template <typename V>
    SearchContextPtr getSearch(const V & vec);

    MemAttr::SP saveMem(AttributeVector &v);
    void saveMemDuringCompaction(AttributeVector &v);
    void checkMem(AttributeVector &v, const MemAttr &e);
    MemAttr::SP saveBoth(AttributePtr v);
    AttributePtr make(Config cfg, const vespalib::string &pref, bool fastSearch = false);
    void load(AttributePtr v, const vespalib::string &name);

    template <typename VectorType, typename BufferType>
    AttributePtr checkLoad(Config cfg, const vespalib::string &name, AttributePtr ev);

    template <typename VectorType, typename BufferType>
    void testReload(AttributePtr v0, AttributePtr v1, AttributePtr v2,
                    MemAttr::SP mv0, MemAttr::SP mv1, MemAttr::SP mv2,
                    MemAttr::SP emv0, MemAttr::SP emv1, MemAttr::SP emv2,
                    Config cfg, const vespalib::string &pref, bool fastSearch, search::DictionaryConfig dictionary_config);

public:
    template <typename VectorType, typename BufferType>
    void test(BasicType bt, CollectionType ct, const vespalib::string &pref);

};


bool
MemAttr::bufEqual(const Buffer &lhs, const Buffer &rhs) const
{
    if (!EXPECT_TRUE((lhs.get() != NULL) == (rhs.get() != NULL)))
        return false;
    if (lhs.get() == NULL)
        return true;
    if (!EXPECT_TRUE(lhs->getDataLen() == rhs->getDataLen()))
        return false;
    if (!EXPECT_TRUE(vespalib::memcmp_safe(lhs->getData(), rhs->getData(),
                                           lhs->getDataLen()) == 0))
        return false;
    return true;
}
 
bool
MemAttr::operator==(const MemAttr &rhs) const
{
    if (!EXPECT_TRUE(bufEqual(_datWriter.buf(), rhs._datWriter.buf())))
        return false;
    if (!EXPECT_TRUE(bufEqual(_idxWriter.buf(), rhs._idxWriter.buf())))
        return false;
    if (!EXPECT_TRUE(bufEqual(_weightWriter.buf(), rhs._weightWriter.buf())))
        return false;
    if (!EXPECT_TRUE(bufEqual(_udatWriter.buf(), rhs._udatWriter.buf())))
        return false;
    return true;
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
        v->commit(true);
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
        if (i == 9)
            continue;
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
                EXPECT_EQUAL(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
                ASSERT_TRUE(static_cast<uint32_t>(v.getValueCount(i)) ==
                            i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, lrand48() & mask) );
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
        if (i == 9)
            continue;
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
                EXPECT_EQUAL(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
                ASSERT_TRUE(static_cast<uint32_t>(v.getValueCount(i)) ==
                            i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, lrand48()) );
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
        if (i == 9)
            continue;
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
                EXPECT_EQUAL(static_cast<uint32_t>(v.getValueCount(i)), i + 1);
            }
        } else {
            EXPECT_TRUE( v.update(i, rnd.getRandomString(2, 50)) );
        }
    }
    v.commit();
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

}

template <typename VectorType, typename BufferType>
void
EnumeratedSaveTest::compare(VectorType &a, VectorType &b)
{
    EXPECT_EQUAL(a.getNumDocs(), b.getNumDocs());
    ASSERT_TRUE(a.getNumDocs() == b.getNumDocs());
    // EXPECT_EQUAL(a.getMaxValueCount(), b.getMaxValueCount());
    EXPECT_EQUAL(a.getCommittedDocIdLimit(), b.getCommittedDocIdLimit());
    uint32_t asz(a.getMaxValueCount());
    uint32_t bsz(b.getMaxValueCount());
    BufferType *av = new BufferType[asz];
    BufferType *bv = new BufferType[bsz];

    for (size_t i(0), m(a.getNumDocs()); i < m; i++) {
        ASSERT_TRUE(asz >= static_cast<uint32_t>(a.getValueCount(i)));
        ASSERT_TRUE(bsz >= static_cast<uint32_t>(b.getValueCount(i)));
        EXPECT_EQUAL(a.getValueCount(i), b.getValueCount(i));
        ASSERT_TRUE(a.getValueCount(i) == b.getValueCount(i));
        EXPECT_EQUAL(static_cast<const AttributeVector &>(a).get(i, av, asz),
                     static_cast<uint32_t>(a.getValueCount(i)));
        EXPECT_EQUAL(static_cast<const AttributeVector &>(b).get(i, bv, bsz),
                     static_cast<uint32_t>(b.getValueCount(i)));
        for(size_t j(0), k(std::min(a.getValueCount(i), b.getValueCount(i)));
            j < k; j++) {
            EXPECT_TRUE(equalsHelper(av[j], bv[j]));
        }
    }
    delete [] bv;
    delete [] av;
}


template <typename VectorType>
VectorType &
EnumeratedSaveTest::as(AttributePtr &v)
{
    VectorType *res = dynamic_cast<VectorType *>(v.get());
    assert(res != NULL);
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
                                   const vespalib::string &index,
                                   const vespalib::string &term,
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
        getSearch(vespalib::stringref(query.data(), query.size()),
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
    return getSearch<StringAttribute, const vespalib::string &>
        (v, "foo", false);
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
        EXPECT_EQUAL(!v.hasMultiValue(), save_result);
    }
}

void
EnumeratedSaveTest::checkMem(AttributeVector &v, const MemAttr &e)
{
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
        EXPECT_TRUE(v.save(ms, "convert"));
        EXPECT_TRUE(ms.writeToFile(tune, fileHeaderContext));
        auto cfg = v.getConfig();
        cfg.set_dictionary_config(search::DictionaryConfig(search::DictionaryConfig::Type::BTREE));
        auto v2 = AttributeFactory::createAttribute("convert", cfg);
        EXPECT_TRUE(v2->load());
        MemAttr m2;
        EXPECT_TRUE(v2->save(m2, v.getBaseFileName()));
        ASSERT_TRUE(m2 == e);
        auto v3 = AttributeFactory::createAttribute("convert", v.getConfig());
        EXPECT_TRUE(v3->load());
    }
}


MemAttr::SP
EnumeratedSaveTest::saveBoth(AttributePtr v)
{
    EXPECT_TRUE(v->save());
    vespalib::string basename = v->getBaseFileName();
    AttributePtr v2 = make(v->getConfig(), basename, true);
    EXPECT_TRUE(v2->load());
    EXPECT_TRUE(v2->save(basename + "_e"));

    search::AttributeMemorySaveTarget ms;
    search::TuneFileAttributes tune;
    search::index::DummyFileHeaderContext fileHeaderContext;
    EXPECT_TRUE(v2->save(ms, basename + "_ee"));
    EXPECT_TRUE(ms.writeToFile(tune, fileHeaderContext));

    return saveMem(*v2);
}


EnumeratedSaveTest::AttributePtr
EnumeratedSaveTest::make(Config cfg, const vespalib::string &pref, bool fastSearch)
{
    cfg.setFastSearch(fastSearch);
    AttributePtr v = AttributeFactory::createAttribute(pref, cfg);
    return v;
}


void
EnumeratedSaveTest::load(AttributePtr v, const vespalib::string &name)
{
    v->setBaseFileName(name);
    EXPECT_TRUE(v->load());
}

template <typename VectorType, typename BufferType>
EnumeratedSaveTest::AttributePtr
EnumeratedSaveTest::checkLoad(Config cfg, const vespalib::string &name,
                              AttributePtr ev)
{
    AttributePtr v = AttributeFactory::createAttribute(name, cfg);
    EXPECT_TRUE(v->load());
    compare<VectorType, BufferType>(as<VectorType>(v), as<VectorType>(ev));
    return v;
}


template <typename VectorType, typename BufferType>
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
                               const vespalib::string &pref,
                               bool fastSearch,
                               search::DictionaryConfig dictionary_config)
{
    // typedef AttributePtr AVP;

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
    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "0", v0)));
    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "1", v1)));
    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "2", v2)));

    AttributePtr v;
    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "0", v0)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv0 : *mv0));
    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "1", v1)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv1 : *mv1));
    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "2", v2)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv2 : *mv2));

    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "0_e", v0)));
    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "1_e", v1)));
    TEST_DO((checkLoad<VectorType, BufferType>(check_cfg, pref + "2_e", v2)));

    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "0_e", v0)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv0 : *mv0));
    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "1_e", v1)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv1 : *mv1));
    TEST_DO((v = checkLoad<VectorType, BufferType>(check_cfg, pref + "2_e", v2)));
    TEST_DO(checkMem(*v, supportsEnumerated ? *emv2 : *mv2));

    saveMemDuringCompaction(*v);

    TermFieldMatchData md;
    SearchContextPtr sc = getSearch<VectorType>(as<VectorType>(v));
    sc->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    SearchBasePtr sb = sc->createIterator(&md, true);
    sb->initFullRange();
    sb->seek(1u);
    EXPECT_EQUAL(7u, sb->getDocId());
    sb->unpack(7u);
    EXPECT_EQUAL(md.getDocId(), 7u);
    if (v->getCollectionType() == CollectionType::SINGLE ||
        flagAttr) {
        EXPECT_EQUAL(md.getWeight(), 1);
    } else if (v->getCollectionType() == CollectionType::ARRAY) {
        EXPECT_EQUAL(md.getWeight(), 2);
    } else {
        if (cfg.basicType() == BasicType::STRING) {
            EXPECT_EQUAL(md.getWeight(), 24);
        } else {
            EXPECT_EQUAL(md.getWeight(), -3);
        }
    }
}


template <typename VectorType, typename BufferType>
void
EnumeratedSaveTest::test(BasicType bt, CollectionType ct,
                         const vespalib::string &pref)
{
    Config cfg(bt, ct);
    AttributePtr v0 = AttributeFactory::createAttribute(pref + "0", cfg);
    AttributePtr v1 = AttributeFactory::createAttribute(pref + "1", cfg);
    AttributePtr v2 = AttributeFactory::createAttribute(pref + "2", cfg);

    addDocs(v0, 0);
    addDocs(v1, 10);
    addDocs(v2, 30);

    populate(as<VectorType>(v0), 0, bt);
    populate(as<VectorType>(v1), 10, bt);
    populate(as<VectorType>(v2), 30, bt);

    MemAttr::SP mv0 = saveMem(*v0);
    MemAttr::SP mv1 = saveMem(*v1);
    MemAttr::SP mv2 = saveMem(*v2);
    
    MemAttr::SP emv0 = saveBoth(v0);
    MemAttr::SP emv1 = saveBoth(v1);
    MemAttr::SP emv2 = saveBoth(v2);

    Config check_cfg(cfg);
    check_cfg.setFastSearch(true);
    checkLoad<VectorType, BufferType>(check_cfg, pref + "0_ee", v0);
    checkLoad<VectorType, BufferType>(check_cfg, pref + "1_ee", v1);
    checkLoad<VectorType, BufferType>(check_cfg, pref + "2_ee", v2);

    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, false, search::DictionaryConfig(search::DictionaryConfig::Type::BTREE))));
    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, true, search::DictionaryConfig(search::DictionaryConfig::Type::BTREE))));
    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, false, search::DictionaryConfig(search::DictionaryConfig::Type::BTREE_AND_HASH))));
    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, true, search::DictionaryConfig(search::DictionaryConfig::Type::BTREE_AND_HASH))));
    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, false, search::DictionaryConfig(search::DictionaryConfig::Type::HASH))));
    TEST_DO((testReload<VectorType, BufferType>(v0, v1, v2,
                                                mv0, mv1, mv2,
                                                emv0, emv1, emv2,
                                                cfg, pref, true, search::DictionaryConfig(search::DictionaryConfig::Type::HASH))));
}

TEST_F("Test enumerated save with single value int8", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT8,
                                      CollectionType::SINGLE,
                                      "int8_sv");
}

TEST_F("Test enumerated save with array value int8", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT8,
                                      CollectionType::ARRAY,
                                      "int8_a");
}

TEST_F("Test enumerated save with weighted set value int8",
       EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::WeightedInt>(BasicType::INT8,
                                       CollectionType::WSET,
                                       "int8_ws");
}

TEST_F("Test enumerated save with single value int16", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT16,
                                      CollectionType::SINGLE,
                                      "int16_sv");
}

TEST_F("Test enumerated save with array value int16", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT16,
                                      CollectionType::ARRAY,
                                      "int16_a");
}

TEST_F("Test enumerated save with weighted set value int16",
       EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::WeightedInt>(BasicType::INT16,
                                       CollectionType::WSET,
                                       "int16_ws");
}

TEST_F("Test enumerated save with single value int32", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT32,
                                      CollectionType::SINGLE,
                                      "int32_sv");
}

TEST_F("Test enumerated save with array value int32", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT32,
                                      CollectionType::ARRAY,
                                      "int32_a");
}

TEST_F("Test enumerated save with weighted set value int32",
       EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::WeightedInt>(BasicType::INT32,
                                       CollectionType::WSET,
                                       "int32_ws");
}

TEST_F("Test enumerated save with single value int64", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT64,
                                      CollectionType::SINGLE,
                                      "int64_sv");
}

TEST_F("Test enumerated save with array value int64", EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::largeint_t>(BasicType::INT64,
                                      CollectionType::ARRAY,
                                      "int64_a");
}

TEST_F("Test enumerated save with weighted set value int64",
       EnumeratedSaveTest)
{
    f.template test<IntegerAttribute,
        IntegerAttribute::WeightedInt>(BasicType::INT64,
                                       CollectionType::WSET,
                                       "int64_ws");
}

TEST_F("Test enumerated save with single value float", EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::FLOAT,
                CollectionType::SINGLE,
                "float_sv");
}

TEST_F("Test enumerated save with array value float", EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::FLOAT,
                CollectionType::ARRAY,
                "float_a");
}

TEST_F("Test enumerated save with weighted set value float",
       EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        FloatingPointAttribute::WeightedFloat>(
                BasicType::FLOAT,
                CollectionType::WSET,
                "float_ws");
}


TEST_F("Test enumerated save with single value double", EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::DOUBLE,
                CollectionType::SINGLE,
                "double_sv");
}

TEST_F("Test enumerated save with array value double", EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        double>(BasicType::DOUBLE,
                CollectionType::ARRAY,
                "double_a");
}

TEST_F("Test enumerated save with weighted set value double",
       EnumeratedSaveTest)
{
    f.template test<FloatingPointAttribute,
        FloatingPointAttribute::WeightedFloat>(
                BasicType::DOUBLE,
                CollectionType::WSET,
                "double_ws");
}


TEST_F("Test enumerated save with single value string", EnumeratedSaveTest)
{
    f.template test<StringAttribute,
        vespalib::string>(BasicType::STRING,
                          CollectionType::SINGLE,
                          "str_sv");
}

TEST_F("Test enumerated save with array value string", EnumeratedSaveTest)
{
    f.template test<StringAttribute,
        vespalib::string>(BasicType::STRING,
                          CollectionType::ARRAY,
                          "str_a");
}

TEST_F("Test enumerated save with weighted set value string",
       EnumeratedSaveTest)
{
    f.template test<StringAttribute,
        StringAttribute::WeightedString>(
                BasicType::STRING,
                CollectionType::WSET,
                "str_ws");
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
