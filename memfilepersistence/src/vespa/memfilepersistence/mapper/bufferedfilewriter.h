// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::BufferedFileWriter
 * @ingroup filestorage
 *
 * @brief A utility class for buffered writing to a file.
 *
 * To minimize the number of system calls, and to minimize the chance of
 * fragmentation, files should be written to disk in large chunks. Since
 * it's easier to write algorithms which writes files in smaller pieces, this
 * class exists to buffer such writes and send them to disk at a later time.
 *
 * @author H�kon Humberset
 * @date 2005-11-03
 */

#pragma once

#include <vespa/fastos/types.h>
#include <vector>

namespace vespalib {
    class File;
}

namespace storage {

namespace memfile {

class BufferedFileWriter {
public:
    struct Cache {
        virtual ~Cache() {}
        virtual uint32_t getCachedAmount() const = 0;
        /** Index given must be within [0 - getCachedAmount()> */
        virtual char* getCache(uint32_t atIndex) = 0;
        /** If true, write to both cache and file, else, write to cache only. */
        virtual bool duplicateCacheWrite() const = 0;
        /** Function for updating content in cache. Implemented in cache as new
         * core overrides it to ignore data ahead of a given index. */
        virtual void setData(const char* data, size_t len, uint64_t pos)
            { memcpy(getCache(pos), data, len); }
    };

private:
    vespalib::File& _file;
    char* _buffer;
    uint32_t _bufferSize;
    uint32_t _bufferedData;
    uint32_t _filePosition;
    uint32_t _writeCount;
    Cache* _cache;
    uint32_t _cacheDirtyUpTo;
    bool _writing;

public:
    BufferedFileWriter(const BufferedFileWriter &) = delete;
    BufferedFileWriter & operator = (const BufferedFileWriter &) = delete;
    /**
     * Create a new buffered file writer.
     *
     * @param filedescriptor Write to this file which should already be open for
     *        writing.
     * @param buffer Pointer to the buffer to use in this writer. Note that
     *               if buffer is 0, fakemode will be used, where all writes
     *               are sent on to OS. This mode can be used to test difference
     *               in performance of using this class or not.
     * @param bufferSize The size of the buffer to keep.
     */
    BufferedFileWriter(vespalib::File&, char* buffer, uint32_t bufferSize);
    /**
     * Destructor does not flush(). Make sure to call flush() manually.
     * (flush() can fail, and destructors should not throw exceptions)
     */
    ~BufferedFileWriter();

    uint32_t getBufferSize() const { return _bufferSize; }

    /**
     * If set, write portion written inside of memory cache here instead of
     * to file.
     */
    void setMemoryCache(Cache* cache);

    bool isMemoryCacheDirty() const { return (_cacheDirtyUpTo != 0); }

    uint32_t getLastDirtyIndex() const { return _cacheDirtyUpTo; }

    void tagCacheClean() { _cacheDirtyUpTo = 0; }

    /** Write all buffered data to disk. */
    void flush();

    // Functions using the held file position.

    /** Writes the given data to file and increases the file position. */
    void write(const void *buffer, size_t size);

    /** Writes undefined data of given size to file and increases position. */
    void writeGarbage(uint32_t size);

    /** Set the file position to the given value. (Flushes before changing) */
    void setFilePosition(uint32_t pos);

    /** Get the current file position. */
    uint32_t getFilePosition() const;

    uint32_t getBufferedSize() const { return _bufferedData; }

    /** Get how many times this writer has flushed data to disk. */
    uint32_t getWriteCount() const { return _writeCount; }

private:
    void write(const char* data, uint32_t size, uint32_t pos);
};

}

}

