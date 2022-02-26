// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vector>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("expglomb_test");

using search::bitcompression::DecodeContext64;
using search::bitcompression::DecodeContext64Base;
using search::bitcompression::EncodeContext64;
using search::bitcompression::EncodeContext64Base;

template <bool bigEndian>
class DecodeContext : public DecodeContext64<bigEndian>
{
public:
    using Parent = DecodeContext64<bigEndian>;
    using Parent::defineReadOffset;
    using EC = EncodeContext64<bigEndian>;

    DecodeContext(const uint64_t *compr, int bitOffset)
        : DecodeContext64<bigEndian>(compr, bitOffset)
    {
        this->defineReadOffset(0);
    }
};


class IDecodeFunc
{
public:
    virtual uint64_t decode() = 0;
    virtual void skip() = 0;
    virtual uint64_t decodeSmall() = 0;
    virtual uint64_t decodeSmallApply() = 0;
    virtual void skipSmall() = 0;

    virtual ~IDecodeFunc() = default;

};


/*
 * Exp golomb decode functions getting kValue from a variable, i.e.
 * compiler is not allowed to generate shift instructions with immediate values.
 * Expressions involving kValue are not constant and can thus not be
 * folded to constant values.
 */
template <bool bigEndian>
class DecodeExpGolombVarK : public IDecodeFunc
{
public:
    using DCB = DecodeContext64Base;
    using DC = DecodeContext<bigEndian>;
    using EC = typename DC::EC;

    DCB &_dc;
    int _kValue;

    DecodeExpGolombVarK(DCB &dc, int kValue)
        : _dc(dc),
          _kValue(kValue)
    {
    }

    uint64_t decode() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB(_dc._val, _dc._valI, _dc._preRead, 
                             _dc._cacheInt, _kValue, EC);
        return val64;
    }

    void skip() override
    {
        unsigned int length;
        UC64_SKIPEXPGOLOMB(_dc._val, _dc._valI, _dc._preRead, 
                             _dc._cacheInt, _kValue, EC);
    }

    uint64_t decodeSmall() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB_SMALL(_dc._val, _dc._valI, _dc._preRead, 
                                   _dc._cacheInt, _kValue, EC);
        return val64;
    }

    uint64_t decodeSmallApply() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(_dc._val, _dc._valI, _dc._preRead, 
                                         _dc._cacheInt, _kValue, EC, val64 =);
        return val64;
    }

    void skipSmall() override
    {
        unsigned int length;
        UC64_SKIPEXPGOLOMB_SMALL(_dc._val, _dc._valI, _dc._preRead, 
                                 _dc._cacheInt, _kValue, EC);
    }

    static std::unique_ptr<IDecodeFunc>
    make(DCB &dc, int kValue)
    {
        return std::unique_ptr<IDecodeFunc>
            (new DecodeExpGolombVarK<bigEndian>(dc, kValue));
    }
};


/*
 * Exp golomb decode functions getting kValue from a template argument
 * i.e. compiler is allowed to generate shift instructions with
 * immediate values and fold constant expressions involving kValue.
 */
template <bool bigEndian, int kValue>
class DecodeExpGolombConstK : public IDecodeFunc
{
public:
    using DCB = DecodeContext64Base;
    using DC = DecodeContext<bigEndian>;
    using EC = typename DC::EC;

    DCB &_dc;

    DecodeExpGolombConstK(DCB &dc)
        : _dc(dc)
    {
    }

    virtual uint64_t decode() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB(_dc._val, _dc._valI, _dc._preRead, 
                             _dc._cacheInt, kValue, EC);
        return val64;
    }

    virtual void skip() override
    {
        unsigned int length;
        UC64_SKIPEXPGOLOMB(_dc._val, _dc._valI, _dc._preRead, 
                             _dc._cacheInt, kValue, EC);
    }

    virtual uint64_t decodeSmall() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB_SMALL(_dc._val, _dc._valI, _dc._preRead, 
                                   _dc._cacheInt, kValue, EC);
        return val64;
    }

    virtual uint64_t decodeSmallApply() override
    {
        unsigned int length;
        uint64_t val64;
        UC64_DECODEEXPGOLOMB_SMALL_APPLY(_dc._val, _dc._valI, _dc._preRead, 
                                         _dc._cacheInt, kValue, EC, val64 =);
        return val64;
    }

    virtual void skipSmall() override
    {
        unsigned int length;
        UC64_SKIPEXPGOLOMB_SMALL(_dc._val, _dc._valI, _dc._preRead, 
                                 _dc._cacheInt, kValue, EC);
    }

    static std::unique_ptr<IDecodeFunc>
    make(DCB &dc, int)
    {
        return std::unique_ptr<IDecodeFunc>
            (new DecodeExpGolombConstK<bigEndian, kValue>(dc));
    }
};


