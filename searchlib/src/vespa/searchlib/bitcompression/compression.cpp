// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compression.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search::bitcompression {

uint64_t CodingTables::_intMask64[65] =
{
    (UINT64_C(1) <<  0) - 1, (UINT64_C(1) <<  1) - 1,
    (UINT64_C(1) <<  2) - 1, (UINT64_C(1) <<  3) - 1,
    (UINT64_C(1) <<  4) - 1, (UINT64_C(1) <<  5) - 1,
    (UINT64_C(1) <<  6) - 1, (UINT64_C(1) <<  7) - 1,
    (UINT64_C(1) <<  8) - 1, (UINT64_C(1) <<  9) - 1,
    (UINT64_C(1) << 10) - 1, (UINT64_C(1) << 11) - 1,
    (UINT64_C(1) << 12) - 1, (UINT64_C(1) << 13) - 1,
    (UINT64_C(1) << 14) - 1, (UINT64_C(1) << 15) - 1,
    (UINT64_C(1) << 16) - 1, (UINT64_C(1) << 17) - 1,
    (UINT64_C(1) << 18) - 1, (UINT64_C(1) << 19) - 1,
    (UINT64_C(1) << 20) - 1, (UINT64_C(1) << 21) - 1,
    (UINT64_C(1) << 22) - 1, (UINT64_C(1) << 23) - 1,
    (UINT64_C(1) << 24) - 1, (UINT64_C(1) << 25) - 1,
    (UINT64_C(1) << 26) - 1, (UINT64_C(1) << 27) - 1,
    (UINT64_C(1) << 28) - 1, (UINT64_C(1) << 29) - 1,
    (UINT64_C(1) << 30) - 1, (UINT64_C(1) << 31) - 1,
    (UINT64_C(1) << 32) - 1, (UINT64_C(1) << 33) - 1,
    (UINT64_C(1) << 34) - 1, (UINT64_C(1) << 35) - 1,
    (UINT64_C(1) << 36) - 1, (UINT64_C(1) << 37) - 1,
    (UINT64_C(1) << 38) - 1, (UINT64_C(1) << 39) - 1,
    (UINT64_C(1) << 40) - 1, (UINT64_C(1) << 41) - 1,
    (UINT64_C(1) << 42) - 1, (UINT64_C(1) << 43) - 1,
    (UINT64_C(1) << 44) - 1, (UINT64_C(1) << 45) - 1,
    (UINT64_C(1) << 46) - 1, (UINT64_C(1) << 47) - 1,
    (UINT64_C(1) << 48) - 1, (UINT64_C(1) << 49) - 1,
    (UINT64_C(1) << 50) - 1, (UINT64_C(1) << 51) - 1,
    (UINT64_C(1) << 52) - 1, (UINT64_C(1) << 53) - 1,
    (UINT64_C(1) << 54) - 1, (UINT64_C(1) << 55) - 1,
    (UINT64_C(1) << 56) - 1, (UINT64_C(1) << 57) - 1,
    (UINT64_C(1) << 58) - 1, (UINT64_C(1) << 59) - 1,
    (UINT64_C(1) << 60) - 1, (UINT64_C(1) << 61) - 1,
    (UINT64_C(1) << 62) - 1, (UINT64_C(1) << 63) - 1,
    static_cast<uint64_t>(-1),
};


