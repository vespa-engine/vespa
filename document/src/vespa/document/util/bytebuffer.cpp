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

ByteBuffer::ByteBuffer(size_t len) :
    ByteBuffer(Alloc::alloc(len), len)
{
}

ByteBuffer::ByteBuffer(const char* buffer, size_t len) :
      _buffer(const_cast<char *>(buffer)),
      _len(len),
      _pos(0),
      _ownedBuffer()
{
}

ByteBuffer::ByteBuffer(Alloc buffer, size_t len) :
      _buffer(static_cast<char *>(buffer.get())),
      _len(len),
      _pos(0),
      _ownedBuffer(std::move(buffer))
{
}

ByteBuffer::ByteBuffer(const ByteBuffer& rhs) :
      _buffer(nullptr),
      _len(rhs._len),
      _pos(rhs._pos),
      _ownedBuffer()
{
    if (rhs._len > 0 && rhs._buffer) {
        Alloc::alloc(rhs._len + 1).swap(_ownedBuffer);
        _buffer = static_cast<char *>(_ownedBuffer.get());
        memcpy(_buffer, rhs._buffer, rhs._len);
        _buffer[rhs._len] = 0;
    }
}

ByteBuffer::~ByteBuffer() = default;

void ByteBuffer::throwOutOfBounds(size_t want, size_t has)
{
    throw BufferOutOfBoundsException(want, has, VESPA_STRLOC);
}

ByteBuffer* ByteBuffer::copyBuffer(const char* buffer, size_t len)
{
    if (buffer && len) {
        Alloc newBuf = Alloc::alloc(len + 1);
        memcpy(newBuf.get(), buffer, len);
        static_cast<char *>(newBuf.get())[len] = 0;
        return new ByteBuffer(std::move(newBuf), len);
    } else {
        return nullptr;
    }
}

void
ByteBuffer::setPos(size_t pos) // throw (BufferOutOfBoundsException)
{
    if (pos > _len) {
        throwOutOfBounds(pos, _len);
    } else {
        _pos=pos;
    }
}

void ByteBuffer::incPos(size_t pos)
{
    if (_pos + pos > _len) {
        throwOutOfBounds(_pos + pos, _len);
    } else {
        _pos+=pos;
    }
}

void ByteBuffer::getNumeric(uint8_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(uint8_t *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumeric(uint8_t v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(uint8_t *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(int16_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val = *(uint16_t *) (void *) getBufferAtPos();
        v = ntohs(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumericNetwork(int16_t v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val = htons(v);
        *(uint16_t *) (void *) getBufferAtPos() = val;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(int32_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint32_t val = *(uint32_t *) (void *) getBufferAtPos();
        v = ntohl(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(int32_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int32_t *) (void *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}


void ByteBuffer::putNumericNetwork(int32_t v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint32_t val = htonl(v);
        *(uint32_t *) (void *) getBufferAtPos() = val;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::putNumeric(int32_t v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(int32_t *) (void *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(float & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(float *) (void *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(int64_t& v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int64_t *) (void *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(double& v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(double *) (void *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}
void ByteBuffer::putNumeric(double v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        *(double *) (void *) getBufferAtPos() = v;
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(double & v) {
    getDoubleLongNetwork(v);
}
void ByteBuffer::putNumericNetwork(int64_t v) {
    putDoubleLongNetwork(v);
}
void ByteBuffer::putNumericNetwork(double v) {
    putDoubleLongNetwork(v);
}
void ByteBuffer::getNumericNetwork(int64_t & v) {
    getDoubleLongNetwork(v);
}

void ByteBuffer::putInt2_4_8Bytes(int64_t number, size_t len) {
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
    const char *v = getBufferAtPos();
    incPos(count);
    memcpy(buffer, v, count);
}
void ByteBuffer::putBytes(const void *buf, size_t count) {
    if (__builtin_expect(getRemaining() < count, 0)) {
        throwOutOfBounds(getRemaining(), sizeof(count));
    } else {
        memcpy(getBufferAtPos(), buf, count);
        incPosNoCheck(count);
    }
}

} // document
