// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/searchlib/common/partialbitvector.h>
#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/exceptions.h>
#include <algorithm>

using namespace search;

using vespalib::alloc::Alloc;
using vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MemoryAllocatorObserver::Stats;

namespace {

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
    EXPECT_EQUAL(bv->getFirstTrueBit(), bv->getNextTrueBit(bv->getStartIndex()));
    uint32_t prevLid = bv->getStartIndex();
    for (auto lid : lids) {
        EXPECT_EQUAL(lid, bv->getNextTrueBit(prevLid + 1));
        EXPECT_EQUAL(prevLid, bv->getPrevTrueBit(lid - 1));
        prevLid = lid;
    }
    EXPECT_TRUE(bv->getNextTrueBit(prevLid + 1) >= end);
    EXPECT_EQUAL(prevLid, bv->getPrevTrueBit(end - 1));
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

}

bool
assertBV(const std::string & exp, const BitVector & act)
{
    bool res1 = EXPECT_EQUAL(exp, toString(act));
    search::fef::TermFieldMatchData f;
    queryeval::SearchIterator::UP it(BitVectorIterator::create(&act, f, true));
    auto & b(dynamic_cast<BitVectorIterator &>(*it));
    bool res2 = EXPECT_EQUAL(exp, toString(b));
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

vespalib::string
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

TEST("requireThatSequentialOperationsOnPartialWorks")
{
    PartialBitVector p1(717,919);

    EXPECT_FALSE(p1.hasTrueBits());
    EXPECT_EQUAL(0u, p1.countTrueBits());
    p1.setBit(719);
    EXPECT_EQUAL(0u, p1.countTrueBits());
    p1.invalidateCachedCount();
    EXPECT_TRUE(p1.hasTrueBits());
    EXPECT_EQUAL(1u, p1.countTrueBits());
    p1.setBitAndMaintainCount(718);
    p1.setBitAndMaintainCount(739);
    p1.setBitAndMaintainCount(871);
    p1.setBitAndMaintainCount(903);
    EXPECT_EQUAL(5u, p1.countTrueBits());
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
    EXPECT_EQUAL(5u, p2.countTrueBits());
    p2.orWith(full);
    EXPECT_EQUAL(202u, p2.countTrueBits());

    AllocatedBitVector before(100);
    before.setInterval(0, 100);
    p2.orWith(before);
    EXPECT_EQUAL(202u, p2.countTrueBits());

    PartialBitVector after(1000, 1100);
    after.setInterval(1000, 1100);
    EXPECT_EQUAL(202u, p2.countTrueBits());
}

TEST("requireThatInitRangeStaysWithinBounds") {
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
    EXPECT_EQUAL(bSmall->countTrueBits(), bLarger->countTrueBits());

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
    EXPECT_EQUAL(CNT, left->countTrueBits());
    EXPECT_EQUAL(CNT, right->countTrueBits());
    func(*left, *right);
    EXPECT_EQUAL(clear ? 0 : CNT, left->countTrueBits());
    EXPECT_EQUAL(CNT, right->countTrueBits());
    left = createEveryNthBitSet(3, 1000, 100);
    right = createEveryNthBitSet(3, 2000, 100);
    EXPECT_EQUAL(CNT, left->countTrueBits());
    EXPECT_EQUAL(CNT, right->countTrueBits());
    func(*right, *left);
    EXPECT_EQUAL(CNT, left->countTrueBits());
    EXPECT_EQUAL(clear ? 0 : CNT, right->countTrueBits());
}

TEST("requireThatAndWorks") {
    for (uint32_t offset(0); offset < 100; offset++) {
        testAnd(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.andWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(true, [](BitVector & a, const BitVector & b) { a.andWith(b); });
}

TEST("requireThatOrWorks") {
    for (uint32_t offset(0); offset < 100; offset++) {
        testOr(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.orWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(false, [](BitVector & a, const BitVector & b) { a.orWith(b); });
}


TEST("requireThatAndNotWorks") {
    for (uint32_t offset(0); offset < 100; offset++) {
        testAndNot(offset);
        verifyThatLongerWithShorterWorksAsZeroPadded(offset, offset+256, offset+256 + offset + 3,
                                                     [](BitVector & a, const BitVector & b) { a.andNotWith(b); });
    }
    verifyNonOverlappingWorksAsZeroPadded(false, [](BitVector & a, const BitVector & b) { a.andNotWith(b); });
}

TEST("test that empty bitvectors does not crash") {
    BitVector::UP empty = BitVector::create(0);
    EXPECT_EQUAL(0u, empty->countTrueBits());
    EXPECT_EQUAL(0u, empty->countInterval(0, 100));
    empty->setInterval(0,17);
    EXPECT_EQUAL(0u, empty->countInterval(0, 100));
    empty->clearInterval(0,17);
    EXPECT_EQUAL(0u, empty->countInterval(0, 100));
    empty->notSelf();
    EXPECT_EQUAL(0u, empty->countInterval(0, 100));
}

TEST("requireThatNotWorks") {
    for (uint32_t offset(0); offset < 100; offset++) {
        testNot(offset);
    }
}

TEST("requireThatClearWorks")
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

TEST("requireThatForEachWorks") {
    AllocatedBitVector v1(128);

    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    EXPECT_EQUAL(128u, v1.size());

    size_t sum(0);
    v1.foreach_truebit([&](uint32_t key) { sum += key; });
    EXPECT_EQUAL(220u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 7);
    EXPECT_EQUAL(220u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 6, 7);
    EXPECT_EQUAL(0u, sum);
    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 7, 8);
    EXPECT_EQUAL(7u, sum);
    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 8, 9);
    EXPECT_EQUAL(0u, sum);

    sum = 0;
    v1.foreach_truebit([&](uint32_t key) { sum += key; }, 8);
    EXPECT_EQUAL(213u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 6);
    EXPECT_EQUAL(5u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 7);
    EXPECT_EQUAL(11u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 8);
    EXPECT_EQUAL(11u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 5, 9);
    EXPECT_EQUAL(19u, sum);

    sum = 0;
    v1.foreach_falsebit([&](uint32_t key) { sum += key; }, 6);
    EXPECT_EQUAL(size_t((((6+127)*(127-6 + 1)) >> 1) - 220), sum);
}


TEST("requireThatSetWorks")
{
    AllocatedBitVector v1(128);

    v1.setBit(7);
    v1.setBit(39);
    v1.setBit(71);
    v1.setBit(103);
    EXPECT_TRUE(assertBV("[7,39,71,103]", v1));
    v1.invalidateCachedCount();
    EXPECT_EQUAL(4u, v1.countTrueBits());

    v1.setBit(80);
    EXPECT_EQUAL(4u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQUAL(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,71,80,103]", v1));

    v1.clearBit(35);
    EXPECT_EQUAL(5u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQUAL(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,71,80,103]", v1));
    v1.clearBit(71);
    EXPECT_EQUAL(5u, v1.countTrueBits());
    v1.invalidateCachedCount();
    EXPECT_EQUAL(4u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,80,103]", v1));

    v1.setBitAndMaintainCount(39);
    EXPECT_EQUAL(4u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,80,103]", v1));
    v1.setBitAndMaintainCount(57);
    EXPECT_EQUAL(5u, v1.countTrueBits());
    EXPECT_TRUE(assertBV("[7,39,57,80,103]", v1));
}

TEST("test BitWord::startBits/endBits") {
    EXPECT_EQUAL(BitWord::startBits(0),  0x00ul);
    EXPECT_EQUAL(BitWord::startBits(1),  0x01ul);
    EXPECT_EQUAL(BitWord::startBits(2),  0x03ul);
    EXPECT_EQUAL(BitWord::startBits(61), 0x1ffffffffffffffful);
    EXPECT_EQUAL(BitWord::startBits(62), 0x3ffffffffffffffful);
    EXPECT_EQUAL(BitWord::startBits(63), 0x7ffffffffffffffful);
    EXPECT_EQUAL(BitWord::endBits(0),  0xfffffffffffffffeul);
    EXPECT_EQUAL(BitWord::endBits(1),  0xfffffffffffffffcul);
    EXPECT_EQUAL(BitWord::endBits(2),  0xfffffffffffffff8ul);
    EXPECT_EQUAL(BitWord::endBits(61), 0xc000000000000000ul);
    EXPECT_EQUAL(BitWord::endBits(62), 0x8000000000000000ul);
    EXPECT_EQUAL(BitWord::endBits(63), 0x0000000000000000ul);
}

TEST("requireThatClearIntervalWorks")
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
            EXPECT_EQUAL(400u, v.countTrueBits());

            v.clearInterval(offset, offset+intervalLength);
            EXPECT_FALSE(v.testBit(offset));
            EXPECT_TRUE(v.testBit(offset-1));
            EXPECT_FALSE(v.testBit(offset+intervalLength-1));
            EXPECT_TRUE(v.testBit(offset+intervalLength));
            EXPECT_EQUAL(400 - intervalLength, v.countTrueBits());
        }
    }
}


TEST("requireThatSetIntervalWorks")
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
    EXPECT_EQUAL(12u, v1.countTrueBits());
    EXPECT_EQUAL(12u, v1.countInterval(1, 1199));
    EXPECT_EQUAL(12u, myCountInterval(v1, 1, 1199));
    
    v1.setInterval(40, 200);
    EXPECT_EQUAL(164u, v1.countInterval(1, 1199));
    EXPECT_EQUAL(164u, myCountInterval(v1, 1, 1199));
    EXPECT_EQUAL(163u, v1.countInterval(1, 201));
    EXPECT_EQUAL(162u, v1.countInterval(1, 200));
    EXPECT_EQUAL(163u, v1.countInterval(7, 201));
    EXPECT_EQUAL(162u, v1.countInterval(8, 201));
    EXPECT_EQUAL(161u, v1.countInterval(8, 200));
    v1.clearInterval(72, 174);
    EXPECT_EQUAL(62u, v1.countInterval(1, 1199));
    EXPECT_EQUAL(62u, myCountInterval(v1, 1, 1199));
    EXPECT_EQUAL(61u, v1.countInterval(1, 201));
    EXPECT_EQUAL(60u, v1.countInterval(1, 200));
    EXPECT_EQUAL(61u, v1.countInterval(7, 201));
    EXPECT_EQUAL(60u, v1.countInterval(8, 201));
    EXPECT_EQUAL(59u, v1.countInterval(8, 200));
    EXPECT_EQUAL(51u, v1.countInterval(8, 192));
    EXPECT_EQUAL(50u, v1.countInterval(8, 191));

    EXPECT_EQUAL(1u, v1.countInterval(1, 20));
    EXPECT_EQUAL(1u, v1.countInterval(7, 20));
    EXPECT_EQUAL(0u, v1.countInterval(8, 20));
    EXPECT_EQUAL(1u, v1.countInterval(1, 8));
    EXPECT_EQUAL(0u, v1.countInterval(1, 7));
}

TEST("requireThatScanWorks")
{
    scanWithOffset(0);
    scanWithOffset(19876);
}

TEST("requireThatGrowWorks")
{
    vespalib::GenerationHolder g;
    GrowableBitVector v(200, 200, g);
    EXPECT_EQUAL(0u, v.writer().countTrueBits());
    
    v.writer().setBitAndMaintainCount(7);
    v.writer().setBitAndMaintainCount(39);
    v.writer().setBitAndMaintainCount(71);
    v.writer().setBitAndMaintainCount(103);
    EXPECT_EQUAL(4u, v.writer().countTrueBits());

    EXPECT_EQUAL(200u, v.reader().size());
    EXPECT_EQUAL(1023u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());
    EXPECT_TRUE(v.reserve(1024));
    EXPECT_EQUAL(200u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.extend(202));
    EXPECT_EQUAL(202u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.shrink(200));
    EXPECT_EQUAL(200u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.reserve(2047));
    EXPECT_EQUAL(200u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());
    EXPECT_FALSE(v.shrink(202));
    EXPECT_EQUAL(202u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71,103]", v.reader()));
    EXPECT_EQUAL(4u, v.writer().countTrueBits());

    EXPECT_FALSE(v.shrink(100));
    EXPECT_EQUAL(100u, v.reader().size()); 
    EXPECT_EQUAL(2047u, v.writer().capacity()); 
    EXPECT_TRUE(assertBV("[7,39,71]", v.reader()));
    EXPECT_EQUAL(3u, v.writer().countTrueBits());

    v.writer().invalidateCachedCount();
    EXPECT_TRUE(v.reserve(3100));
    EXPECT_EQUAL(100u, v.reader().size());
    EXPECT_EQUAL(4095u, v.writer().capacity());
    EXPECT_EQUAL(3u, v.writer().countTrueBits());

    g.assign_generation(1);
    g.reclaim(2);
}

TEST("require that growable bit vectors keeps memory allocator")
{
    AllocStats stats;
    auto memory_allocator = std::make_unique<MemoryAllocatorObserver>(stats);
    Alloc init_alloc = Alloc::alloc_with_allocator(memory_allocator.get());
    vespalib::GenerationHolder g;
    GrowableBitVector v(200, 200, g, &init_alloc);
    EXPECT_EQUAL(AllocStats(1, 0), stats);
    v.writer().resize(1); // DO NOT TRY THIS AT HOME
    EXPECT_EQUAL(AllocStats(2, 1), stats);
    v.reserve(2000);
    EXPECT_EQUAL(AllocStats(3, 1), stats);
    v.extend(4000);
    EXPECT_EQUAL(AllocStats(4, 1), stats);
    v.shrink(200);
    EXPECT_EQUAL(AllocStats(4, 1), stats);
    v.writer().resize(1); // DO NOT TRY THIS AT HOME
    EXPECT_EQUAL(AllocStats(5, 2), stats);
    g.assign_generation(1);
    g.reclaim(2);
}

TEST("require that creating partial nonoverlapping vector is cleared") {
    AllocatedBitVector org(1000);
    org.setInterval(org.getStartIndex(), org.size());
    EXPECT_EQUAL(1000u, org.countTrueBits());

    BitVector::UP after = BitVector::create(org, 2000, 3000);
    EXPECT_EQUAL(2000u, after->getStartIndex());
    EXPECT_EQUAL(3000u, after->size());
    EXPECT_EQUAL(0u, after->countTrueBits());

    BitVector::UP before = BitVector::create(*after, 0, 1000);
    EXPECT_EQUAL(0u, before->getStartIndex());
    EXPECT_EQUAL(1000u, before->size());
    EXPECT_EQUAL(0u, before->countTrueBits());
}

TEST("require that creating partial overlapping vector is properly copied") {
    AllocatedBitVector org(1000);
    org.setInterval(org.getStartIndex(), org.size());
    EXPECT_EQUAL(1000u, org.countTrueBits());

    BitVector::UP after = BitVector::create(org, 900, 1100);
    EXPECT_EQUAL(900u, after->getStartIndex());
    EXPECT_EQUAL(1100u, after->size());
    EXPECT_EQUAL(100u, after->countTrueBits());

    BitVector::UP before = BitVector::create(*after, 0, 1000);
    EXPECT_EQUAL(0u, before->getStartIndex());
    EXPECT_EQUAL(1000u, before->size());
    EXPECT_EQUAL(100u, before->countTrueBits());
}

TEST_MAIN() { TEST_RUN_ALL(); }
