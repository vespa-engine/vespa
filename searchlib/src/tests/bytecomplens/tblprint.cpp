// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("tblprint");
#include <vespa/vespalib/util/random.h>

#include <vector>
#include <vespa/vespalib/data/databuffer.h>


/**
 * Class compressing a table of offsets in memory.
 * After adding (n) offsets you can access
 * (n-1) pairs of (length, offset).
 * All offsets must be increasing, but they
 * may be added in several chunks.
 **/
class ByteCompressedLengths
{
public:
    /**
     * Construct an empty instance
     **/
    ByteCompressedLengths();

    /**
     * add the given offset table.
     * @param entries number of offsets to store.
     * @param offsets table that contains (entries) offsets.
     **/
    void addOffsetTable(uint64_t entries, uint64_t *offsets);

    /**
     * free resources
     **/
    ~ByteCompressedLengths();

    /**
     * Fetch a length and offset from compressed data.
     * Note invariant: id < size(); size() == (entries-1)
     *
     * @param id The index into the offset table
     * @param offset Will be incremented by offset[id]
     * @return The delta (offset[id+1] - offset[id])
     **/
    uint64_t getLength(uint64_t id, uint64_t &offset) const;

    /**
     * The number of (length, offset) pairs stored
     **/
    uint64_t size() const { return _entries; }

    struct L3Entry {
        uint64_t offset;
        uint64_t l0toff;
        uint64_t l1toff;
        uint64_t l2toff;
    };
    vespalib::DataBuffer _l0space;
    vespalib::DataBuffer _l1space;
    vespalib::DataBuffer _l2space;
    const uint8_t *_l0table;
    const uint8_t *_l1table;
    const uint8_t *_l2table;

    std::vector<L3Entry> _l3table;

    uint64_t _lenSum1;
    uint64_t _lenSum2;
    uint64_t _l0oSum1;
    uint64_t _l0oSum2;
    uint64_t _l1oSum2;
    uint64_t _last_offset;
    uint64_t _entries;

    void addOffset(uint64_t offset);
};

/**
 * get "Byte Compressed Number" from buffer, incrementing pointer
 **/
static inline uint64_t getBCN(const uint8_t *&buffer)
{
    uint8_t b = *buffer++;
    uint64_t len = (b & 127);
    unsigned shiftLen = 0;
    while (b & 128) {
        shiftLen += 7;
        b = *buffer++;
        len |= ((b & 127) << shiftLen);
    }
    return len;
}

static size_t writeLen(vespalib::DataBuffer &buf, uint64_t len)
{
    size_t bytes = 0;
    do {
        uint8_t b = len & 127;
        len >>= 7;
        if (len > 0) {
            b |= 128;
        }
        buf.ensureFree(1);
        buf.writeInt8(b);
        ++bytes;
    } while (len > 0);
    return bytes;
}


ByteCompressedLengths::ByteCompressedLengths()
    : _l0space(),
      _l1space(),
      _l2space(),
      _l3table(),
      _lenSum1(0),
      _lenSum2(0),
      _l0oSum1(0),
      _l0oSum2(0),
      _l1oSum2(0),
      _last_offset(0),
      _entries(0)
{
}


void
ByteCompressedLengths::addOffset(uint64_t offset)
{
    assert(offset >= _last_offset);

    uint64_t len = offset - _last_offset;
    uint64_t i = _entries++;

    if ((i & 3) == 0) {
        _lenSum2 += _lenSum1;
        _l0oSum2 += _l0oSum1;

        uint64_t t1n = i >> 2;
        if ((t1n & 3) == 0) {
            uint64_t t2n = t1n >> 2;

            if ((t2n & 3) == 0) {
                L3Entry e;
                e.offset = _last_offset;
                e.l0toff = _l0space.getDataLen();
                e.l1toff = _l1space.getDataLen();
                e.l2toff = _l2space.getDataLen();

                _l3table.push_back(e);
            } else {
                writeLen(_l2space, _lenSum2);
                writeLen(_l2space, _l0oSum2);
                writeLen(_l2space, _l1oSum2);
            }
            _lenSum2 = 0;
            _l0oSum2 = 0;
            _l1oSum2 = 0;
        } else {
            _l1oSum2 += writeLen(_l1space, _lenSum1);
            _l1oSum2 += writeLen(_l1space, _l0oSum1);
        }
        _lenSum1 = 0;
        _l0oSum1 = 0;
    }
    _l0oSum1 += writeLen(_l0space, len);
    _lenSum1 += len;
    _last_offset = offset;
}


