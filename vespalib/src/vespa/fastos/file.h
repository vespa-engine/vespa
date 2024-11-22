// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitions for FastOS_File and FastOS_StatInfo.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once

#include <vespa/vespalib/util/time.h>
#include <string>

#define FASTOS_PREFIX(a) FastOS_##a

constexpr int FASTOS_FILE_OPEN_READ      = (1<<0);
constexpr int FASTOS_FILE_OPEN_WRITE     = (1<<1);
constexpr int FASTOS_FILE_OPEN_EXISTING  = (1<<2);
constexpr int FASTOS_FILE_OPEN_CREATE    = (1<<3);
constexpr int FASTOS_FILE_OPEN_TRUNCATE  = (1<<4);
constexpr int FASTOS_FILE_OPEN_DIRECTIO  = (1<<7);
constexpr int FASTOS_FILE_OPEN_SYNCWRITES = (1<<9); // synchronous writes

/**
 * This class contains regular file-access functionality.
 *
 * Example (how to read 10 bytes from the file 'hello.txt'):
 *
 * @code
 *   void Foo::Bar ()
 *   {
 *      FastOS_File file("hello.txt");
 *
 *      if(file.OpenReadOnly())
 *      {
 *         char buffer[10];
 *
 *         if(file.Read(buffer, 10) == 10)
 *         {
 *            // Read success
 *         }
 *         else
 *         {
 *            // Read failure
 *         }
 *      }
 *      else
 *      {
 *         // Unable to open file 'hello.txt'
 *      }
 *   }
 * @endcode
 */

class DirectIOException : public std::exception
{
public:
    DirectIOException(const char * fileName, const void * buffer, size_t length, int64_t offset);
    ~DirectIOException();
    const char* what() const noexcept override { return _what.c_str(); }
    const void * getBuffer() const { return _buffer; }
    size_t       getLength() const { return _length; }
    int64_t      getOffset() const { return _offset; }
    const std::string & getFileName() const { return _fileName; }
private:
    std::string  _what;
    std::string  _fileName;
    const void * _buffer;
    size_t       _length;
    int64_t      _offset;
};

class FastOS_FileInterface
{
private:
    FastOS_FileInterface (const FastOS_FileInterface&);
    FastOS_FileInterface& operator=(const FastOS_FileInterface&);

    // Default options for madvise used on every file opening. Default is FADV_NORMAL
    // Set with setDefaultFAdviseOptions() application wide.
    // And setFAdviseOptions() per file.
    static int    _defaultFAdviseOptions;
    int           _fAdviseOptions;
    size_t        _chunkSize;
    void WriteBufInternal(const void *buffer, size_t length);

protected:
    static bool   _fsyncEnabled;
    std::string   _filename;
    unsigned int  _openFlags;
    bool          _directIOEnabled;
    bool          _syncWritesEnabled;

public:
    static void enableFSync() noexcept { _fsyncEnabled = true; }
    static void setDefaultFAdviseOptions(int options) noexcept { _defaultFAdviseOptions = options; }
    int getFAdviseOptions()                     const noexcept { return _fAdviseOptions; }
    void setFAdviseOptions(int options)               noexcept { _fAdviseOptions = options; }

    /**
     * Constructor. A filename could be supplied at this point, or specified
     * later using @ref Open().
     * @param filename  a filename (optional)
     */
    FastOS_FileInterface(const char *filename=nullptr);

    /**
     * Destructor. If the current file is open, the destructor will close
     * it for you.
     */
    virtual ~FastOS_FileInterface();

    /**
     * Return the filename associated with the File object. If no filename
     * has been set, an empty string is returned instead.
     * @return The filename associated with the File object
     */
    virtual const char *GetFileName() const;

