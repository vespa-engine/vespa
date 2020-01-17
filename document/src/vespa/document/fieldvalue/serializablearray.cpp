// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "serializablearray.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/data/databuffer.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".document.serializable-array");

using std::vector;
using vespalib::make_string;

namespace document {

namespace serializablearray {

using BufferMapT = vespalib::hash_map<int, ByteBuffer::UP>;

class BufferMap : public BufferMapT {
public:
    using BufferMapT::BufferMapT;
};

}


SerializableArray::SerializableArray()
    : _serializedCompression(CompressionConfig::NONE),
      _uncompressedLength(0)
{
}

SerializableArray::SerializableArray(EntryMap entries, ByteBuffer::UP buffer,
                                     CompressionConfig::Type comp_type,uint32_t uncompressed_length)
    : _entries(std::move(entries)),
      _owned(),
      _serializedCompression(comp_type)
{

    if (CompressionConfig::isCompressed(_serializedCompression)) {
        _compSerData = std::move(buffer);
        _uncompressedLength = uncompressed_length;
    } else {
        _uncompressedLength = buffer->getRemaining();
        _uncompSerData = std::move(buffer);
    }
}

serializablearray::BufferMap & ensure(std::unique_ptr<serializablearray::BufferMap> & owned) {
    if (!owned) {
        owned = std::make_unique<serializablearray::BufferMap>();
    }
    return *owned;
}

SerializableArray::SerializableArray(const SerializableArray& other)
    : Cloneable(),
      _entries(other._entries),
      _owned(),
      _uncompSerData(other._uncompSerData.get() ? new ByteBuffer(*other._uncompSerData) : nullptr),
      _compSerData(other._compSerData.get() ? new ByteBuffer(*other._compSerData) : nullptr),
      _serializedCompression(other._serializedCompression),
      _uncompressedLength(other._uncompressedLength)
{
    for (size_t i(0); i < _entries.size(); i++) {
        Entry & e(_entries[i]);
        if (e.hasBuffer()) {
            // Pointing to a buffer in the _owned structure.
            ByteBuffer::UP buf(ByteBuffer::copyBuffer(e.getBuffer(_uncompSerData.get()), e.size()));
            e.setBuffer(buf->getBuffer());
            ensure(_owned)[e.id()] = std::move(buf);
        } else {
            // If not it is relative to the buffer _uncompSerData, and hence it is valid as is.
        }
    }
    if (_uncompSerData.get()) {
        LOG_ASSERT(_uncompressedLength == _uncompSerData->getRemaining());
    }
}

void
SerializableArray::swap(SerializableArray& other)
{
    _entries.swap(other._entries);
    _owned.swap(other._owned);
    std::swap(_uncompSerData, other._uncompSerData);
    std::swap(_compSerData, other._compSerData);
    std::swap(_serializedCompression, other._serializedCompression);
    std::swap(_uncompressedLength, other._uncompressedLength);
}

void SerializableArray::clear()
{
    _entries.clear();
    _uncompSerData.reset();
    _compSerData.reset();
    _serializedCompression = CompressionConfig::NONE;
    _uncompressedLength = 0;
}

SerializableArray::~SerializableArray() = default;

void
SerializableArray::invalidate()
{
    _compSerData.reset();
}

void
SerializableArray::set(int id, ByteBuffer::UP buffer)
{
    maybeDecompress();
    Entry e(id, buffer->getRemaining(), buffer->getBuffer());
    ensure(_owned)[id] = std::move(buffer);
    auto it = find(id);
    if (it == _entries.end()) {
        _entries.push_back(e);
    } else {
        *it = e;
    }
    invalidate();
}

void SerializableArray::set(int id, const char* value, int len)
{
    set(id, std::unique_ptr<ByteBuffer>(ByteBuffer::copyBuffer(value,len)));
}

SerializableArray::EntryMap::const_iterator
SerializableArray::find(int id) const
{
    return std::find_if(_entries.begin(), _entries.end(), [id](const auto& e){ return e.id() == id; });
}

SerializableArray::EntryMap::iterator
SerializableArray::find(int id)
{
    return std::find_if(_entries.begin(), _entries.end(), [id](const auto& e){ return e.id() == id; });
}

bool
SerializableArray::has(int id) const
{
    return (find(id) != _entries.end());
}

vespalib::ConstBufferRef
SerializableArray::get(int id) const
{
    vespalib::ConstBufferRef buf;
    if ( !maybeDecompressAndCatch() ) {
        auto found = find(id);

        if (found != _entries.end()) {
            const Entry& entry = *found;
            buf = vespalib::ConstBufferRef(entry.getBuffer(_uncompSerData.get()), entry.size());
        }
    } else {
        // should we clear all or what?
    }

    return buf;
}

bool
SerializableArray::deCompressAndCatch() const
{
    try {
        const_cast<SerializableArray *>(this)->deCompress();
        return false;
    } catch (const std::exception & e) {
        LOG(warning, "Deserializing compressed content failed: %s", e.what());
        return true;
    }
}

void
SerializableArray::clear(int id)
{
    maybeDecompress();
    auto it  = find(id);
    if (it != _entries.end()) {
        _entries.erase(it);
        if (_owned) {
            _owned->erase(id);
        }
        invalidate();
    }
}

void
SerializableArray::deCompress() // throw (DeserializeException)
{
    using vespalib::compression::decompress;
    // will only do this once

    LOG_ASSERT(_compSerData);
    LOG_ASSERT(!_uncompSerData);

    if (_serializedCompression == CompressionConfig::NONE ||
        _serializedCompression == CompressionConfig::UNCOMPRESSABLE)
    {
        _uncompSerData = std::move(_compSerData);
        LOG_ASSERT(_uncompressedLength == _uncompSerData->getRemaining());
    } else {
        ByteBuffer::UP newSerialization(new ByteBuffer(_uncompressedLength));
        vespalib::DataBuffer unCompressed(newSerialization->getBuffer(), newSerialization->getLength());
        unCompressed.clear();
        try {
            decompress(_serializedCompression,
                       _uncompressedLength,
                       vespalib::ConstBufferRef(_compSerData->getBufferAtPos(), _compSerData->getRemaining()),
                       unCompressed,
                       false);
        } catch (const std::runtime_error & e) {
            throw DeserializeException(
                make_string( "Document was compressed with code unknown code %d", _serializedCompression),
                VESPA_STRLOC);
        }

        if (unCompressed.getDataLen() != (size_t)_uncompressedLength) {
            throw DeserializeException(
                    make_string("Did not decompress to the expected length: had %zu, wanted %d, got %zu",
                                _compSerData->getRemaining(), _uncompressedLength, unCompressed.getDataLen()),
                    VESPA_STRLOC);
        }
        assert(newSerialization->getBuffer() == unCompressed.getData());
        newSerialization->setLimit(_uncompressedLength);
        _uncompSerData = std::move(newSerialization);
        LOG_ASSERT(_uncompressedLength == _uncompSerData->getRemaining());
    }
}

vespalib::compression::CompressionInfo
SerializableArray::getCompressionInfo() const {
    return CompressionInfo(_uncompressedLength, _compSerData->getRemaining());
}

const char *
SerializableArray::Entry::getBuffer(const ByteBuffer * readOnlyBuffer) const {
    return hasBuffer() ? _data._buffer : readOnlyBuffer->getBuffer() + getOffset();
}

} // document