void
ByteCompressedLengths::addOffsetTable(uint64_t entries, uint64_t *offsets)
{
    if (entries == 0) return;
    // Do we have some offsets already?
    if (_entries > 0) {
        // yes, add first offset normally
        addOffset(offsets[0]);
    } else {
        // no, special treatment for very first offset
        _last_offset = offsets[0];
    }
    for (uint64_t cnt = 1; cnt < entries; ++cnt) {
        addOffset(offsets[cnt]);
    }
    _l0table = (uint8_t *)_l0space.getData();
    _l1table = (uint8_t *)_l1space.getData();
    _l2table = (uint8_t *)_l2space.getData();

    LOG(debug, "compressed %ld offsets", (_entries+1));
    LOG(debug, "(%ld bytes)", (_entries+1)*sizeof(uint64_t));
    LOG(debug, "to (%ld + %ld + %ld) bytes + %ld l3entries",
        _l0space.getDataLen(),
        _l1space.getDataLen(),
        _l2space.getDataLen(),
        _l3table.size());
    LOG(debug, "(%ld bytes)",
        (_l0space.getDataLen() + _l1space.getDataLen() + _l2space.getDataLen() +
         _l3table.size()*sizeof(L3Entry)));
}


ByteCompressedLengths::~ByteCompressedLengths()
{
}

uint64_t
ByteCompressedLengths::getLength(uint64_t numSkip, uint64_t &offset) const
{
    assert(numSkip < _entries);

    unsigned skipL0 = numSkip & 3;
    unsigned skipL1 = (numSkip >> 2) & 3;
    unsigned skipL2 = (numSkip >> 4) & 3;
    uint64_t skipL3 = (numSkip >> 6);

    offset += _l3table[skipL3].offset;
    uint64_t l0toff = _l3table[skipL3].l0toff;
    uint64_t l1toff = _l3table[skipL3].l1toff;
    uint64_t l2toff = _l3table[skipL3].l2toff;

    // printf("start off %ld l0off %ld l1off %ld l2off %ld\n", offset, l0toff, l1toff, l2toff);

    const uint8_t *l2pos = _l2table + l2toff;

    while (skipL2 > 0) {
        --skipL2;
        offset += getBCN(l2pos);
        l0toff += getBCN(l2pos);
        l1toff += getBCN(l2pos);
    }

    const uint8_t *l1pos = _l1table + l1toff;

    while (skipL1 > 0) {
        --skipL1;
        offset += getBCN(l1pos);
        l0toff += getBCN(l1pos);

    }
    const uint8_t *l0pos = _l0table + l0toff;

    while (skipL0 > 0) {
        --skipL0;
        offset += getBCN(l0pos);
    }
    // printf("end off %ld l0off %ld l1off %ld l2off %ld\n", offset, l0toff, l1toff, l2toff);
    return getBCN(l0pos);
}



class Test {
public:
    static void printTable();
};



int main(int /*argc*/, char ** /*argv*/)
{
    Test::printTable();
    return 0;
}

void
Test::printTable()
{
    vespalib::RandomGen rndgen(0x07031969);
#define TBLSIZ 120
    uint32_t *lentable = new uint32_t[TBLSIZ];
    uint64_t *offtable = new uint64_t[TBLSIZ];

    uint64_t offset = 16 + TBLSIZ*8;

    for (int i = 0; i < TBLSIZ; i++) {
        int sel = rndgen.nextInt32();
        int val = rndgen.nextInt32();
        switch (sel & 0x7) {
        case 0:
            val &= 0x7F;
            break;
        case 1:
            val &= 0xFF;
            break;
        case 3:
            val &= 0x1FFF;
            break;
        case 4:
            val &= 0x3FFF;
            break;
        case 5:
            val &= 0x7FFF;
            break;
        case 6:
            val &= 0xFFFF;
            break;
        case 7:
        default:
            val &= 0xFFFFF;
            break;
        }
        offtable[i] = offset;
        lentable[i] = val;
        offset += val;
    }

    ByteCompressedLengths foo;
    foo.addOffsetTable(TBLSIZ, offtable);

    const uint8_t *l1pos = foo._l1table;
    const uint8_t *l2pos = foo._l2table;

    printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
           "offset", "length", "BCN val", "L0 len/off", "skipL1", "skipL2", "skipL3");

    int slb = 0;
    for (int i = 0; i+1 < TBLSIZ; i++) {
        printf("%ld\t%d\t[", offtable[i], lentable[i]);
        int bytes=0;
        uint64_t len = lentable[i];
        do {
            uint8_t b = len & 127;
            len >>= 7;
            if (len > 0) {
                b |= 128;
            }
            printf(" %02X", b);
            ++bytes;
        } while (len > 0);
        printf(" ]\t%d", bytes);
        printf("/%d", slb);
        slb += bytes;

        if ((i & 63) == 0) {
            printf("\t\t\t%ld/%ld/%ld/%ld",
                   foo._l3table[i >> 6].offset,
                   foo._l3table[i >> 6].l0toff,
                   foo._l3table[i >> 6].l1toff,
                   foo._l3table[i >> 6].l2toff);
        } else
        if ((i & 15) == 0) {
            printf("\t\t%ld", getBCN(l2pos));
            printf("/%ld", getBCN(l2pos));
            printf("/%ld", getBCN(l2pos));
        } else
        if ((i & 3) == 0) {
            printf("\t%ld", getBCN(l1pos));
            printf("/%ld", getBCN(l1pos));
        }
        printf("\n");
    }
    printf("%ld\n", offtable[TBLSIZ-1]);
    fflush(stdout);
}
