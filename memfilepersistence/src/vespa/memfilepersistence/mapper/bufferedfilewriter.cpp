// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedfilewriter.h"
#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.memfile.bufferedfilewriter");

namespace storage {

namespace memfile {

using vespalib::ValueGuard;

BufferedFileWriter::
BufferedFileWriter(vespalib::File& file, char* buffer, uint32_t bufferSize)
    : _file(file),
      _buffer(buffer),
      _bufferSize(bufferSize),
      _bufferedData(0),
      _filePosition(0),
      _writeCount(0),
      _cache(0),
      _cacheDirtyUpTo(0),
      _writing(false)
{
            // Since we normally use direct IO for writing, we want to have
            // 512b aligned buffers
    if (bufferSize < 512) {
            // Only warn for this. Used in testing.
        LOGBP(warning, "Using buffer smaller than 512b");
    } else if (bufferSize % 512 != 0) {
        std::ostringstream ost;
        ost << "Buffered file writer got buffer of length " << bufferSize
            << " (Not dividable by 512)";
        throw vespalib::IllegalArgumentException(ost.str());
    }
    LOG(spam, "Using buffer in writer of %u bytes", bufferSize);
}

BufferedFileWriter::~BufferedFileWriter()
{
    if (LOG_WOULD_LOG(debug) && _bufferedData != 0) {
        LOG(debug, "Discarding %u bytes of buffered, unflushed data",
            _bufferedData);
    }
}

void
BufferedFileWriter::setMemoryCache(Cache* cache)
{
    _cache = cache;
    _cacheDirtyUpTo = 0;
    if (cache == 0) {
        LOG(spam, "No longer using a memory cache");
    } else {
        LOG(spam, "Using memory cache of %u bytes", _cache->getCachedAmount());
    }
}

void BufferedFileWriter::write(const char* data, uint32_t size, uint32_t pos)
{
    _writing = true;
        // If at least parts of data written is cached in slotfileimage, update
        // cache rather than write to file.
    if (_cache != 0 && _cache->getCachedAmount() > pos) {
        uint32_t len = std::min(size, _cache->getCachedAmount() - pos);
        _cache->setData(data, len, pos);
        if (_cache->duplicateCacheWrite()) {
            len = 0;
        }
        if (len != size) { // Write remaining directly to disk
            LOG(spam, "Writing remainder after cache, bypassing buffer. "
                      "%u bytes at pos %u.", size - len, pos + len);
            _file.write(data + len, size - len, pos + len);
            ++_writeCount;
        } else {
            LOG(spam, "Writing %u bytes to memory cache at position %u.",
                size, pos);
        }
        _cacheDirtyUpTo = std::max(_cacheDirtyUpTo, pos + len);
    } else {
        LOG(spam, "Writing directly to file, bypassing buffer. %u"
                  " bytes at pos %u", size, pos);
        _file.write(data, size, pos);
        ++_writeCount;
    }
    _writing = false;
}

void BufferedFileWriter::flush()
{
    if (_bufferedData == 0) return;
    LOG(spam, "Flushing buffer. Writing %u at pos %u.",
              _bufferedData, _filePosition);
    write(_buffer, _bufferedData, _filePosition);
    _filePosition += _bufferedData;
    _bufferedData = 0;
}

void BufferedFileWriter::write(const void *buffer, size_t size)
{
    LOG(spam, "Writing %" PRIu64 " bytes to buffer at position %u.",
        size, _filePosition + _bufferedData);
    if (!_buffer) { // If we don't use a buffer, just write to file.
        write(static_cast<const char*>(buffer), size, _filePosition);
        _filePosition += size;
        return;
    }
        // In case of exception later, reset state to original state
    ValueGuard<uint32_t> bufIndexGuard(_bufferedData);
    ValueGuard<uint32_t> filePositionGuard(_filePosition);
        // Buffer may contain data prior to this write call. If this is
        // successfully written to disk, we need to update state to revert
        // to such that we don't lose that write.

    if (_bufferedData + size >= _bufferSize) {
        size_t part = _bufferSize - _bufferedData;
        memcpy(_buffer + _bufferedData, buffer, part);
        _bufferedData = _bufferSize;
        buffer = static_cast<const char*>(buffer) + part;
        flush();
        bufIndexGuard = 0;
        filePositionGuard = _filePosition + _bufferSize - part;
        size -= part;
    }

    if (_bufferedData + size >= _bufferSize) {
        if (reinterpret_cast<unsigned long>(buffer)%0x200 == 0) {
            // Write the big part that is a multiple of _bufferSize to the file.
            size_t part((size/_bufferSize)*_bufferSize);
            write(static_cast<const char*>(buffer), part, _filePosition);
            _filePosition += part;
            buffer = static_cast<const char*>(buffer) + part;
            size -= part;
        } else {
            for (; _bufferedData + size >= _bufferSize; size -= _bufferSize, buffer = static_cast<const char*>(buffer) + _bufferSize) {
                memcpy(_buffer, buffer, _bufferSize);
                _bufferedData = _bufferSize;
                flush();
            }
        }
    }

        // We now have room for the rest of the data in buffer
    assert(_bufferedData + size < _bufferSize);
    memcpy(_buffer + _bufferedData, buffer, size);
    _bufferedData += size;
        // Finished successfully, deactivate guards
    bufIndexGuard.deactivate();
    filePositionGuard.deactivate();
}

void BufferedFileWriter::writeGarbage(uint32_t size) {
    LOG(spam, "Writing %u bytes of garbage at position %u.",
        size, _filePosition + _bufferedData);
    if (!_buffer) {
        ValueGuard<uint32_t> filePositionGuard(_filePosition);
        uint32_t maxBufferSize = 0xFFFF;
        uint32_t bufSize = (size > maxBufferSize ? maxBufferSize : size);
        std::unique_ptr<char[]> buf(new char[bufSize]);
        while (size > 0) {
            uint32_t part = (size > bufSize ? bufSize : size);
            write(&buf[0], part, _filePosition);
            _filePosition += part;
            size -= part;
        }
        filePositionGuard.deactivate();
        return;
    }
        // In case of exception later, reset state to original state
    ValueGuard<uint32_t> bufIndexGuard(_bufferedData);
    ValueGuard<uint32_t> filePositionGuard(_filePosition);

    if (_bufferedData + size >= _bufferSize) {
        size_t part = _bufferSize - _bufferedData;
        memset(_buffer + _bufferedData, 0xFF, part);
        _bufferedData += part; // Use any garbage data already there.
        flush();
        bufIndexGuard = 0;
        filePositionGuard = _filePosition + _bufferSize - part;
        size -= part;
    }

    memset(_buffer + _bufferedData, 0xFF, std::min(_bufferSize-_bufferedData, size));

    for (;_bufferedData + size >= _bufferSize; size -= _bufferSize) {
        _bufferedData = _bufferSize;
        flush();
    }

    // We now have room for the rest of the data in buffer
    assert(_bufferedData + size < _bufferSize);
    _bufferedData += size; // Use any garbage data already there.
    // Finished successfully, deactivate guards
    bufIndexGuard.deactivate();
    filePositionGuard.deactivate();
}

void BufferedFileWriter::setFilePosition(uint32_t pos)
{
    if (pos != _filePosition + _bufferedData) {
        flush();
        _filePosition = pos;
    }
}

uint32_t BufferedFileWriter::getFilePosition() const
{
    return _filePosition + _bufferedData;
}

}

}
