// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
   @author Thomas F. Gundersen, �ystein Fledsberg
   @version $Id$
   @date 2004-03-15
*/

#include "bytebuffer.h"
#include "bufferexceptions.h"
#include "stringutil.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>
#include <arpa/inet.h>

#define LOG_DEBUG1(a)
// Enable this macros instead to see what bytebuffer calls come
//#define LOG_DEBUG1(a) std::cerr << "ByteBuffer(" << ((void*) this) << " " << a << ")\n";

#define LOG_DEBUG2(a,b) LOG_DEBUG1(vespalib::make_string(a,b));
#define LOG_DEBUG3(a,b,c) LOG_DEBUG1(vespalib::make_string(a,b,c));
#define LOG_DEBUG4(a,b,c,d) LOG_DEBUG1(vespalib::make_string(a,b,c,d));

using vespalib::alloc::Alloc;

namespace document {

VESPA_IMPLEMENT_EXCEPTION_SPINE(BufferOutOfBoundsException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(InputOutOfRangeException);

vespalib::string BufferOutOfBoundsException::createMessage(size_t pos, size_t len) {
    vespalib::asciistream ost;
    ost << pos << " > " << len;
    return ost.str();
}

BufferOutOfBoundsException::BufferOutOfBoundsException(
        size_t pos, size_t len, const vespalib::string& location)
    : IoException(createMessage(pos, len), IoException::NO_SPACE, location, 1)
{
}

InputOutOfRangeException::InputOutOfRangeException(
        const vespalib::string& msg, const vespalib::string& location)
    : IoException(msg, IoException::INTERNAL_FAILURE, location, 1)
{
}

ByteBuffer::ByteBuffer() :
      _buffer(NULL),
      _len(0),
      _pos(0),
      _limit(0),
      _bufHolder(NULL),
      _ownedBuffer()
{
    set(NULL, 0);
    LOG_DEBUG1("Created empty bytebuffer");
}

ByteBuffer::ByteBuffer(size_t len) :
    ByteBuffer(Alloc::alloc(len), len)
{
}

ByteBuffer::ByteBuffer(const char* buffer, size_t len) :
      _buffer(NULL),
      _len(0),
      _pos(0),
      _limit(0),
      _bufHolder(NULL),
      _ownedBuffer()
{
    set(buffer, len);
}

ByteBuffer::ByteBuffer(Alloc buffer, size_t len) :
      _buffer(static_cast<char *>(buffer.get())),
      _len(len),
      _pos(0),
      _limit(len),
      _bufHolder(NULL),
      _ownedBuffer(std::move(buffer))
{
}

ByteBuffer::ByteBuffer(BufferHolder* buf, size_t pos, size_t len, size_t limit) :
      _buffer(NULL),
      _len(0),
      _pos(0),
      _limit(0),
      _bufHolder(NULL),
      _ownedBuffer()
{
    set(buf, pos, len, limit);
    LOG_DEBUG3("Created copy of byte buffer of length %" PRIu64 " with "
               "limit %" PRIu64 ".", len, limit);
}

ByteBuffer::ByteBuffer(const ByteBuffer& bb) :
      _buffer(0),
      _len(0),
      _pos(0),
      _limit(0),
      _bufHolder(NULL),
      _ownedBuffer()
{
    LOG_DEBUG1("Created empty byte buffer to assign to.");
    *this = bb;
}

ByteBuffer& ByteBuffer::operator=(const ByteBuffer & org)
{
    if (this != & org) {
        cleanUp();
        if (org._len > 0 && org._buffer) {
            Alloc::alloc(org._len + 1).swap(_ownedBuffer);
            _buffer = static_cast<char *>(_ownedBuffer.get());
            memcpy(_buffer,org._buffer,org._len);
            _buffer[org._len] = 0;
        }
        _len = org._len;
        _pos = org._pos;
        _limit = org._limit;
        LOG_DEBUG4("Assignment created new buffer of size %" PRIu64 " at pos "
                   "%" PRIu64 " with limit %" PRIu64 ".",
                   _len, _pos, _limit);
    }
    return *this;
}

void
ByteBuffer::set(BufferHolder* buf, size_t pos, size_t len, size_t limit)
{
    cleanUp();
    _bufHolder = buf;
    _bufHolder->addRef();
    _buffer = static_cast<char *>(_bufHolder->_buffer.get());
    _pos=pos;
    _len=len;
    _limit=limit;
    LOG_DEBUG4("set() created new buffer of size %" PRIu64 " at pos "
               "%" PRIu64 " with limit %" PRIu64 ".",
               _len, _pos, _limit);
}

ByteBuffer::~ByteBuffer()
{
    if (_bufHolder) {
        _bufHolder->subRef();
    }
}

std::unique_ptr<ByteBuffer>
ByteBuffer::sliceCopy() const
{
    ByteBuffer* buf = new ByteBuffer;
    buf->sliceFrom(*this, _pos, _limit);

    LOG_DEBUG3("Created slice at pos %" PRIu64 " with limit %" PRIu64 ".",
               _pos, _limit);
    return std::unique_ptr<ByteBuffer>(buf);
}

void ByteBuffer::throwOutOfBounds(size_t want, size_t has)
{
    LOG_DEBUG1("Throwing out of bounds exception");
    throw BufferOutOfBoundsException(want, has, VESPA_STRLOC);
}

void
ByteBuffer::sliceFrom(const ByteBuffer& buf, size_t from, size_t to) // throw (BufferOutOfBoundsException)
{
    LOG_DEBUG3("Created slice from buffer from %" PRIu64 " to %" PRIu64 ".",
               from, to);
    if (from > buf._len) {
        throwOutOfBounds(from, buf._len);
    } else if (to > buf._len) {
        throwOutOfBounds(to, buf._len);
    } else if (to < from) {
        throwOutOfBounds(to, from);
    } else {

        if (!buf._buffer) {
            clear();
            return;
        }

        // Slicing from someone that doesn't own their buffer, must make own copy.
        if (( buf._ownedBuffer.get() == NULL ) && (buf._bufHolder == NULL)) {
            cleanUp();
            Alloc::alloc(to-from + 1).swap(_ownedBuffer);
            _buffer = static_cast<char *>(_ownedBuffer.get());
            memcpy(_buffer, buf._buffer + from, to-from);
            _buffer[to-from] = 0;
            _pos = 0;
            _len = _limit = to-from;
            return;
        }

        // Slicing from someone that owns, but hasn't made a reference counter yet.
        if (!buf._bufHolder) {
            buf._bufHolder=new BufferHolder(std::move(const_cast<Alloc &>(buf._ownedBuffer)));
        }

        // Slicing from refcounter.
        cleanUp();

        _bufHolder = buf._bufHolder;
        _bufHolder->addRef();
        _buffer = static_cast<char *>(_bufHolder->_buffer.get());
        _pos=from;
        _len=to;
        _limit=to;
    }
}

ByteBuffer* ByteBuffer::copyBuffer(const char* buffer, size_t len)
{
    if (buffer && len) {
        Alloc newBuf = Alloc::alloc(len + 1);
        memcpy(newBuf.get(), buffer, len);
        static_cast<char *>(newBuf.get())[len] = 0;
        return new ByteBuffer(std::move(newBuf), len);
    } else {
        return NULL;
    }
}

void
ByteBuffer::setPos(size_t pos) // throw (BufferOutOfBoundsException)
{
    LOG_DEBUG3("Setting pos to be %" PRIu64 ", limit is %" PRIu64 ".",
               pos, _limit);
    if (pos>_limit) {
        throwOutOfBounds(pos, _limit);
    } else {
        _pos=pos;
    }
}

void
ByteBuffer::setLimit(size_t limit) // throw (BufferOutOfBoundsException)
{
    LOG_DEBUG3("Setting limit to %" PRIu64 ", (size is %" PRIu64 ").", limit, _len);
    if (limit>_len) {
        throwOutOfBounds(limit, _len);
    } else {
        _limit=limit;
    }
}


ByteBuffer::BufferHolder::BufferHolder(Alloc buffer)
    : _buffer(std::move(buffer))
{
}

ByteBuffer::BufferHolder::~BufferHolder() = default;
void ByteBuffer::dump() const
{
    fprintf(stderr, "ByteBuffer: Length %lu, Pos %lu, Limit %lu\n",
            _len, _pos, _limit);
    for (size_t i=0; i<_len; i++) {
        if (_buffer[i]>32 && _buffer[i]<126) {
            fprintf(stderr, "%c", _buffer[i]);
        } else {
            fprintf(stderr, "[%d]",_buffer[i]);
        }
    }
}

void ByteBuffer::incPos(size_t pos)
{
    LOG_DEBUG2("incPos(%" PRIu64 ")", pos);
    if (_pos + pos > _limit) {
        throwOutOfBounds(_pos + pos, _limit);
    } else {
        _pos+=pos;
#ifdef __FORCE_VALGRIND_ON_SERIALIZE__
        forceValgrindStart2Pos();
#endif
    }
}

void ByteBuffer::getNumeric(uint8_t & v) {
    LOG_DEBUG2("getNumeric8(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(uint8_t *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumeric(uint8_t v) {
    LOG_DEBUG2("putNumeric8(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(uint8_t *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

size_t ByteBuffer::forceValgrindStart2Pos() const
{
    size_t zeroCount(0);
    if (_buffer) {
        for(const char * c(_buffer), *e(c + _pos); c < e; c++) {
            if (*c == 0) {
                zeroCount++;
            }
        }
    }
    return zeroCount;
}

size_t ByteBuffer::forceValgrindPos2Lim() const
{
    size_t zeroCount(0);
    if (_buffer) {
        for(const char * c(getBufferAtPos()), *e(c + getRemaining()); c < e; c++) {
            if (*c == 0) {
                zeroCount++;
            }
        }
    }
    return zeroCount;
}


void ByteBuffer::getNumericNetwork(int16_t & v) {
    LOG_DEBUG2("getNumericNetwork16(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val = *(uint16_t *) getBufferAtPos();
        v = ntohs(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(int16_t & v) {
    LOG_DEBUG2("getNumeric16(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int16_t *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumericNetwork(int16_t v) {
    LOG_DEBUG2("putNumericNetwork16(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val = htons(v);
        *(uint16_t *) getBufferAtPos() = val;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumeric(int16_t v) {
    LOG_DEBUG2("putNumeric16(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(int16_t *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(int32_t & v) {
    LOG_DEBUG2("getNumericNetwork32(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint32_t val = *(uint32_t *) getBufferAtPos();
        v = ntohl(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(int32_t & v) {
    LOG_DEBUG2("getNumeric32(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int32_t *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}


void ByteBuffer::putNumericNetwork(int32_t v) {
    LOG_DEBUG2("putNumericNetwork32(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint32_t val = htonl(v);
        *(uint32_t *) getBufferAtPos() = val;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumeric(int32_t v) {
    LOG_DEBUG2("putNumeric32(%d)", (int) v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(int32_t *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(float & v) {
    LOG_DEBUG2("getNumericNetworkFloat(%f)", v);
    // XXX depends on sizeof(float) == sizeof(uint32_t) == 4
    // and endianness same for float and ints
    int32_t val;
    getIntNetwork(val);
    memcpy(&v, &val, sizeof(v));
}

void ByteBuffer::getNumeric(float & v) {
    LOG_DEBUG2("getNumericFloat(%f)", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(float *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumericNetwork(float v) {
    LOG_DEBUG2("putNumericNetworkFloat(%f)", v);
    // XXX depends on sizeof(float) == sizeof(int32_t) == 4
    // and endianness same for float and ints
    int32_t val;
    memcpy(&val, &v, sizeof(val));
    putIntNetwork(val);
}

void ByteBuffer::putNumeric(float v) {
    LOG_DEBUG2("putNumericFloat(%f)", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(float *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}
void ByteBuffer::getNumeric(int64_t& v) {
    LOG_DEBUG2("getNumeric64(%" PRId64 ")", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int64_t *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}
void ByteBuffer::putNumeric(int64_t v) {
    LOG_DEBUG2("putNumeric64(%" PRId64 ")", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(int64_t *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}
void ByteBuffer::getNumeric(double& v) {
    LOG_DEBUG2("getNumericDouble(%f)", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(double *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}
void ByteBuffer::putNumeric(double v) {
    LOG_DEBUG2("putNumericDouble(%f)", v);
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(double *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(double & v) {
    LOG_DEBUG2("getNumericNetworkDouble(%f)", v);
    getDoubleLongNetwork(v);
}
void ByteBuffer::putNumericNetwork(int64_t v) {
    LOG_DEBUG2("putNumericNetwork64(%" PRId64 ")", v);
    putDoubleLongNetwork(v);
}
void ByteBuffer::putNumericNetwork(double v) {
    LOG_DEBUG2("putNumericNetworkDouble(%f)", v);
    putDoubleLongNetwork(v);
}
void ByteBuffer::getNumericNetwork(int64_t & v) {
    LOG_DEBUG2("getNumericNetwork64(%" PRId64 ")", v);
    getDoubleLongNetwork(v);
}

void ByteBuffer::putInt2_4_8Bytes(int64_t number, size_t len) {
    LOG_DEBUG3("putInt2_4_8(%" PRId64 ", %" PRIu64 ")", number, len);
    if (number < 0ll) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x3FFFFFFFFFFFFFFFll) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode number larger than 2^62."), VESPA_STRLOC);
    }

    if (len == 0) {
        if (number < 0x8000ll) {
            //length 2 bytes
            putShortNetwork((int16_t) number);
        } else if (number < 0x40000000ll) {
            //length 4 bytes
            putIntNetwork(((int32_t) number) | 0x80000000);
        } else {
            //length 8 bytes
            putLongNetwork(number | 0xC000000000000000ll);
        }
    } else if (len == 2) {
        //length 2 bytes
        putShortNetwork((int16_t) number);
    } else if (len == 4) {
        //length 4 bytes
        putIntNetwork(((int32_t) number) | 0x80000000);
    } else if (len == 8) {
        //length 8 bytes
        putLongNetwork(number | 0xC000000000000000ll);
    } else {
        throw InputOutOfRangeException(vespalib::make_string(
                "Cannot encode number using %d bytes.", (int)len), VESPA_STRLOC);
    }
}

void ByteBuffer::getInt2_4_8Bytes(int64_t & v) {
    LOG_DEBUG2("getInt2_4_8(%" PRId64 ")", v);
    if (getRemaining() >= 2) {
        uint8_t flagByte = peekByte();

        if (flagByte & 0x80) {
            if (flagByte & 0x40) {
                //length 8 bytes
                int64_t tmp;
                getLongNetwork(tmp);
                v = tmp & 0x3FFFFFFFFFFFFFFFll;
            } else {
                //length 4 bytes
                int32_t tmp;
                getIntNetwork(tmp);
                v = (int64_t) (tmp & 0x3FFFFFFF);
            }
        } else {
            //length 2 bytes
            int16_t tmp;
            getShortNetwork(tmp);
            v = (int64_t) tmp;
        }
    } else {
        throwOutOfBounds(getRemaining(), 2);
    }
}

size_t ByteBuffer::getSerializedSize2_4_8Bytes(int64_t number) {
    if (number < 0ll) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x3FFFFFFFFFFFFFFFll) {
        throw InputOutOfRangeException(vespalib::make_string(
                "Cannot encode number larger than 2^62."), VESPA_STRLOC);
    }

    if (number < 0x8000ll) {
        return 2;
    } else if (number < 0x40000000ll) {
        return 4;
    } else {
        return 8;
    }
}

void ByteBuffer::putInt1_2_4Bytes(int32_t number) {
    LOG_DEBUG2("putInt1_2_4Bytes(%i)", number);
    if (number < 0) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x3FFFFFFF) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode number larger than 2^30."), VESPA_STRLOC);
    }

    if (number < 0x80) {
        putByte((unsigned char) number);
    } else if (number < 0x4000) {
        putShortNetwork((int16_t) (((int16_t)number) | ((int16_t) 0x8000)));
    } else {
        putIntNetwork(number | 0xC0000000);
    }
}

void ByteBuffer::getInt1_2_4Bytes(int32_t & v) {
    LOG_DEBUG2("getInt1_2_4Bytes(%i)", v);
    if (getRemaining() >= 1) {
        unsigned char flagByte = peekByte();

        if (flagByte & 0x80) {
            if (flagByte & 0x40) {
                //length 4 bytes
                int32_t tmp;
                getIntNetwork(tmp);
                v = tmp & 0x3FFFFFFF;
            } else {
                //length 2 bytes
                int16_t tmp;
                getShortNetwork(tmp);
                v = (int32_t) (tmp & ((int16_t) 0x3FFF));
            }
        } else {
            v = (int32_t) flagByte;
            incPosNoCheck(1);
        }
    } else {
        throwOutOfBounds(getRemaining(), 1);
    }
}

size_t ByteBuffer::getSerializedSize1_2_4Bytes(int32_t number) {
    if (number < 0) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x3FFFFFFF) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode number larger than 2^30."), VESPA_STRLOC);
    }

    if (number < 0x80) {
        return 1;
    } else if (number < 0x4000) {
        return 2;
    } else {
        return 4;
    }
}
void ByteBuffer::putInt1_4Bytes(int32_t number) {
    LOG_DEBUG2("putInt1_4Bytes(%i)", number);
    if (number < 0) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x7FFFFFFF) {
        throw InputOutOfRangeException(vespalib::make_string(
                    "Cannot encode number larger than 2^31."), VESPA_STRLOC);
    }

    if (number < 0x80) {
        putByte((unsigned char) number);
    } else {
        putIntNetwork(number | 0x80000000);
    }
}
void ByteBuffer::getInt1_4Bytes(int32_t & v) {
    LOG_DEBUG2("getInt1_4Bytes(%i)", v);
    if (getRemaining() >= 1) {
        unsigned char flagByte = peekByte();

        if (flagByte & 0x80) {
            //length 4 bytes
            int32_t tmp;
            getIntNetwork(tmp);
            v = tmp & 0x7FFFFFFF;
        } else {
            v = (int32_t) flagByte;
            incPosNoCheck(1);
        }
    } else {
        throwOutOfBounds(getRemaining(), 1);
    }
}
size_t ByteBuffer::getSerializedSize1_4Bytes(int32_t number) {
    if (number < 0) {
        throw InputOutOfRangeException(vespalib::make_string(
                "Cannot encode negative number."), VESPA_STRLOC);
    } else if (number > 0x7FFFFFFF) {
        throw InputOutOfRangeException(vespalib::make_string(
            "Cannot encode number larger than 2^31."), VESPA_STRLOC);
    }

    if (number < 0x80) {
        return 1;
    } else {
        return 4;
    }
}
void ByteBuffer::getBytes(void *buffer, size_t count)
{
    LOG_DEBUG3("getBytes(%p, %" PRIu64 ")", buffer, count);
    const char *v = getBufferAtPos();
    incPos(count);
    memcpy(buffer, v, count);
}
void ByteBuffer::putBytes(const void *buf, size_t count) {
    LOG_DEBUG3("putBytes(%p, %" PRIu64 ")", buf, count);
    if (__builtin_expect(getRemaining() < count, 0)) {
        throwOutOfBounds(getRemaining(), sizeof(count));
    } else {
        memcpy(getBufferAtPos(), buf, count);
        incPosNoCheck(count);
    }
}
std::string ByteBuffer::toString() {
    std::ostringstream ost;
    StringUtil::printAsHex(ost, getBuffer(), getLength());
    return ost.str();
}

void ByteBuffer::swap(ByteBuffer& other) {
    LOG_DEBUG2("swap(%p)", &other);
    std::swap(_bufHolder, other._bufHolder);
    std::swap(_buffer, other._buffer);
    std::swap(_len, other._len);
    std::swap(_pos, other._pos);
    std::swap(_limit, other._limit);
}

void ByteBuffer::cleanUp() {
    LOG_DEBUG1("cleanUp()");
    if (_bufHolder) {
        _bufHolder->subRef();
        _bufHolder = NULL;
    } else {
        Alloc().swap(_ownedBuffer);
    }
    _buffer = NULL;
}

} // document
