// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/fastos/file.h>

/**
 * Provides buffered file access.
 */
class Fast_BufferedFile : public FastOS_FileInterface
{
private:
    using Alloc = vespalib::alloc::Alloc;
    /** The number of bytes left in the file. */
    int64_t _fileleft;
    /** Pointer to the start of the buffer. Correctly aligned for direct IO */
    Alloc _buf;
    /** Pointer to the input point in the buffer. */
    char *_bufi;
    /** Pointer to the end of the buffer. */
    char *_bufe;
    /** The file position for next read or write. */
    int64_t _filepos;
    /** True if the file should be read using direct IO */
    bool _directIOEnabled;

    void setupDirectIOAlign();
    char * buf() { return static_cast<char *>(_buf.get()); }
protected:
    /** The file instance used for low-level file access. */
    std::unique_ptr<FastOS_FileInterface> _file;

public:
    /**
     * Create buffered file.
     * @param file file instance that should be used for low-level
     *             file access. If this is NULL, an instance of
     *             FastOS_File will be created. NOTE: the file
     *             instance given here will be deleted by
     *             the destructor.
     **/
    Fast_BufferedFile(FastOS_FileInterface *file, size_t bufferSize);
    Fast_BufferedFile(FastOS_FileInterface *file);
    Fast_BufferedFile();
    Fast_BufferedFile(size_t bufferSize);
    Fast_BufferedFile(const Fast_BufferedFile &) = delete;
    Fast_BufferedFile & operator = (const Fast_BufferedFile &) = delete;

    /**
     * Delete the file instance used for low-level file access.
     **/
    virtual ~Fast_BufferedFile(void);
    /**
     * Open an existing file for reading.
     *
     * @param name The name of the file to open.
     */
    void ReadOpenExisting(const char *name);
    /**
     * Open a file for reading.
     *
     * @param name The name of the file to open.
     */
    void ReadOpen(const char *name);

    /**
     * Open file for writing.
     *
     * @param name The name of the file to open.
     */
    void WriteOpen(const char *name);
    /**
     * Reset the internal start and end pointers to the
     * head of the buffer, thus "emptying" it.
     */
    void ResetBuf(void);
    /**
     * Write the buffer to the file. Caution: Uses obsolete
     * FastOS_FileInterface::WriteBuf.
     * Allocates a 32kB buffer if not previously allocated.
     */
    void flushWriteBuf(void);
    /**
     * Read from the file into the buffer. Allocates a 32kB
     * buffer if not previously allocated. Fills the buffer,
     * or reads as much as possible if the (rest of) the file
     * is smaller than the buffer.
     * Caution: If the amount read is smaller than the expected
     * amount, the method will abort.
     */
    void fillReadBuf(void);
    /**
     * Read the next line of the buffered file into a buffer,
     * reading from the file as necessary.
     *
     * @param buf The buffer to fill.
     * @param buflen The size of the buffer.
     * @return Pointer to the start of the next line, of NULL if no line.
     */
    char * ReadLine(char *buf, size_t buflen);
    /**
     * Write a buffer to a buffered file, flushing to file
     * as necessary.
     *
     * @param src The source buffer.
     * @param srclen The length of the source buffer.
     */
    ssize_t Write2(const void*, size_t) override;
    /**
     * Write a string to a buffered file, flushing to file
     * as necessary.
     *
     * @param src The source string.
     */
    void WriteString(const char *src);
    /**
     * Read from the buffered file into a buffer, reading from
     * the file as necessary.
     *
     * @param dst The destination buffer.
     * @param dstlen The length of the destination buffer.
     * @return The number of bytes read.
     */
    ssize_t Read(void *dst, size_t dstlen) override;
    /**
     * Write one byte to the buffered file, flushing to
     * file if necessary.
     *
     * @param byte The byte to write.
     */
    void WriteByte(char byte);
    /**
     * Get one byte from the buffered file, reading from
     * the file if necessary.
     *
     * @return int The byte read, or -1 if not read.
     */
    int GetByte(void);
    /**
     * Add an unsigned int number as ASCII text in base 10 to the buffered
     * file using a fixed width with a designated fill character.
     *
     * @param num The number to add.
     * @param fieldw The number of characters to use.
     * @param fill The character to left-pad the field with,
     *     for instance '0' or ' '.
     */
    void addNum(unsigned int num, int fieldw, char fill);
    /**
     * Get the number of bytes left to read from the buffered
     * file. This is the sum of bytes left in the buffer, and
     * the number of bytes left in the file that has not yet
     * been read into the buffer.
     *
     * @return The number of bytes left.
     */
    uint64_t BytesLeft(void) const;
    /**
     * Test for end of file.
     *
     * @return bool True if all bytes have been read from the
     *    buffered file.
     */
    bool Eof(void) const;
    /**
     * Get the size of the file.
     *
     * @return int64_t The size of the file.
     */
    int64_t GetSize () override;
    /**
     * Truncate or extend the file to a new size. Required write
     * access.
     *
     * @return bool True if successful.
     */
    bool SetSize (int64_t s) override;
    /**
     * Test if the file is opened.
     *
     * @return bool True if the file is currently opened.
     */
    bool IsOpened () const override;
    /**
     * Force completion of pending disk writes (flush cache).
     */
    bool Sync() override;
    /**
     * Get the time the file was last modified.
     *
     * @return time_t The last modification time.
     */
    time_t GetModificationTime() override;
    /**
     * Turn on direct IO.
     */
    void EnableDirectIO() override;
    void EnableSyncWrites() override;

    /**
     * Flush the buffer. If in write mode, write the buffer to
     * the file, then reset the buffer.
     */
    void Flush();
    /**
     * Flush the buffer, and close the file instance.
     * @return The result of the Close operation.
     */
    bool Close () override;
    /**
     * Get the buffered file position, in bytes.
     * This takes into account the data in the buffer, that has
     * been read, or not written to the file.
     *
     * @return int64_t The file position.
     */
    int64_t GetPosition () override;
    /**
     * Set the position in the file. The next read or write
     * will continue from this position.
     *
     * @param s The position.
     * @return bool True if successful.
     */
    bool SetPosition(int64_t s) override;

    /**
     * Get name of buffered file.
     *
     * @return name of buffered file, or NULL if no file.
     */
    const char *GetFileName() const override;

    /**
     * Just forwarded to the real file to support FastOS_FileInterface.
     */
    bool Open(unsigned int, const char*) override;
    bool Delete() override;

    void alignEndForDirectIO();
};