using IDecodeFuncFactory =
    std::unique_ptr<IDecodeFunc> (*)(DecodeContext64Base &dc, int kValue);


template <bool bigEndian>
class DecodeFuncFactories
{
public:
    using IDF = IDecodeFuncFactory;
    std::vector<IDF> _constK;
    IDF _varK;

public:
    DecodeFuncFactories();

    void addConstKFactory(int kValue, IDecodeFuncFactory factory) {
        (void) kValue;
        assert(static_cast<unsigned int>(kValue) == _constK.size());
#pragma GCC diagnostic push
#if !defined(__clang__) && defined(__GNUC__) && __GNUC__ == 12
#pragma GCC diagnostic ignored "-Warray-bounds"
#endif
        _constK.push_back(factory);
#pragma GCC diagnostic pop
    }

    IDecodeFuncFactory getConstKFactory(int kValue) const {
        assert(kValue >= 0 &&
               static_cast<unsigned int>(kValue) < _constK.size());
        return _constK[kValue];
    }

    IDecodeFuncFactory getVarKFactory() const { return _varK; }
};


template <bool bigEndian>
struct RegisterFactoryPtr;


template <bool bigEndian>
using RegisterFactory = void (*)(DecodeFuncFactories<bigEndian> &factories,
                                 RegisterFactoryPtr<bigEndian> &ptr);


template <bool bigEndian>
struct RegisterFactoryPtr
{
    RegisterFactory<bigEndian> _ptr;

    RegisterFactoryPtr(RegisterFactory<bigEndian> ptr)
        : _ptr(ptr)
    {
    }
};


template <bool bigEndian, int kValue>
class RegisterFactories
{
public:
    static void registerFactory(DecodeFuncFactories<bigEndian> &factories,
                                RegisterFactoryPtr<bigEndian> &ptr)
    {
        factories.addConstKFactory(kValue,
                                   &DecodeExpGolombConstK<bigEndian, kValue>::
                                   make);
        ptr._ptr = &RegisterFactories<bigEndian, kValue+1>::registerFactory;
    }
};


template <bool bigEndian>
class RegisterFactories<bigEndian, 64>
{
public:
    static void registerFactory(DecodeFuncFactories<bigEndian> &factories,
                                RegisterFactoryPtr<bigEndian> &ptr)
    {
        (void) factories;
        ptr._ptr = nullptr;
    }
};


template <bool bigEndian>
DecodeFuncFactories<bigEndian>::DecodeFuncFactories()
    : _constK(),
      _varK(&DecodeExpGolombVarK<bigEndian>::make)
{
    RegisterFactoryPtr<bigEndian> f(
            &RegisterFactories<bigEndian, 0>::registerFactory);
    while (f._ptr) {
        (*f._ptr)(*this, f);
    }
}


class TestFixtureBase
{
public:
    std::vector<uint64_t> _randNums;
    using EC = EncodeContext64Base;

    void fillRandNums();
    void calcBoundaries(int kValue, bool small, std::vector<uint64_t> &v);

    void
    testBoundaries(int kValue, bool small,
                   std::vector<uint64_t> &v,
                   DecodeContext64Base &dc,
                   DecodeContext64Base &dcSkip,
                   DecodeContext64Base &dcApply,
                   IDecodeFunc &df,
                   IDecodeFunc &dfSkip,
                   IDecodeFunc &dfApply);

    void
    testRandNums(DecodeContext64Base &dc,
                 DecodeContext64Base &dcSkip,
                 IDecodeFunc &df,
                 IDecodeFunc &dfSkip);
};


void
TestFixtureBase::fillRandNums()
{
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        _randNums.push_back(rval);
    }
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        uint32_t bits = (rand() & 63);
        rval &= ((UINT64_C(1) << bits) - 1);
        _randNums.push_back(rval);
    }
}


