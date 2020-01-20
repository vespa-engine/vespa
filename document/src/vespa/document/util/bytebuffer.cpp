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
using vespalib::make_string;

namespace document {

namespace {

static void throwOutOfBounds(size_t want, size_t has) __attribute__((noinline, noreturn));

void throwOutOfBounds(size_t want, size_t has)
{
    throw BufferOutOfBoundsException(want, has, VESPA_STRLOC);
}

}

#if defined(__i386__) || defined(__x86_64__)

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

void ByteBuffer::getNumericNetwork(int16_t & v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        uint16_t val = *(uint16_t *) (void *) getBufferAtPos();
        v = ntohs(val);
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

void ByteBuffer::getNumeric(int64_t& v) {
    if (__builtin_expect(getRemaining() < sizeof(v), 0)) {
        throwOutOfBounds(getRemaining(), sizeof(v));
    } else {
        v = *(int64_t *) (void *) getBufferAtPos();
        incPosNoCheck(sizeof(v));
    }
}

void ByteBuffer::getNumericNetwork(double & v) {
    getDoubleLongNetwork(v);
}

void ByteBuffer::getNumericNetwork(int64_t & v) {
    getDoubleLongNetwork(v);
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