uint64_t
CodingTables::_intMask64le[65] =
{
    /**/               0, -(UINT64_C(1) << 63),
    -(UINT64_C(1) << 62), -(UINT64_C(1) << 61),
    -(UINT64_C(1) << 60), -(UINT64_C(1) << 59),
    -(UINT64_C(1) << 58), -(UINT64_C(1) << 57),
    -(UINT64_C(1) << 56), -(UINT64_C(1) << 55),
    -(UINT64_C(1) << 54), -(UINT64_C(1) << 53),
    -(UINT64_C(1) << 52), -(UINT64_C(1) << 51),
    -(UINT64_C(1) << 50), -(UINT64_C(1) << 49),
    -(UINT64_C(1) << 48), -(UINT64_C(1) << 47),
    -(UINT64_C(1) << 46), -(UINT64_C(1) << 45),
    -(UINT64_C(1) << 44), -(UINT64_C(1) << 43),
    -(UINT64_C(1) << 42), -(UINT64_C(1) << 41),
    -(UINT64_C(1) << 40), -(UINT64_C(1) << 39),
    -(UINT64_C(1) << 38), -(UINT64_C(1) << 37),
    -(UINT64_C(1) << 36), -(UINT64_C(1) << 35),
    -(UINT64_C(1) << 34), -(UINT64_C(1) << 33),
    -(UINT64_C(1) << 32), -(UINT64_C(1) << 31),
    -(UINT64_C(1) << 30), -(UINT64_C(1) << 29),
    -(UINT64_C(1) << 28), -(UINT64_C(1) << 27),
    -(UINT64_C(1) << 26), -(UINT64_C(1) << 25),
    -(UINT64_C(1) << 24), -(UINT64_C(1) << 23),
    -(UINT64_C(1) << 22), -(UINT64_C(1) << 21),
    -(UINT64_C(1) << 20), -(UINT64_C(1) << 19),
    -(UINT64_C(1) << 18), -(UINT64_C(1) << 17),
    -(UINT64_C(1) << 16), -(UINT64_C(1) << 15),
    -(UINT64_C(1) << 14), -(UINT64_C(1) << 13),
    -(UINT64_C(1) << 12), -(UINT64_C(1) << 11),
    -(UINT64_C(1) << 10), -(UINT64_C(1) <<  9),
    -(UINT64_C(1) <<  8), -(UINT64_C(1) <<  7),
    -(UINT64_C(1) <<  6), -(UINT64_C(1) <<  5),
    -(UINT64_C(1) <<  4), -(UINT64_C(1) <<  3),
    -(UINT64_C(1) <<  2), -(UINT64_C(1) <<  1),
    static_cast<uint64_t>(-1),
};


template <>
void
EncodeContext64EBase<false>::writeBits(uint64_t data, uint32_t length)
{
    // While there are enough bits remaining in "data",
    // fill the cacheInt and flush it to vector
    if (length >= _cacheFree) {
        // Shift new bits into cacheInt
        _cacheInt |= (data << (64 - _cacheFree));
        *_valI++ = bswap(_cacheInt);
        data = (_cacheFree < 64) ? data >> _cacheFree : 0;
        // Initialize variables for receiving new bits
        length -= _cacheFree;
        _cacheInt = 0;
        _cacheFree = 64;
    }

    if (length > 0) {
        uint64_t dataFragment = (data & CodingTables::_intMask64[length]);
        _cacheInt |= (dataFragment << (64 - _cacheFree));
        _cacheFree -= length;
    }
}

namespace {

vespalib::string noFeatures = "NoFeatures";

}

void
DecodeContext64Base::readBytes(uint8_t *buf, size_t len)
{
    while (len > 0) {
        // Ensure that buffer to read from isn't empty
        if (__builtin_expect(_valI >= _valE, false)) {
            _readContext->readComprBuffer();
        }
        uint64_t readOffset = getReadOffset();
        // Validate that read offset is byte aligned
        assert((readOffset & 7) == 0);
        // Get start and end of buffer to read from, then calculate size
        const uint8_t *rbuf = reinterpret_cast<const uint8_t *>(getCompr()) +
                              (getBitOffset() >> 3);
        const uint8_t *rbufE = reinterpret_cast<const uint8_t *>(_realValE);
        size_t rbufSize = rbufE - rbuf; // Size of buffer to read from
        // How much to copy in this iteration of the loop
        size_t copySize = std::min(rbufSize, len);
        // Something must be copied during each iteration
        assert(copySize > 0);
        memcpy(buf, rbuf, copySize);
        buf += copySize;
        len -= copySize;
        // Adjust read position to account for bytes read
        _readContext->setPosition(readOffset + copySize * 8);
    }
    if (__builtin_expect(_valI >= _valE, false)) {
        _readContext->readComprBuffer();
    }
}