namespace {

/*
 * Add values around a calculated boundary, to catch off by one errors.
 */
void
addBoundary(uint64_t boundary, uint64_t maxVal, std::vector<uint64_t> &v)
{
    uint64_t low = boundary > 2u ? boundary - 2 : 0;
    uint64_t high = maxVal - 2u < boundary ? maxVal : boundary + 2;
    assert(low <= high);
    LOG(info, "low=0x%" PRIx64 ", high=0x%" PRIx64, low, high);
    uint64_t i = low;
    for (;;) {
        v.push_back(i);
        if (i == high)
            break;
        ++i;
    }
}

}

void
TestFixtureBase::calcBoundaries(int kValue, bool small, std::vector<uint64_t> &v)
{
    const char *smallStr = small ? "small" : "not small";
    v.push_back(0);
    uint64_t maxVal = EC::maxExpGolombVal(kValue); // encode method limit
    if (small) {
        maxVal = EC::maxExpGolombVal(kValue, 64);
    }
    LOG(debug, "kValue=%u, %s, maxVal is 0x%" PRIx64, kValue, smallStr, maxVal);
    for (int bits = kValue + 1;
         bits + kValue <= 128 && (bits <= 64 || !small);
         ++bits) {
        uint64_t boundary = EC::maxExpGolombVal(kValue, bits);
        if (bits + kValue == 128) {
            LOG(debug,
                "boundary for kValue=%d, %s, bits=%d: 0x%" PRIx64,
                kValue, smallStr, bits, boundary);
        }
        addBoundary(boundary, maxVal, v);
    }
    std::sort(v.begin(), v.end());
    auto ve = std::unique(v.begin(), v.end());
    uint32_t oldSize = v.size();
    v.resize(ve - v.begin());
    uint32_t newSize = v.size();
    LOG(debug,
        "kValues=%u, %s, boundaries %u -> %u, maxVal=0x%" PRIx64 ", highest=0x%" PRIx64,
        kValue, smallStr, oldSize, newSize, maxVal, v.back());
}


void
TestFixtureBase::testBoundaries(int kValue, bool small,
                                std::vector<uint64_t> &v,
                                DecodeContext64Base &dc,
                                DecodeContext64Base &dcSkip,
                                DecodeContext64Base &dcApply,
                                IDecodeFunc &df,
                                IDecodeFunc &dfSkip,
                                IDecodeFunc &dfApply)
{
    uint32_t bits = 0;
    uint64_t maxSame = 0;
    
    for (auto num : v) {
        uint64_t prevPos = dc.getReadOffset();
        uint64_t val64 = small ? df.decodeSmall() : df.decode();
        EXPECT_EQUAL(num, val64);
        uint64_t currPos = dc.getReadOffset();
        if (small) {
            dfSkip.skipSmall();
        } else {
            dfSkip.skip();
        }
        EXPECT_EQUAL(currPos, dcSkip.getReadOffset());
        if (small) {
            uint64_t sval64 = dfApply.decodeSmallApply();
            EXPECT_EQUAL(num, sval64);
            EXPECT_EQUAL(currPos, dcApply.getReadOffset());
        }
        if (num == 0) {
            bits = currPos - prevPos;
            maxSame = EC::maxExpGolombVal(kValue, bits);
        } else {
            assert(bits <= currPos - prevPos);
            if (bits < currPos - prevPos) {
                ASSERT_EQUAL(bits + 2, currPos - prevPos);
                bits += 2;
                ASSERT_EQUAL(maxSame + 1, num);
                maxSame = EC::maxExpGolombVal(kValue, bits);
            }
        }
    }
}


void
TestFixtureBase::testRandNums(DecodeContext64Base &dc,
                              DecodeContext64Base &dcSkip,
                              IDecodeFunc &df,
                              IDecodeFunc &dfSkip)
{
    for (auto num : _randNums) {
        uint64_t val64 = df.decode();
        EXPECT_EQUAL(num, val64);
        uint64_t currPos = dc.getReadOffset();
        dfSkip.skip();
        EXPECT_EQUAL(currPos, dcSkip.getReadOffset());
    }
}



template <bool bigEndian>
class TestFixture : public TestFixtureBase
{
public:
    DecodeFuncFactories<bigEndian> _factories;
    using DC = DecodeContext<bigEndian>;
    using EC = typename DC::EC;
    using Parent = TestFixtureBase;
    using Parent::testBoundaries;
    using Parent::testRandNums;

    TestFixture()
        : TestFixtureBase(),
          _factories()
    {
        fillRandNums();
    }