    /**
     * Open a file.
     * @param openFlags    A combination of the flags: FASTOS_FILE_OPEN_READ,
     *                     (Is read-access desired?), FASTOS_FILE_OPEN_WRITE
     *                     (Is write-access desired?), and
     *                     FASTOS_FILE_OPEN_EXISTING (The file to be opened
     *                     should already exist. If the file does not exist,
     *                     the call will fail.).
     * @param filename     You may optionally specify a filename here. This
     *                     will replace the currently associated filename
     *                     (if any) set using either the constructor,
     *                     or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    virtual bool Open(unsigned int openFlags, const char *filename=nullptr) = 0;

    /**
     * Open a file for read/write access. The file will be created if it does
     * not already exist.
     * @param filename       You may optionally specify a filename here. This
     *                       will replace the currently associated filename
     *                       (if any) set using either the constructor,
     *                       or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    bool OpenReadWrite(const char *filename=nullptr);

    /**
     * Open a file for read access. This method fails if the file does
     * not already exist.
     * @param abortIfNotExist  Abort the program if the file does not exist.
     * @param filename         You may optionally specify a filename here. This
     *                         will replace the currently associated filename
     *                         (if any) set using either the constructor,
     *                         or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    bool OpenReadOnlyExisting (bool abortIfNotExist=false, const char *filename=nullptr);

    /**
     * Open a file for write access. If the file does not exist, it is created.
     * If the file exists, it is truncated to 0 bytes.
     * @param filename         You may optionally specify a filename here. This
     *                         will replace the currently associated filename
     *                         (if any) set using either the constructor,
     *                         or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    bool OpenWriteOnlyTruncate(const char *filename=nullptr);

    /**
     * Open a file for write access. This method fails if the file does
     * not already exist.
     * @param abortIfNotExist  Abort the program if the file does not exist.
     * @param filename         You may optionally specify a filename here. This
     *                         will replace the currently associated filename
     *                         (if any) set using either the constructor,
     *                         or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    bool OpenWriteOnlyExisting (bool abortIfNotExist=false, const char *filename=nullptr);

    /**
     * Open a file for read-access only. This method fails if the file does
     * not already exist.
     * @param filename       You may optionally specify a filename here. This
     *                       will replace the currently associated filename
     *                       (if any) set using either the constructor,
     *                       or a previous call to @ref Open().
     * @return Boolean success/failure
     */
    bool OpenReadOnly(const char *filename=nullptr);

    /**
     * Open a file for write-access only.  The file will be created if it does
     * not already exist.
     * @param filename       You may optionally specify a filename here. This
     *                       will replace the currently associated filename
     *                       (if any) set using either the constructor,
     *                       or a previous call to Open().
     * @return Boolean success/failure
     */
    bool OpenWriteOnly(const char *filename=nullptr);

    /**
     * Close the file. The call will successfully do nothing if the file
     * already is closed.
     * @return Boolean success/failure
     */
    [[nodiscard]] virtual bool Close() = 0;

    /**
     * Is the file currently opened?
     * @return true if the file is opened, else false
     */
    virtual bool IsOpened() const = 0;

    /**
     * Read [length] bytes into [buffer].
     * @param buffer  buffer pointer
     * @param length  number of bytes to read
     * @return The number of bytes which was actually read,
     *         or -1 on error.
     */
    [[nodiscard]] virtual ssize_t Read(void *buffer, size_t length) = 0;

    /**
     * Write [len] bytes from [buffer].  This is just a wrapper for
     * Write2, which does the same thing but returns the number of
     * bytes written instead of just a bool.
     * @param buffer  buffer pointer
     * @param len     number of bytes to write
     * @return Boolean success/failure
     */
    [[nodiscard]] bool CheckedWrite(const void *buffer, size_t len);

    /**
     * Write [len] bytes from [buffer].
     * @param buffer  buffer pointer
     * @param len     number of bytes to write
     * @return The number of bytes actually written, or -1 on error
     */
    [[nodiscard]] virtual ssize_t Write2(const void *buffer, size_t len) = 0;

    /**
     * Read [length] bytes into [buffer]. Caution! If the actual number
     * of bytes read != [length], an error message is printed to stderr
     * and the program aborts.
     *
     * The method is included for backwards compatibility reasons.
     * @param buffer  buffer pointer
     * @param length  number of bytes to read
     */
    virtual void ReadBuf(void *buffer, size_t length);

    /**
     * Write [length] bytes from [buffer] in chunks. Caution! If the write fails,
     * an error message is printed to stderr and the program aborts.
     *
     * The method is included for backwards compatibility reasons.
     * @param buffer  buffer pointer
     * @param length  number of bytes to write
     */
    virtual void WriteBuf(const void *buffer, size_t length);