uint32_t
DecodeContext64Base::
readHeader(vespalib::GenericHeader &header, int64_t fileSize)
{
    size_t hhSize = vespalib::GenericHeader::getMinSize();
    assert(static_cast<int64_t>(hhSize) <= fileSize);
    vespalib::DataBuffer dataBuffer(32_Ki);
    dataBuffer.ensureFree(hhSize);
    readBytes(reinterpret_cast<uint8_t *>(dataBuffer.getFree()),
              hhSize);
    dataBuffer.moveFreeToData(hhSize);
    vespalib::GenericHeader::BufferReader bufferReader(dataBuffer);
    uint32_t headerLen = vespalib::GenericHeader::readSize(bufferReader);
    // Undo read from buffer
    dataBuffer.moveDeadToData(hhSize - dataBuffer.getDataLen());
    assert(headerLen <= fileSize);
    (void) fileSize;
    if (headerLen > hhSize) {
        // Read remaining header into buffer
        dataBuffer.ensureFree(headerLen - hhSize);
        readBytes(reinterpret_cast<uint8_t *>(dataBuffer.getFree()),
                  headerLen - hhSize);
        dataBuffer.moveFreeToData(headerLen - hhSize);
    }
    uint32_t len = header.read(bufferReader);
    (void) len;
    assert(len >= header.getSize());
    assert(len == headerLen);
    return headerLen;
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
writeBits(const uint64_t *bits, uint32_t bitOffset, uint32_t bitLength)
{
    typedef FeatureEncodeContext<bigEndian> EC;
    UC64_ENCODECONTEXT_CONSTRUCTOR(o, _);

    if (bitOffset + bitLength < 64) {
        uint32_t length = bitLength;
        if (bigEndian) {
            uint64_t data = ((bitOffset + length) > 0)
                ? (EC::bswap(*bits) >> (64 - bitOffset - length)) & CodingTables::_intMask64[length]
                : 0;
            UC64BE_WRITEBITS_NS(o, EC);
        } else {
            uint64_t data = (EC::bswap(*bits) >> bitOffset) &
                            CodingTables::_intMask64[length];
            UC64LE_WRITEBITS_NS(o, EC);
        }
    } else {
        uint32_t bitsLeft = bitLength;
        do {
            uint32_t length = 64 - bitOffset;
            bitsLeft -= length;
            if (bigEndian) {
                uint64_t data = EC::bswap(*bits) &
                                CodingTables::_intMask64[length];
                UC64BE_WRITEBITS_NS(o, EC);
            } else {
                uint64_t data = (EC::bswap(*bits) >> bitOffset) &
                                CodingTables::_intMask64[length];
                UC64LE_WRITEBITS_NS(o, EC);
            }
            ++bits;
        } while (0);
        while (bitsLeft >= 64) {
            uint32_t length = 64;
            uint64_t data = EC::bswap(*bits);
            UC64_WRITEBITS_NS(o, EC);
            ++bits;
            bitsLeft -= 64;
            if (__builtin_expect(oBufI >= _valE, false)) {
                UC64_ENCODECONTEXT_STORE(o, _);
                _writeContext->writeComprBuffer(false);
                UC64_ENCODECONTEXT_LOAD(o, _);
            }
        }
        if (bitsLeft > 0) {
            uint32_t length = bitsLeft;
            if (bigEndian) {
                uint64_t data = EC::bswap(*bits) >> (64 - length);
                UC64BE_WRITEBITS_NS(o, EC);
            } else {
                uint64_t data = EC::bswap(*bits) &
                                CodingTables::_intMask64[length];
                UC64LE_WRITEBITS_NS(o, EC);
            }
        }
    }
    UC64_ENCODECONTEXT_STORE(o, _);
    if (__builtin_expect(oBufI >= _valE, false)) {
        _writeContext->writeComprBuffer(false);
    }
}

template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::writeBytes(vespalib::ConstArrayRef<char> buf)
{
    for (unsigned char c : buf) {
        writeBits(c, 8);
        if (__builtin_expect(_valI >= _valE, false)) {
            _writeContext->writeComprBuffer(false);
        }
    }
}

template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
writeString(vespalib::stringref buf)
{
    size_t len = buf.size();
    for (unsigned int i = 0; i < len; ++i) {
        writeBits(static_cast<unsigned char>(buf[i]), 8);
        if (__builtin_expect(_valI >= _valE, false)) {
            _writeContext->writeComprBuffer(false);
        }
    }
    writeBits(0, 8);
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
writeHeader(const vespalib::GenericHeader &header)
{
    vespalib::DataBuffer dataBuffer(32_Ki);
    vespalib::GenericHeader::BufferWriter bufferWriter(dataBuffer);
    dataBuffer.ensureFree(header.getSize());
    header.write(bufferWriter);
    const uint8_t *data = reinterpret_cast<const uint8_t *>
                          (dataBuffer.getData());
    uint32_t offset = (reinterpret_cast<unsigned long>(data) & 7);
    data -= offset;
    uint32_t bitOffset = offset * 8;
    uint32_t bitLen = dataBuffer.getDataLen() * 8;
    writeBits(reinterpret_cast<const uint64_t *>(data), bitOffset, bitLen);
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
            const vespalib::string &prefix)
{
    (void) header;
    (void) prefix;
}


template <bool bigEndian>
const vespalib::string &
FeatureDecodeContext<bigEndian>::getIdentifier() const
{
    return noFeatures;
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::readFeatures(DocIdAndFeatures &features)
{
    (void) features;
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::skipFeatures(unsigned int count)
{
    (void) count;
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::
unpackFeatures(const search::fef::TermFieldMatchDataArray &matchData,
               uint32_t docId)
{
    if (matchData.size() == 1) {
        matchData[0]->reset(docId);
    }
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    (void) params;
}


template <bool bigEndian>
void
FeatureDecodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    params.clear();
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
readHeader(const vespalib::GenericHeader &header,
           const vespalib::string &prefix)
{
    (void) header;
    (void) prefix;
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
writeHeader(vespalib::GenericHeader &header,
            const vespalib::string &prefix) const
{
    (void) header;
    (void) prefix;
}


template <bool bigEndian>
const vespalib::string &
FeatureEncodeContext<bigEndian>::getIdentifier() const
{
    return noFeatures;
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::writeFeatures(const DocIdAndFeatures &features)
{
    (void) features;
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
setParams(const PostingListParams &params)
{
    (void) params;
}


template <bool bigEndian>
void
FeatureEncodeContext<bigEndian>::
getParams(PostingListParams &params) const
{
    params.clear();
}

template <>
void
EncodeContext64EBase<true>::writeBits(uint64_t data, uint32_t length)
{
    // While there are enough bits remaining in "data",
    // fill the cacheInt and flush it to vector
    if (length >= _cacheFree) {
        // Shift new bits into cacheInt
        _cacheInt |= ((data >> (length - _cacheFree)) &
                      CodingTables::_intMask64[_cacheFree]);
        *_valI++ = bswap(_cacheInt);

        // Initialize variables for receiving new bits
        length -= _cacheFree;
        _cacheInt = 0;
        _cacheFree = 64;
    }

    if (length > 0) {
        uint64_t dataFragment = (data & CodingTables::_intMask64[length]);
        _cacheInt |= (dataFragment << (_cacheFree - length));
        _cacheFree -= length;
    }
}

template class FeatureDecodeContext<true>;
template class FeatureDecodeContext<false>;

template class FeatureEncodeContext<true>;
template class FeatureEncodeContext<false>;

}
