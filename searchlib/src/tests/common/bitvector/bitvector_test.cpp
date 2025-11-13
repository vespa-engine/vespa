// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/searchlib/common/partialbitvector.h>
#include <vespa/searchlib/common/read_stats.h>
#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/datastore/aligner.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <sstream>

using namespace search;

using vespalib::alloc::Alloc;
using vespalib::alloc::test::MemoryAllocatorObserver;
using vespalib::datastore::Aligner;
using vespalib::nbostream;
using AllocStats = MemoryAllocatorObserver::Stats;

namespace {

const std::string testdata("bitvector_test_testdata");

std::string
toString(const BitVector & bv)
{
    std::stringstream ss;
    ss << "[";
    bool first = true;
    uint32_t nextBit = bv.getStartIndex();
    for (;;) {
        nextBit = bv.getNextTrueBit(nextBit);
        if (nextBit >= bv.size()) {
            break;
        }
        if (!first) {
            ss << ",";
        }
        ss << nextBit++;
        first = false;
    }
    ss << "]";
    return ss.str();
}


std::string
toString(BitVectorIterator &b)
{
    std::stringstream ss;
    ss << "[";
    bool first = true;
    b.initFullRange();
    for (uint32_t docId = 1; ! b.isAtEnd(docId); ) {
        if (!b.seek(docId)) {
            docId = std::max(docId + 1, b.getDocId());
            if (b.isAtEnd(docId))
                break;
            continue;
        }
        if (!first) {
            ss << ",";
        }
        b.unpack(docId);
        ss << docId++;
        first = false;
    }
    ss << "]";
    return ss.str();
}



uint32_t
myCountInterval(const BitVector &bv, uint32_t low, uint32_t high)
{
    uint32_t res = 0u;
    if (bv.size() == 0u)
        return 0u;
    if (high >= bv.size())
        high = bv.size() - 1;
    for (; low <= high; ++low) {
        if (bv.testBit(low))
            ++res;
    }
    return res;
}

void
scan(uint32_t count, uint32_t offset, uint32_t size, vespalib::Rand48 &rnd)
{
    std::vector<uint32_t> lids;
    lids.reserve(count);
    uint32_t end = size + offset;
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t lid = offset + (rnd.lrand48() % (size - 1)) + 1;
        lids.push_back(lid);
    }
    std::sort(lids.begin(), lids.end());
    lids.resize(std::unique(lids.begin(), lids.end()) - lids.begin());
    BitVector::UP bv(BitVector::create(offset, end));
    for (auto lid : lids) {
        bv->setBit(lid);
    }
    EXPECT_EQ(bv->getFirstTrueBit(), bv->getNextTrueBit(bv->getStartIndex()));
    uint32_t prevLid = bv->getStartIndex();
    for (auto lid : lids) {
        EXPECT_EQ(lid, bv->getNextTrueBit(prevLid + 1));
        EXPECT_EQ(prevLid, bv->getPrevTrueBit(lid - 1));
        prevLid = lid;
    }
    EXPECT_TRUE(bv->getNextTrueBit(prevLid + 1) >= end);
    EXPECT_EQ(prevLid, bv->getPrevTrueBit(end - 1));
}

void
scanWithOffset(uint32_t offset)
{
    vespalib::Rand48 rnd;

    rnd.srand48(32);
    scan(10,      offset, 1000000, rnd);
    scan(100,     offset, 1000000, rnd);
    scan(1000,    offset, 1000000, rnd);
    scan(10000,   offset, 1000000, rnd);
    scan(100000,  offset, 1000000, rnd);
    scan(500000,  offset, 1000000, rnd);
    scan(1000000, offset, 1000000, rnd);
}

std::string
param_as_string(const testing::TestParamInfo<uint32_t>& info)
{
    std::ostringstream os;
    os << "old_guard_bits_";
    os << info.param;
    return os.str();
}

}

bool
assertBV(const std::string & exp, const BitVector & act)
{
    EXPECT_EQ(exp, toString(act));
    bool res1 = (exp == toString(act));
    search::fef::TermFieldMatchData f;
    queryeval::SearchIterator::UP it(BitVectorIterator::create(&act, f, true));
    auto & b(dynamic_cast<BitVectorIterator &>(*it));
    EXPECT_EQ(exp, toString(b));
    bool res2 = (exp == toString(b));
    return res1 && res2;
}

void
fill(BitVector & bv, const std::vector<uint32_t> & bits, uint32_t offset, bool fill=true)
{
    for (uint32_t bit : bits) {
        if (fill) {
            bv.setBit(bit + offset);
        } else {
            bv.clearBit(bit + offset);
        }
    }
}

std::string
fill(const std::vector<uint32_t> & bits, uint32_t offset)
{
    vespalib::asciistream os;
    os << "[";
    size_t count(0);
    for (uint32_t bit : bits) {
        count++;
        os << bit + offset;
        if (count != bits.size()) { os << ","; }
    }
    os << "]";
    return os.str();
}