    /**
     * Read [length] bytes at file offset [readOffset] into [buffer].
     * Only thread-safe if an OS-specific implementation exists.
     *
     * Caution! If the actual number of bytes read != [length], an
     * error message is printed to stderr and the program aborts.
     *
     * @param buffer      buffer pointer
     * @param length      number of bytes to read
     * @param readOffset  file offset where the read operation starts
     */
    virtual void ReadBuf(void *buffer, size_t length, int64_t readOffset);

    /**
     * Set the filepointer. The next @ref Read() or @ref Write() will
     * continue from this position.
     * @param  position position of the new file pointer (in bytes)
     * @return Boolean success/failure
     */
    virtual bool SetPosition(int64_t position) = 0;

    /**
     * Get the filepointer. -1 is returned if the operation fails.
     * @return current position of file pointer (in bytes)
     */
    virtual int64_t getPosition() const = 0;

    /**
     * Return the file size. This method requires that the file is
     * currently opened. If you wish to determine the file size
     * without opening the file, use @ref Stat().
     * If an error occurs, the returned value is -1.
     * @return file size (in bytes)
     */
    virtual int64_t getSize() const = 0;

    /**
     * Force completion of pending disk writes (flush cache).
     */
    [[nodiscard]] virtual bool Sync() = 0;

    /**
     * Are we in some kind of file read mode?
     */
    bool IsReadMode() {
        return ((_openFlags & FASTOS_FILE_OPEN_READ) != 0);
    }

    /**
     * Are we in some kind of file write mode?
     */
    bool IsWriteMode() {
        return ((_openFlags & FASTOS_FILE_OPEN_WRITE) != 0);
    }

    /**
     * Truncate or extend the file to the new size.
     * The file pointer is also set to [newSize].
     * @ref SetSize() requires write access to the file.
     * @param newSize  New file size
     * @return         Boolean success/failure.
     */
    virtual bool SetSize(int64_t newSize) = 0;

    /**
     * Enable direct disk I/O (disable OS buffering & cache). Reads
     * and writes will be performed directly to or from the user
     * program buffer, provided appropriate size and alignment
     * restrictions are met. If the restrictions are not met, a
     * normal read or write is performed as a fallback.
     * Call this before opening a file, and query
     * @ref GetDirectIORestrictions() after a file is opened to get the
     * neccessary alignment restrictions. It is possible that direct
     * disk I/O could not be enabled. In that case
     * @ref GetDirectIORestrictions will return false.
     */
    virtual void EnableDirectIO();

    virtual void EnableSyncWrites();

    bool useSyncWrites() const { return _syncWritesEnabled; }

    size_t getChunkSize() const { return _chunkSize; }

    /**
     * Get restrictions for direct disk I/O. The file should be opened
     * before this method is called.
     * Even though direct disk I/O is enabled through @ref EnableDirectIO(),
     * this method could still return false, indicating that direct disk I/O
     * is not being used for this file. This could be caused by either: no
     * OS support for direct disk I/O, direct disk I/O might only be implemented
     * for certain access-modes, the file is on a network drive or other
     * partition where direct disk I/O is disallowed.
     *
     * The restriction-arguments are always filled with valid data, independant
     * of the return code and direct disk I/O availability.
     *
     * @param  memoryAlignment      Buffer alignment restriction
     * @param  transferGranularity  All transfers must be a multiple of
     *                              [transferGranularity] bytes. All
     *                              file offsets for these transfers must
     *                              also be a multiple of [transferGranularity]
     *                              bytes.
     * @param  transferMaximum      All transfers must be <= [transferMaximum]
     *                              bytes.
     * @return  True if direct disk I/O is being used for this file, else false.
     */
    virtual bool
    GetDirectIORestrictions(size_t &memoryAlignment,
                            size_t &transferGranularity,
                            size_t &transferMaximum);

    /**
     * Retrieve the required padding for direct I/O to be used.
     *
     * @param  offset     File offset
     * @param  buflen     Buffer length
     * @param  padBefore  Number of pad bytes needed in front of the buffer
     * @param  padAfter   Number of pad bytes needed after the buffer
     *
     * @return   True if the file access can be accomplished with
     *           direct I/O, else false.
     */
    virtual bool DirectIOPadding(int64_t offset,
                                 size_t buflen,
                                 size_t &padBefore,
                                 size_t &padAfter);

    /**
     * Allocate a buffer for normal io.
     * @param  byteSize          Number of bytes to be allocated
     * @return pointer value or nullptr if out of memory
     *         Use free() with this pointer to deallocate the buffer.
     */
    static void *allocateIOBuffer(size_t byteSize);

