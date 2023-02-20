// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
using vespalib::make_string;

namespace document {

namespace {

[[noreturn]] static void throwOutOfBounds(size_t want, size_t has) __attribute__((noinline));

void throwOutOfBounds(size_t want, size_t has)
{
    throw BufferOutOfBoundsException(want, has, VESPA_STRLOC);
}

}

#if defined(__i386__) || defined(__x86_64__) || defined(__AARCH64EL__)

template<typename T>
void
ByteBuffer::getDoubleLongNetwork(T &val) {
    //TODO: Change this if we move to big-endian hardware
    if (__builtin_expect(getRemaining() < (int)sizeof(T), 0)) {
        throwOutOfBounds(sizeof(T), getRemaining());
    }

    auto * data = reinterpret_cast<unsigned char*>(&val);
    for (int i=sizeof(T)-1; i>=0; --i) {
        getByte(data[i]);
    }
}

#else
#error "getDoubleLongNetwork is undefined for this arcitecture"
#endif

VESPA_IMPLEMENT_EXCEPTION_SPINE(BufferOutOfBoundsException);

vespalib::string BufferOutOfBoundsException::createMessage(size_t pos, size_t len) {
    vespalib::asciistream ost;
    ost << pos << " > " << len;
    return ost.str();
}

BufferOutOfBoundsException::BufferOutOfBoundsException(size_t pos, size_t len, const vespalib::string& location)
    : IoException(createMessage(pos, len), IoException::NO_SPACE, location, 1)
{
}

ByteBuffer::ByteBuffer(Alloc buffer, uint32_t len)
  : _buffer(static_cast<const char *>(buffer.get())),
    _len(len),
    _pos(0),
    _ownedBuffer(std::make_unique<Alloc>(std::move(buffer)))
{
}

ByteBuffer::ByteBuffer(std::unique_ptr<Alloc> buffer, uint32_t len)
    : _buffer(static_cast<const char *>(buffer->get())),
      _len(len),
      _pos(0),
      _ownedBuffer(std::move(buffer))
{
}

ByteBuffer::ByteBuffer(const ByteBuffer& rhs)
    : _buffer(nullptr),
      _len(rhs._len),
      _pos(rhs._pos),
      _ownedBuffer()
{
    if (rhs._len > 0 && rhs._buffer) {
        auto buf = Alloc::alloc(rhs._len);
        memcpy(buf.get(), rhs._buffer, rhs._len);
        _buffer = static_cast<const char *>(buf.get());
        _ownedBuffer = std::make_unique<Alloc>(std::move(buf));
    }
}

ByteBuffer
ByteBuffer::copyBuffer(const char* buffer, uint32_t len)
{
    if (buffer && len) {
        Alloc newBuf = Alloc::alloc(len);
        memcpy(newBuf.get(), buffer, len);
        return ByteBuffer(std::make_unique<Alloc>(std::move(newBuf)), len);
    } else {
        return ByteBuffer();
    }
}

void ByteBuffer::incPos(uint32_t pos)
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
        v = *reinterpret_cast<const uint8_t *>(getBufferAtPos());
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(int16_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val;
        memcpy(&val, getBufferAtPos(), sizeof(val));
        v = ntohs(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(int32_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint32_t val;
        memcpy(&val, getBufferAtPos(), sizeof(val));
        v = ntohl(val);
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumeric(int64_t& v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        memcpy(&v, getBufferAtPos(), sizeof(v));
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(double & v) {
    getDoubleLongNetwork(v);
}

void ByteBuffer::getNumericNetwork(int64_t & v) {
    getDoubleLongNetwork(v);
}

void ByteBuffer::getBytes(void *buffer, uint32_t count)
{
    const char *v = getBufferAtPos();
    incPos(count);
    if (count > 0) {
        memcpy(buffer, v, count);
    }
}

} // document