std::vector<uint32_t> A = {7, 39, 71, 103};
std::vector<uint32_t> B = {15, 39, 71, 100};

void
testAnd(uint32_t offset)
{
    uint32_t end = offset + 128;
    BitVector::UP v1(BitVector::create(offset, end));
    BitVector::UP v2(BitVector::create(offset, end));
    BitVector::UP v3(BitVector::create(offset, end));

    fill(*v1, A, offset);
    fill(*v3, A, offset);
    fill(*v2, B, offset);
    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));

    EXPECT_TRUE(assertBV(fill(A, offset), *v3));
    v3->andWith(*v2);
    EXPECT_TRUE(assertBV(fill({39,71}, offset), *v3));

    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));
}

void
testOr(uint32_t offset)
{
    uint32_t end = offset + 128;
    BitVector::UP v1(BitVector::create(offset, end));
    BitVector::UP v2(BitVector::create(offset, end));
    BitVector::UP v3(BitVector::create(offset, end));

    fill(*v1, A, offset);
    fill(*v3, A, offset);
    fill(*v2, B, offset);
    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));

    EXPECT_TRUE(assertBV(fill(A, offset), *v3));
    v3->orWith(*v2);
    EXPECT_TRUE(assertBV(fill({7,15,39,71,100,103}, offset), *v3));

    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));
}

void
testAndNot(uint32_t offset)
{
    uint32_t end = offset + 128;
    BitVector::UP v1(BitVector::create(offset, end));
    BitVector::UP v2(BitVector::create(offset, end));
    BitVector::UP v3(BitVector::create(offset, end));

    fill(*v1, A, offset);
    fill(*v3, A, offset);
    fill(*v2, B, offset);
    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));

    EXPECT_TRUE(assertBV(fill(A, offset), *v3));
    v3->andNotWith(*v2);
    EXPECT_TRUE(assertBV(fill({7,103}, offset), *v3));

    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
    EXPECT_TRUE(assertBV(fill(B, offset), *v2));

    v3->clear();
    fill(*v3, A, offset);
    EXPECT_TRUE(assertBV(fill(A, offset), *v3));


    std::vector<RankedHit> rh;
    rh.emplace_back(15u+offset, 0.0);
    rh.emplace_back(39u+offset, 0.0);
    rh.emplace_back(71u+offset, 0.0);
    rh.emplace_back(100u+offset, 0.0);

    v3->andNotWithT(RankedHitIterator(&rh[0], 4));
    EXPECT_TRUE(assertBV(fill({7,103}, offset), *v3));
}

void
testNot(uint32_t offset)
{
    uint32_t end = offset + 128;
    BitVector::UP v1(BitVector::create(offset, end));
    v1->setInterval(offset, end);
    fill(*v1, A, offset, false);

    v1->notSelf();
    EXPECT_TRUE(assertBV(fill(A, offset), *v1));
}

std::unique_ptr<const BitVector>
make_test_bv()
{
    auto bv = std::make_unique<AllocatedBitVector>(2047u);
    bv->setBit(42);
    bv->setBit(1049);
    return bv;
}

std::unique_ptr<nbostream>
write_test_bv_to_nbostream(const BitVector &bv, uint32_t guard_bits, size_t alignment)
{
    auto out = std::make_unique<nbostream>();
    Aligner aligner(alignment);
    uint64_t size = bv.size();
    uint64_t cached_hits = bv.countTrueBits();
    uint64_t file_bytes = aligner.align(bv.num_bytes_plain(size + guard_bits));
    *out << size << cached_hits << file_bytes;
    out->write(bv.getStart(), std::min(bv.getFileBytes(), file_bytes));
    if (guard_bits == BitVector::num_guard_bits) {
        EXPECT_EQ(file_bytes, bv.getFileBytes());
    } else {
        EXPECT_NE(file_bytes, bv.getFileBytes());
    }
    if (file_bytes > bv.getFileBytes()) {
        EXPECT_TRUE(guard_bits > BitVector::num_guard_bits);
        std::vector<char> zerofill(file_bytes - bv.getFileBytes());
        out->write(zerofill.data(), zerofill.size());
    }
    return out;
}

void
write_test_bv_to_file(const BitVector& bv, const std::string& file_name)
{
    std::filesystem::path bvpath(file_name);
    std::ofstream bvfile(bvpath, std::ios::binary);
    auto legacy_entry_size = BitVector::legacy_num_bytes_with_single_guard_bit(bv.size());
    bvfile.write(static_cast<const char *>(bv.getStart()), legacy_entry_size);
    bvfile.close();
    ASSERT_TRUE(bvfile.good());
    std::filesystem::resize_file(bvpath, 1024u);
}

class BitVectorTest : public ::testing::Test {
protected:
    static std::unique_ptr<const BitVector> _file_bv;
    static constexpr uint32_t file_alignment = 0x100;