    void
    testBoundaries(int kValue, bool small,
                   std::vector<uint64_t> &v,
                   IDecodeFuncFactory f,
                   search::ComprFileWriteContext &wc);
    void testBoundaries(int kValue, bool small, std::vector<uint64_t> &v);
    void testBoundaries();
    void testRandNums(int kValue, IDecodeFuncFactory f, search::ComprFileWriteContext &wc);
    void testRandNums(int kValue);
    void testRandNums();
};


template <bool bigEndian>
void
TestFixture<bigEndian>::testBoundaries(int kValue, bool small,
                                       std::vector<uint64_t> &v,
                                       IDecodeFuncFactory f,
                                       search::ComprFileWriteContext &wc)
{
    DC dc(wc.getComprBuf(), 0);
    DC dcSkip(wc.getComprBuf(), 0);
    DC dcApply(wc.getComprBuf(), 0);
    std::unique_ptr<IDecodeFunc> df((*f)(dc, kValue));
    std::unique_ptr<IDecodeFunc> dfSkip((*f)(dcSkip, kValue));
    std::unique_ptr<IDecodeFunc> dfApply((*f)(dcApply, kValue));
    testBoundaries(kValue, small, v, dc, dcSkip, dcApply,
                   *df, *dfSkip, *dfApply);
}


template <bool bigEndian>
void
TestFixture<bigEndian>::testBoundaries(int kValue, bool small, std::vector<uint64_t> &v)
{
    EC e;
    search::ComprFileWriteContext wc(e);
    wc.allocComprBuf(32_Ki, 32_Ki);
    e.setupWrite(wc);
    for (auto num : v) {
        e.encodeExpGolomb(num, kValue);
        if (e._valI >= e._valE)
            wc.writeComprBuffer(false);
    }
    e.flush();

    IDecodeFuncFactory f = _factories.getConstKFactory(kValue);
    testBoundaries(kValue, small, v, f, wc);
    f = _factories.getVarKFactory();
    testBoundaries(kValue, small, v, f, wc);
}


template <bool bigEndian>
void
TestFixture<bigEndian>::testBoundaries()
{
    for (int kValue = 0; kValue < 64; ++kValue) {
        std::vector<uint64_t> v;
        calcBoundaries(kValue, false, v);
        testBoundaries(kValue, false, v);
        /*
         * Note: We don't support kValue being 63 for when decoding
         * "small" numbers (limited to 64 bits in encoded form) since
         * performance penalty is not worth the extra flexibility.
         */ 
        if (kValue < 63) {
            v.clear();
            calcBoundaries(kValue, true, v);
            testBoundaries(kValue, true, v);
        }
    }
}


template <bool bigEndian>
void
TestFixture<bigEndian>::testRandNums(int kValue, IDecodeFuncFactory f, search::ComprFileWriteContext &wc)
{
    DC dc(wc.getComprBuf(), 0);
    DC dcSkip(wc.getComprBuf(), 0);
    std::unique_ptr<IDecodeFunc> df((*f)(dc, kValue));
    std::unique_ptr<IDecodeFunc> dfSkip((*f)(dcSkip, kValue));
    testRandNums(dc, dcSkip, *df, *dfSkip);
}


template <bool bigEndian>
void
TestFixture<bigEndian>::testRandNums(int kValue)
{
    EC e;
    search::ComprFileWriteContext wc(e);
    wc.allocComprBuf(32_Ki, 32_Ki);
    e.setupWrite(wc);
    for (auto num : _randNums) {
        e.encodeExpGolomb(num, kValue);
        if (e._valI >= e._valE)
            wc.writeComprBuffer(false);
    }
    e.flush();

    IDecodeFuncFactory f = _factories.getConstKFactory(kValue);
    testRandNums(kValue, f, wc);
    f = _factories.getVarKFactory();
    testRandNums(kValue, f, wc);
}


template <bool bigEndian>
void
TestFixture<bigEndian>::testRandNums()
{
    for (int k = 0; k < 64; ++k) {
        testRandNums(k);
    }
}


TEST_F("Test bigendian expgolomb encoding/decoding", TestFixture<true>)
{
    f.testRandNums();
    f.testBoundaries();
}


TEST_F("Test little expgolomb encoding/decoding", TestFixture<false>)
{
    f.testRandNums();
    f.testBoundaries();
}


TEST_MAIN() { TEST_RUN_ALL(); }