    /**
     * Get maximum memory alignment for directio buffers.
     * @return maximum memory alignment for directio buffers.
     */
    static size_t getMaxDirectIOMemAlign();

    /**
     * Allocate a buffer properly aligned with regards to direct io
     * access restrictions.
     * @param  byteSize          Number of bytes to be allocated
     * @return Aligned pointer value or nullptr if out of memory.
     *         Use free() with this pointer to deallocate the buffer.
     */
    virtual void *AllocateDirectIOBuffer(size_t byteSize);

    /**
     * Enable mapping of complete file contents into the address space of the
     * running process.  This only works for small files.  This operation
     * will be ignored on some file types.
     */
    virtual void enableMemoryMap(int mmapFlags);

    /**
     * Inquiry about where in memory file data is located.
     * @return location of file data in memory.  If the file is not mapped,
     * nullptr is returned.
     */
    virtual void *MemoryMapPtr(int64_t position) const;

    /**
     * Inquiry if file content is mapped into memory.
     * @return true if file is mapped in memory, false otherwise.
     */
    virtual bool IsMemoryMapped() const;

    /**
     * Will drop whatever is in the FS cache when called. Does not have effect in the future.
     **/
    virtual void dropFromCache() const;

    enum Error
    {
        ERR_ZERO = 1,   // No error                       New style
        ERR_NOENT,      // No such file or directory
        ERR_NOMEM,      // Not enough memory
        ERR_ACCES,      // Permission denied
        ERR_EXIST,      // File exists
        ERR_INVAL,      // Invalid argument
        ERR_NFILE,      // File table overflow
        ERR_MFILE,      // Too many open files
        ERR_NOSPC,      // No space left on device
        ERR_INTR,       // interrupt
        ERR_AGAIN,      // Resource unavailable, try again
        ERR_BUSY,       // Device or resource busy
        ERR_IO,         // I/O error
        ERR_PERM,       // Not owner
        ERR_NODEV,      // No such device
        ERR_NXIO,       // Device not configured
        ERR_UNKNOWN,    // Unknown

        ERR_EZERO = 1,   // No error                      Old style
        ERR_ENOENT,      // No such file or directory
        ERR_ENOMEM,      // Not enough memory
        ERR_EACCES,      // Permission denied
        ERR_EEXIST,      // File exists
        ERR_EINVAL,      // Invalid argument
        ERR_ENFILE,      // File table overflow
        ERR_EMFILE,      // Too many open files
        ERR_ENOSPC,      // No space left on device
        ERR_EINTR,       // interrupt
        ERR_EAGAIN,      // Resource unavailable, try again
        ERR_EBUSY,       // Device or resource busy
        ERR_EIO,         // I/O error
        ERR_EPERM,       // Not owner
        ERR_ENODEV,      // No such device
        ERR_ENXIO        // Device not configured
    };


    /**
     * If a file operation fails, the error code can be retrieved
     * via this method. See @ref Error for possible error codes.
     * @return Error code
     */
    static Error GetLastError();

    /**
     * Similar to @ref GetLastError(), but this method returns a string
     * instead of an error code.
     * @return String describing the last error
     */
    static std::string getLastErrorString();
};


/**
 * The class serves as a container for information returned by
 * @ref FastOS_File::Stat().
 */
class FastOS_StatInfo
{
public:
    /**
     * Possible error codes.
     */
    enum StatError
    {
        Ok,           //!< ok
        Unknown,      //!< unknown error
        FileNotFound  //!< file not found error
    };

    StatError _error;

    /**
     * Is it a regular file? This field is only valid if @ref _error is
     * @ref Ok.
     */
    bool _isRegular;

    /**
     * Is it a directory? This field is only valid if @ref _error is
     * @ref Ok.
     */
    bool _isDirectory;

    /**
     * File size. This field is only valid if @ref _error is
     * @ref Ok.
     */
    int64_t _size;

    /**
     * Time of last modification in seconds.
     */
    vespalib::system_time _modifiedTime;
};

#ifdef __linux__
#include <vespa/fastos/linux_file.h>
typedef FastOS_Linux_File FASTOS_PREFIX(File);
#else
#include <vespa/fastos/unix_file.h>
typedef FastOS_UNIX_File FASTOS_PREFIX(File);
#endif