    BitVectorTest();
    ~BitVectorTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

std::unique_ptr<const BitVector> BitVectorTest::_file_bv;

BitVectorTest::BitVectorTest()
    : ::testing::Test()
{
}

BitVectorTest::~BitVectorTest() = default;

void
BitVectorTest::SetUpTestSuite()
{
    std::filesystem::remove_all(testdata);
    std::filesystem::create_directory(testdata);
    auto bv = make_test_bv();
    write_test_bv_to_file(*bv,testdata + "/bv");
    _file_bv = std::move(bv);
}

void
BitVectorTest::TearDownTestSuite()
{
    _file_bv.reset();
    std::filesystem::remove_all(testdata);
}

TEST_F(BitVectorTest, requireThatSequentialOperationsOnPartialWorks)
{
    PartialBitVector p1(717,919);

    EXPECT_FALSE(p1.hasTrueBits());
    EXPECT_EQ(0u, p1.countTrueBits());
    p1.setBit(719);
    EXPECT_EQ(0u, p1.countTrueBits());
    p1.invalidateCachedCount();
    EXPECT_TRUE(p1.hasTrueBits());
    EXPECT_EQ(1u, p1.countTrueBits());
    p1.setBitAndMaintainCount(718);
    p1.setBitAndMaintainCount(739);
    p1.setBitAndMaintainCount(871);
    p1.setBitAndMaintainCount(903);
    EXPECT_EQ(5u, p1.countTrueBits());
    EXPECT_TRUE(assertBV("[718,719,739,871,903]", p1));

    PartialBitVector p2(717,919);
    EXPECT_FALSE(p1 == p2);
    p2.setBitAndMaintainCount(719);
    p2.setBitAndMaintainCount(718);
    p2.setBitAndMaintainCount(739);
    p2.setBitAndMaintainCount(871);
    EXPECT_FALSE(p1 == p2);
    p2.setBitAndMaintainCount(903);
    EXPECT_TRUE(p1 == p2);

    AllocatedBitVector full(1000);
    full.setInterval(0, 1000);
    EXPECT_EQ(5u, p2.countTrueBits());
    p2.orWith(full);
    EXPECT_EQ(202u, p2.countTrueBits());

    AllocatedBitVector before(100);
    before.setInterval(0, 100);
    p2.orWith(before);
    EXPECT_EQ(202u, p2.countTrueBits());

    PartialBitVector after(1000, 1100);
    after.setInterval(1000, 1100);
    EXPECT_EQ(202u, p2.countTrueBits());
}

TEST_F(BitVectorTest, requireThatInitRangeStaysWithinBounds) {
    AllocatedBitVector v1(128);
    search::fef::TermFieldMatchData f;
    queryeval::SearchIterator::UP it(BitVectorIterator::create(&v1, f, true));
    it->initRange(700, 800);
    EXPECT_TRUE(it->isAtEnd());
}

void
setEveryNthBit(uint32_t n, BitVector & bv, uint32_t offset, uint32_t end) {
    for (uint32_t i(0); i < (end - offset); i++) {
        if ((i % n) == 0) {
            bv.setBit(offset + i);
        } else {
            bv.clearBit(offset + i);
        }
    }
    bv.invalidateCachedCount();
}
BitVector::UP
createEveryNthBitSet(uint32_t n, uint32_t offset, uint32_t sz) {
    BitVector::UP bv(BitVector::create(offset, offset + sz));
    setEveryNthBit(n, *bv, offset, offset + sz);
    return bv;
}

template<typename Func>
void
verifyThatLongerWithShorterWorksAsZeroPadded(uint32_t offset, uint32_t sz1, uint32_t sz2, Func func) {
    BitVector::UP aLarger = createEveryNthBitSet(2, offset, sz2);

    BitVector::UP bSmall = createEveryNthBitSet(3, 0, offset + sz1);
    BitVector::UP bLarger = createEveryNthBitSet(3, 0, offset + sz2);
    BitVector::UP bEmpty = createEveryNthBitSet(3, 0, 0);
    bLarger->clearInterval(offset + sz1, offset + sz2);
    EXPECT_EQ(bSmall->countTrueBits(), bLarger->countTrueBits());

    BitVector::UP aLarger2 = BitVector::create(*aLarger, aLarger->getStartIndex(), aLarger->size());
    BitVector::UP aLarger3 = BitVector::create(*aLarger, aLarger->getStartIndex(), aLarger->size());
    EXPECT_TRUE(*aLarger == *aLarger2);
    EXPECT_TRUE(*aLarger == *aLarger3);
    func(*aLarger, *bLarger);
    func(*aLarger2, *bSmall);
    func(*aLarger3, *bEmpty);
    EXPECT_TRUE(*aLarger == *aLarger2);
}

template<typename Func>
void
verifyNonOverlappingWorksAsZeroPadded(bool clear, Func func) {
    constexpr size_t CNT = 34;
    BitVector::UP left = createEveryNthBitSet(3, 1000, 100);
    BitVector::UP right = createEveryNthBitSet(3, 2000, 100);
    EXPECT_EQ(CNT, left->countTrueBits());
    EXPECT_EQ(CNT, right->countTrueBits());
    func(*left, *right);
    EXPECT_EQ(clear ? 0 : CNT, left->countTrueBits());
    EXPECT_EQ(CNT, right->countTrueBits());
    left = createEveryNthBitSet(3, 1000, 100);
    right = createEveryNthBitSet(3, 2000, 100);
    EXPECT_EQ(CNT, left->countTrueBits());
    EXPECT_EQ(CNT, right->countTrueBits());
    func(*right, *left);
    EXPECT_EQ(CNT, left->countTrueBits());
    EXPECT_EQ(clear ? 0 : CNT, right->countTrueBits());
}

TEST_F(BitVectorTest, requireThatAndWorks) {
    for (uint32_t offset(0); offset < 100; offset++) {
        testAnd(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.andWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(true, [](BitVector & a, const BitVector & b) { a.andWith(b); });
}

TEST_F(BitVectorTest, requireThatOrWorks) {
    for (uint32_t offset(0); offset < 100; offset++) {
        testOr(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.orWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(false, [](BitVector & a, const BitVector & b) { a.orWith(b); });
}


TEST_F(BitVectorTest, requireThatAndNotWorks) {
    for (uint32_t offset(0); offset < 100; offset++) {
        testAndNot(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.andNotWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(false, [](BitVector & a, const BitVector & b) { a.andNotWith(b); });
}

TEST_F(BitVectorTest, test_that_empty_bitvectors_does_not_crash) {
    BitVector::UP empty = BitVector::create(0);
    EXPECT_EQ(0u, empty->countTrueBits());
    EXPECT_EQ(0u, empty->countInterval(0, 100));
    empty->setInterval(0,17);
    EXPECT_EQ(0u, empty->countInterval(0, 100));
    empty->clearInterval(0,17);
    EXPECT_EQ(0u, empty->countInterval(0, 100));
    empty->notSelf();
    EXPECT_EQ(0u, empty->countInterval(0, 100));
}

TEST_F(BitVectorTest, requireThatNotWorks) {
    for (uint32_t offset(0); offset < 100; offset++) {
        testNot(offset);
    }
}

TEST_F(BitVectorTest, requireThatClearWorks)
{
    AllocatedBitVector v1(128);

    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    EXPECT_TRUE(assertBV("[7,39,71,103]", v1));

    v1.clear();
    EXPECT_TRUE(assertBV("[]", v1));
}

TEST_F(BitVectorTest, requireThatForEachWorks) {
    AllocatedBitVector v1(128);

    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    EXPECT_EQ(128u, v1.size());

    size_t sum(0);
    v1.foreach_truebit([&](uint32_t key) { sum += key; });
    EXPECT_EQ(220u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 7);
    EXPECT_EQ(220u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 6, 7);
    EXPECT_EQ(0u, sum);
    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 7, 8);
    EXPECT_EQ(7u, sum);
    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 8, 9);
    EXPECT_EQ(0u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 8);
    EXPECT_EQ(213u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 6);
    EXPECT_EQ(5u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 7);
    EXPECT_EQ(11u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 8);
    EXPECT_EQ(11u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 9);
    EXPECT_EQ(19u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 6);
    EXPECT_EQ(size_t((((6+127)*(127-6 + 1)) >> 1) - 220), sum);
}


TEST_F(BitVectorTest, requireThatSetWorks)
{
    AllocatedBitVector v1(128);

    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    EXPECT_TRUE(assertBV("[7,39,71,103]", v1));
    v1.invalidateCachedCount();
    EXPECT_EQ(4u, v1.countTrueBits());

    v1.setBit(80);
    EXPECT_EQ(4u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQ(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,71,80,103]", v1));

    v1.clearBit(35);
    EXPECT_EQ(5u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQ(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,71,80,103]", v1));
    v1.clearBit(71);
    EXPECT_EQ(5u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQ(4u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,80,103]", v1));

    v1.setBitAndMaintainCount(39);
    EXPECT_EQ(4u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,80,103]", v1));
    v1.setBitAndMaintainCount(57);
    EXPECT_EQ(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,57,80,103]", v1));
}

TEST_F(BitVectorTest, test_BitWord_startBits_endBits) {
    EXPECT_EQ(BitWord::startBits(0),  0x00ul);
    EXPECT_EQ(BitWord::startBits(1),  0x01ul);
    EXPECT_EQ(BitWord::startBits(2),  0x03ul);
    EXPECT_EQ(BitWord::startBits(61), 0x1ffffffffffffffful);
    EXPECT_EQ(BitWord::startBits(62), 0x3ffffffffffffffful);
    EXPECT_EQ(BitWord::startBits(63), 0x7ffffffffffffffful);
    EXPECT_EQ(BitWord::endBits(0),  0xfffffffffffffffeul);
    EXPECT_EQ(BitWord::endBits(1),  0xfffffffffffffffcul);
    EXPECT_EQ(BitWord::endBits(2),  0xfffffffffffffff8ul);
    EXPECT_EQ(BitWord::endBits(61), 0xc000000000000000ul);
    EXPECT_EQ(BitWord::endBits(62), 0x8000000000000000ul);
    EXPECT_EQ(BitWord::endBits(63), 0x0000000000000000ul);
}

TEST_F(BitVectorTest, requireThatClearIntervalWorks)
{
    AllocatedBitVector v1(1200);
    
    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    v1.setBit(200);
    v1.setBit(500);
    EXPECT_TRUE(assertBV("[7,39,71,103,200,500]", v1));

    v1.clearInterval(40, 70);
    EXPECT_TRUE(assertBV("[7,39,71,103,200,500]", v1));
    v1.clearInterval(39, 71);
    EXPECT_TRUE(assertBV("[7,71,103,200,500]", v1));
    v1.clearInterval(39, 72);
    EXPECT_TRUE(assertBV("[7,103,200,500]", v1));
    v1.clearInterval(20, 501);
    EXPECT_TRUE(assertBV("[7]", v1));

    AllocatedBitVector v(400);
    for (size_t intervalLength(1); intervalLength < 100; intervalLength++) {
        for (size_t offset(100); offset < 200; offset++) {

            v.clear();
            v.notSelf();
            EXPECT_EQ(400u, v.countTrueBits());

            v.clearInterval(offset, offset+intervalLength);
            EXPECT_FALSE(v.testBit(offset));
            EXPECT_TRUE(v.testBit(offset-1));
            EXPECT_FALSE(v.testBit(offset+intervalLength-1));
            EXPECT_TRUE(v.testBit(offset+intervalLength));
            EXPECT_EQ(400 - intervalLength, v.countTrueBits());
        }
    }
}


TEST_F(BitVectorTest, requireThatSetIntervalWorks)
{
    AllocatedBitVector v1(1200);
    
    EXPECT_FALSE(v1.hasTrueBits());
    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    v1.setBit(200);
    v1.setBit(500);
    EXPECT_TRUE(assertBV("[7,39,71,103,200,500]", v1));

    v1.setInterval(40, 46);
    EXPECT_TRUE(assertBV("[7,39,40,41,42,43,44,45,71,103,200,500]", v1));
    EXPECT_TRUE(v1.hasTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQ(12u, v1.countTrueBits());
    EXPECT_EQ(12u, v1.countInterval(1, 1199));
    EXPECT_EQ(12u, myCountInterval(v1, 1, 1199));
    
    v1.setInterval(40, 200);
    EXPECT_EQ(164u, v1.countInterval(1, 1199));
    EXPECT_EQ(164u, myCountInterval(v1, 1, 1199));
    EXPECT_EQ(163u, v1.countInterval(1, 201));
    EXPECT_EQ(162u, v1.countInterval(1, 200));
    EXPECT_EQ(163u, v1.countInterval(7, 201));
    EXPECT_EQ(162u, v1.countInterval(8, 201));
    EXPECT_EQ(161u, v1.countInterval(8, 200));
    v1.clearInterval(72, 174);
    EXPECT_EQ(62u, v1.countInterval(1, 1199));
    EXPECT_EQ(62u, myCountInterval(v1, 1, 1199));
    EXPECT_EQ(61u, v1.countInterval(1, 201));
    EXPECT_EQ(60u, v1.countInterval(1, 200));
    EXPECT_EQ(61u, v1.countInterval(7, 201));
    EXPECT_EQ(60u, v1.countInterval(8, 201));
    EXPECT_EQ(59u, v1.countInterval(8, 200));
    EXPECT_EQ(51u, v1.countInterval(8, 192));
    EXPECT_EQ(50u, v1.countInterval(8, 191));

    EXPECT_EQ(1u, v1.countInterval(1, 20));
    EXPECT_EQ(1u, v1.countInterval(7, 20));
    EXPECT_EQ(0u, v1.countInterval(8, 20));
    EXPECT_EQ(1u, v1.countInterval(1, 8));
    EXPECT_EQ(0u, v1.countInterval(1, 7));
}

TEST_F(BitVectorTest, requireThatScanWorks)
{
    scanWithOffset(0);
    scanWithOffset(19876);
}

TEST_F(BitVectorTest, requireThatGrowWorks)
{
    vespalib::GenerationHolder g;
    GrowableBitVector v(200, 200, g);
    EXPECT_EQ(0u, v.writer().countTrueBits());
    
    v.writer().setBitAndMaintainCount(7);
    v.writer().setBitAndMaintainCount(39);
    v.writer().setBitAndMaintainCount(71);
    v.writer().setBitAndMaintainCount(103);
    EXPECT_EQ(4u, v.writer().countTrueBits());

    EXPECT_EQ(200u, v.reader().size());
    EXPECT_EQ(2048u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());
    EXPECT_TRUE(v.reserve(2048u));
    EXPECT_EQ(200u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.extend(202));
    EXPECT_EQ(202u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.shrink(200));
    EXPECT_EQ(200u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.reserve(4096u - BitVector::num_guard_bits));
    EXPECT_EQ(200u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.shrink(202));
    EXPECT_EQ(202u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQ(4u, v.writer().countTrueBits());

    EXPECT_FALSE(v.shrink(100));
    EXPECT_EQ(100u, v.reader().size()); 
    EXPECT_EQ(4096u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_TRUE(assertBV("[7,39,71]", v.reader()));
    EXPECT_EQ(3u, v.writer().countTrueBits());

    v.writer().invalidateCachedCount();
    EXPECT_TRUE(v.reserve(5100u));
    EXPECT_EQ(100u, v.reader().size());
    EXPECT_EQ(6144u - BitVector::num_guard_bits, v.writer().capacity());
    EXPECT_EQ(3u, v.writer().countTrueBits());

    g.assign_generation(1);
    g.reclaim(2);
}

TEST_F(BitVectorTest, require_that_growable_bit_vectors_keeps_memory_allocator)
{
    AllocStats stats;
    auto memory_allocator = std::make_unique<MemoryAllocatorObserver>(stats);
    Alloc init_alloc = Alloc::alloc_with_allocator(memory_allocator.get());
    vespalib::GenerationHolder g;
    GrowableBitVector v(200, 200, g, &init_alloc);
    EXPECT_EQ(AllocStats(1, 0), stats);
    v.writer().resize(1); // DO NOT TRY THIS AT HOME
    EXPECT_EQ(AllocStats(2, 1), stats);
    v.reserve(2048);
    EXPECT_EQ(AllocStats(3, 1), stats);
    v.extend(5000);
    EXPECT_EQ(AllocStats(4, 1), stats);
    v.shrink(200);
    EXPECT_EQ(AllocStats(4, 1), stats);
    v.writer().resize(1); // DO NOT TRY THIS AT HOME
    EXPECT_EQ(AllocStats(5, 2), stats);
    g.assign_generation(1);
    g.reclaim(2);
}

TEST_F(BitVectorTest, require_that_creating_partial_nonoverlapping_vector_is_cleared) {
    AllocatedBitVector org(1000);
    org.setInterval(org.getStartIndex(), org.size());
    EXPECT_EQ(1000u, org.countTrueBits());

    BitVector::UP after = BitVector::create(org, 2000, 3000);
    EXPECT_EQ(2000u, after->getStartIndex());
    EXPECT_EQ(3000u, after->size());
    EXPECT_EQ(0u, after->countTrueBits());

    BitVector::UP before = BitVector::create(*after, 0, 1000);
    EXPECT_EQ(0u, before->getStartIndex());
    EXPECT_EQ(1000u, before->size());
    EXPECT_EQ(0u, before->countTrueBits());
}

TEST_F(BitVectorTest, require_that_creating_partial_overlapping_vector_is_properly_copied) {
    AllocatedBitVector org(1000);
    org.setInterval(org.getStartIndex(), org.size());
    EXPECT_EQ(1000u, org.countTrueBits());

    BitVector::UP after = BitVector::create(org, 900, 1100);
    EXPECT_EQ(900u, after->getStartIndex());
    EXPECT_EQ(1100u, after->size());
    EXPECT_EQ(100u, after->countTrueBits());

    BitVector::UP before = BitVector::create(*after, 0, 1000);
    EXPECT_EQ(0u, before->getStartIndex());
    EXPECT_EQ(1000u, before->size());
    EXPECT_EQ(100u, before->countTrueBits());
}

void fill(BitVector & bv) {
    uint32_t numBitsSet = bv.size()/10;
    for (uint32_t i(0); i < numBitsSet; i++) {
        bv.setBit(rand()%bv.size());
    }
}

BitVector::UP
orSerial(const std::vector<BitVector::UP> & bvs) {
    BitVector::UP master = BitVector::create(*bvs[0]);
    for (uint32_t i(1); i < bvs.size(); i++) {
        master->orWith(*bvs[i]);
    }
    return master;
}

BitVector::UP
orParallel(vespalib::ThreadBundle & thread_bundle, const std::vector<BitVector::UP> & bvs) {
    BitVector::UP master = BitVector::create(*bvs[0]);
    std::vector<BitVector *> vectors;
    vectors.reserve(bvs.size());
    vectors.push_back(master.get());
    for (uint32_t i(1); i < bvs.size(); i++) {
        vectors.push_back(bvs[i].get());
    }
    BitVector::parallelOr(thread_bundle, vectors);
    return master;
}

void verifyParallelOr(vespalib::ThreadBundle & thread_bundle, uint32_t numVectors, uint32_t numBits) {
    std::vector<BitVector::UP> bvs;
    bvs.reserve(numVectors);
    for (uint32_t i(0); i < numVectors; i++) {
        bvs.push_back(BitVector::create(numBits));
        fill(*bvs.back());
    }
    auto serial = orSerial(bvs);
    auto parallel = orParallel(thread_bundle, bvs);
    EXPECT_TRUE(*serial == *parallel);
}

TEST_F(BitVectorTest, Require_that_parallel_OR_computes_same_result_as_serial) {
    srand(7);
    for (uint32_t numThreads : {1, 3, 7}) {
        vespalib::SimpleThreadBundle thread_bundle(numThreads);
        for (uint32_t numVectors : {1, 2, 5}) {
            for (uint32_t numBits : {1117, 11117, 111117, 1111117, 11111117}) {
                verifyParallelOr(thread_bundle, numVectors, numBits);
            }
        }
    }
}

namespace {

bool check_full_term_field_match_data_reset_on_unpack(bool strict, bool full_reset)
{
    AllocatedBitVector bv(10);
    bv.setBit(5);
    fef::TermFieldMatchData tfmd;
    auto iterator = BitVectorIterator::create(&bv, bv.size(), tfmd, nullptr, strict, false, full_reset);
    tfmd.setNumOccs(10);
    iterator->initRange(1, bv.size());
    iterator->unpack(5);
    return tfmd.getNumOccs() == 0;
}

}

TEST_F(BitVectorTest, reset_term_field_match_data_on_unpack)
{
    EXPECT_FALSE(check_full_term_field_match_data_reset_on_unpack(false, false));
    EXPECT_FALSE(check_full_term_field_match_data_reset_on_unpack(true, false));
    EXPECT_TRUE(check_full_term_field_match_data_reset_on_unpack(false, true));
    EXPECT_TRUE(check_full_term_field_match_data_reset_on_unpack(true, true));
}

TEST_F(BitVectorTest, fixup_count_and_guard_bit_and_zero_remaining_data_bits_after_short_read)
{
    AllocatedBitVector bv(256);
    bv.setBit(5);
    bv.invalidateCachedCount();
    EXPECT_EQ(1, bv.countTrueBits());

    constexpr size_t short_read_bytes = 16;
    auto buf = Alloc::alloc(bv.getFileBytes(), 256_Mi);
    memcpy(buf.get(), bv.getStart(), bv.getFileBytes());
    ASSERT_LT(short_read_bytes, bv.getFileBytes());
    memset(static_cast<char*>(buf.get()) + short_read_bytes, 0xff, bv.getFileBytes() - short_read_bytes);
    constexpr BitVector::Index ignored_true_bits = 42;
    AllocatedBitVector bv2(bv.size(), std::move(buf), 0, short_read_bytes, ignored_true_bits);
    EXPECT_EQ(1, bv2.countTrueBits());
    EXPECT_TRUE(bv2.testBit(bv2.size()));
    EXPECT_TRUE(bv == bv2);
}

TEST_F(BitVectorTest, normal_guard_bits)
{
    for (uint32_t num_end_padding_bits = 0; num_end_padding_bits < 2; ++num_end_padding_bits) {
        auto bv_size = 2048u - BitVector::num_guard_bits - num_end_padding_bits;
        SCOPED_TRACE("bv_size=" + std::to_string(bv_size));
        AllocatedBitVector bv(bv_size);
        EXPECT_EQ(bv_size + num_end_padding_bits, bv.capacity());
        bv.clearInterval(0, bv_size);
        EXPECT_EQ(bv_size, bv.getFirstTrueBit(0));
        bv.setInterval(0, bv_size);
        if (BitVector::num_guard_bits > 1 || num_end_padding_bits != 0) {
            EXPECT_EQ(bv_size + 1, bv.getFirstFalseBit(0));
        }
    }
}

TEST_F(BitVectorTest, dynamic_guard_bits)
{
    vespalib::GenerationHolder g;
    for (uint32_t num_end_padding_bits = 0; num_end_padding_bits < 2; ++num_end_padding_bits) {
        auto bv_size = 2048u - BitVector::num_guard_bits - num_end_padding_bits;
        constexpr bool single_guard_bit = (BitVector::num_guard_bits == 1);
        /*
         * Even guard bits are set to 1 and odd guard bits are set to 0 when using multiple guard bits.
         * This avoids conflict between old and new guard bits when changing bitvector size by 1 and when
         * bit vector size is 1 less than capacity.
         */
        constexpr uint32_t slack = single_guard_bit ? 0 : 1;
        SCOPED_TRACE("bv_size=" + std::to_string(bv_size));
        GrowableBitVector bv(bv_size, bv_size, g, nullptr);
        EXPECT_EQ(bv_size + num_end_padding_bits, bv.writer().capacity());
        bv.writer().clearInterval(0, bv_size);
        // Only even guard bits are set to '1' when using multiple guard bits.
        EXPECT_EQ(bv_size + ((num_end_padding_bits == 0) ? 0 : slack), bv.reader().getFirstTrueBit(0));
        bv.shrink(257);
        EXPECT_EQ(257 + slack, bv.reader().getFirstTrueBit(0));
        bv.shrink(256);
        EXPECT_EQ(256, bv.reader().getFirstTrueBit(0));
        bv.shrink(255);
        EXPECT_EQ(255 + slack, bv.reader().getFirstTrueBit(0));
        bv.extend(bv_size);
        EXPECT_EQ(bv_size + num_end_padding_bits, bv.writer().capacity());
        bv.writer().setInterval(0, bv_size);
        if (BitVector::num_guard_bits > 1) {
            EXPECT_EQ(bv_size + ((num_end_padding_bits == 0) ? slack : 0), bv.reader().getFirstFalseBit(0));
        }
        bv.writer().clearBit(300);
        bv.shrink(257);
        /*
         * Only odd guard bits are set to '0' when using multiple guard bits.
         * No '0' guard bit is set and the whole cleared interval is overwritten by new '1'
         * guard bit when using single guard bit and shrinking size by 1.
         */
        EXPECT_EQ(single_guard_bit ? 258 : 257, bv.reader().getFirstFalseBit(0));
        bv.shrink(256);
        EXPECT_EQ(single_guard_bit ? 258 : 257, bv.reader().getFirstFalseBit(0));
        bv.shrink(255);
        EXPECT_EQ(single_guard_bit ? 258 : 255, bv.reader().getFirstFalseBit(0));
    }
    g.assign_generation(1);
    g.reclaim(2);
}

TEST_F(BitVectorTest, read_from_attriute_vector_file)
{
    vespalib::GenerationHolder g;
    std::string bvpath(testdata + "/bv");
    FastOS_File file;
    file.OpenReadOnly(bvpath.c_str());
    ASSERT_TRUE(file.IsOpened());
    GrowableBitVector bv(1, 1, g);
    bv.writer().clear();
    uint32_t numDocs = _file_bv->size();
    bv.extend(numDocs);
    auto entry_size = BitVector::legacy_num_bytes_with_single_guard_bit(numDocs);
    ASSERT_LE(entry_size, bv.writer().numBytes(bv.writer().size()));
    file.ReadBuf(bv.writer().getStart(), entry_size);
    bv.fixup_after_load();
    auto bv_snap = bv.make_snapshot(bv.writer().size());
    EXPECT_TRUE(*bv_snap == *_file_bv);
    g.assign_generation(1);
    g.reclaim(2);
}

class BitVectorCompatibilityTest : public BitVectorTest,
                                   public testing::WithParamInterface<uint32_t> {
protected:
    BitVectorCompatibilityTest();
    ~BitVectorCompatibilityTest() override;
};

BitVectorCompatibilityTest::BitVectorCompatibilityTest()
    : BitVectorTest(),
      testing::WithParamInterface<uint32_t>()
{
}

BitVectorCompatibilityTest::~BitVectorCompatibilityTest() = default;

INSTANTIATE_TEST_SUITE_P(BitVectorCompatibilityTest, BitVectorCompatibilityTest, testing::Values(1, 2),
                         param_as_string);

TEST_P(BitVectorCompatibilityTest, read_from_file_bitvector_dictionary_file)
{
    uint32_t old_guard_bits = GetParam();
    std::string bvpath(testdata + "/bv");
    Aligner aligner(file_alignment);
    size_t entry_size = aligner.align(BitVector::num_bytes_plain(_file_bv->size() + old_guard_bits));
    EXPECT_EQ(old_guard_bits == 1 ? 256 : 512, entry_size);
    if (old_guard_bits == BitVector::num_guard_bits) {
        EXPECT_EQ(entry_size, _file_bv->getFileBytes());
    }
    FastOS_File file;
    file.OpenReadOnly(bvpath.c_str());
    ASSERT_TRUE(file.IsOpened());
    ReadStats read_stats;
    auto bv = BitVector::create(_file_bv->size(), file, 0, entry_size, _file_bv->countTrueBits(), read_stats);
    ASSERT_TRUE(bv);
    EXPECT_TRUE(*bv == *_file_bv);
}

TEST_P(BitVectorCompatibilityTest, read_from_nbostream)
{
    uint32_t old_guard_bits = GetParam();
    auto nbos = write_test_bv_to_nbostream(*_file_bv, old_guard_bits, file_alignment);
    if (old_guard_bits == BitVector::num_guard_bits) {
        EXPECT_EQ(_file_bv->getFileBytes() + 3 * sizeof(uint64_t), nbos->size());
    } else {
        EXPECT_NE(_file_bv->getFileBytes() + 3 * sizeof(uint64_t), nbos->size());
    }
    AllocatedBitVector bv(1u);
    *nbos >> bv;
    EXPECT_TRUE(bv == *_file_bv);
    EXPECT_EQ(0u, nbos->size());
}

GTEST_MAIN_RUN_ALL_TESTS()
