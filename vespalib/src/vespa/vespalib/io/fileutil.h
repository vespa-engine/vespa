// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file fileutil.h
 *
 * This class contains file utilities that gives a C++ interface to
 * file operations, and which throws exceptions on failures. The exceptions
 * should contain decent failure messages, such that you don't have to worry
 * about the errno error codes.
 *
 * It provides the following:
 *   - A C++ interface for file IO.
 *   - Exceptions for critical behavior, such that one don't need to handle
 *     critical errors low level in code (if using exceptions that is).
 *   - Functionality for recursive deletion of directories.
 *   - Functionality for copying instead of renaming moving between filesystems.
 *   - Functionality for creating missing parent directories on operations
 *     creating files and directories.
 *   - Functionality for lazy file opening. (Useful when using caches)
 *   - An interface that doesn't expose low level C++ file IO, so it can
 *     hopefully stay static even if we want to change low level implementation.
 *   - Ownership of file descriptor to avoid leaks on exceptions.
 *   - A low level layer that can be used to inject disk errors in tests, and
 *     count operation types and the like. Useful in testing.
 *
 * @author Hakon Humberset
 */

#pragma once

#include <unistd.h>
#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memory.h>

namespace vespalib {

/**
 * @brief Simple metadata about a file or directory.
 **/
struct FileInfo {
    typedef std::unique_ptr<FileInfo> UP;

    bool  _plainfile;
    bool  _directory;
    bool  _symlink;
    off_t _size;

    bool operator==(const FileInfo&) const;
};

std::ostream& operator<<(std::ostream&, const FileInfo&);

/**
 * @brief A File instance is used to access a single open file.
 *
 * By using this class you get automatic closing of files when the
 * object is destructed, and since the class knows the filename, you
 * will get sensible exceptions containing file names if something
 * goes wrong.
 */
class File {
private:
    int _fd;
    int _flags;
    vespalib::string _filename;
    bool _close;
    mutable int _fileReads; // Tracks number of file reads done on this file
    mutable int _fileWrites; // Tracks number of file writes done in this file

    /**
     * Verify that direct I/O alignment preconditions hold. Triggers assertion
     * failure on violations.
     */
    void verifyDirectIO(uint64_t buf, size_t bufsize, off_t offset) const;

public:
    typedef std::unique_ptr<File> UP;

    /**
     * If failing to open file using direct IO it will retry using cached IO.
     */
    enum Flag { READONLY = 1, CREATE = 2, DIRECTIO = 4, TRUNC = 8 };

    /** Create a file instance, without opening the file. */
    File(vespalib::stringref filename);

    /** Create a file instance of an already open file. */
    File(int fileDescriptor, vespalib::stringref filename);

    /** Copying a file instance, moves any open file descriptor. */
    File(File& f);
    File& operator=(File& f);

    /** Closes the file if not instructed to do otherwise. */
    virtual ~File();

    /**
     * Make this instance point at another file.
     * Closes the old file it it was open.
     */
    void setFilename(vespalib::stringref filename);

    const vespalib::string& getFilename() const { return _filename; }

    virtual void open(int flags, bool autoCreateDirectories = false);

    bool isOpen() const { return (_fd != -1); }
    bool isOpenWithDirectIO() const { return ((_flags & DIRECTIO) != 0); }

    /**
     * Whether or not file should be closed when this instance is destructed.
     * By default it will be closed.
     */
    void closeFileWhenDestructed(bool close);

    virtual int getFileDescriptor() const { return _fd; }

    /**
     * Get information about the current file. If file is opened, file descriptor
     * will be used for stat. If file is not open, and the file does not exist
     * yet, you will get fileinfo describing an empty file.
     */
    virtual FileInfo stat() const;

    /**
     * Get the filesize of a file, specified by a file descriptor.
     *
     * @throw IoException If we failed to stat the file.
     */
    virtual off_t getFileSize() const { return stat()._size; }

    /**
     * Resize the currently open file to a given size,
     * truncating or extending file with 0 bytes according to what the former
     * size was.
     *
     * @param size new size of file
     * @throw IoException If we failed to resize the file.
     */
    virtual void resize(off_t size);

    /**
     * Writes data to file.
     *
     * If file is opened in direct I/O mode, arguments buf, bufsize and offset
     * MUST all be 512-byte aligned!
     *
     * @param buf         The buffer to get data from.
     * @param bufsize     Number of bytes to write.
     * @param offset      The position in the file where write should happen.
     * @throw IoException If we failed to write to the file.
     * @return            Always return bufsize.
     */
    virtual off_t write(const void *buf, size_t bufsize, off_t offset);

    /**
     * Read characters from a file.
     *
     * If file is opened in direct I/O mode, arguments buf, bufsize and offset
     * MUST all be 512-byte aligned!
     *
     * @param buf         The buffer into which to read.
     * @param bufsize     Maximum number of bytes to read.
     * @param offset      Position in file to read from.
     * @throw IoException If we failed to read from file.
     * @return            The number of bytes actually read. If less than
     *                    bufsize, this indicates that EOF was reached.
     */
    virtual size_t read(void *buf, size_t bufsize, off_t offset) const;

    /**
     * Read the file into a string.
     *
     * This is a small wrapper around read(), see there for more info.
     *
     * @throw   IoException If we failed to read from file.
     * @return  The content of the file.
     */
    vespalib::string readAll() const;

    /**
     * Read a file into a string.
     *
     * This is a convenience function for the member functions open() and
     * readAll(), see there for more details.
     *
     * @throw   IoException If we failed to read from file.
     * @return  The content of the file.
     */
    static vespalib::string readAll(vespalib::stringref path);

    /**
     * Sync file or directory.
     *
     * This is a convenience function for the member functions open() and
     * sync(), see there for more details.
     *
     * @throw IoException If we failed to sync the file.
     */
    static void sync(vespalib::stringref path);

    virtual void sync();
    virtual bool close();
    virtual bool unlink();

    int getFileReadCount() const { return _fileReads; }
    int getFileWriteCount() const { return _fileWrites; }
};

/**
 * Get the current working directory.
 *
 * @throw IoException On failure.
 */
extern vespalib::string getCurrentDirectory();

/**
 * Change working directory.
 *
 * @param directory   The directory to change to.
 * @throw IoException If we failed to change to the new working directory.
 */
extern void chdir(const vespalib::string & directory);

/**
 * Stat a file.
 *
 * @throw  IoException If we failed to stat the file.
 * @return A file info object if everything went well, a null pointer if the
 *         file was not found.
 */
extern FileInfo::UP stat(const vespalib::string & path);

/**
 * Stat a file. Give info on symlink rather than on file pointed to.
 *
 * @throw  IoException If we failed to stat the file.
 * @return A file info object if everything went well, a null pointer if the
 *         file was not found.
 */
extern FileInfo::UP lstat(const vespalib::string & path);

/**
 * Check if a file exists or not. See also pathExists.
 *
 * @throw IoException If we failed to stat the file.
 */
extern bool fileExists(const vespalib::string & path);

/**
 * Check if a path exists, i.e. whether it's a symbolic link, regular file,
 * directory, etc.
 *
 * This function is the same as fileExists, except if path is a symbolic link:
 * This function returns true, while fileExists returns true only if the path
 * the symbolic link points to exists.
 */
extern inline bool pathExists(const vespalib::string & path) {
    return (lstat(path).get() != 0);
}

/**
 * Get the filesize of the given file. Ignoring if it exists or not.
 * (None-existing files will be reported to have size zero)
 */
extern inline off_t getFileSize(const vespalib::string & path) {
    FileInfo::UP info(stat(path));
    return (info.get() == 0 ? 0 : info->_size);
}

/**
 * Check if a file is a plain file or not.
 *
 * @return True if it is a plain file, false if it don't exist or isn't.
 * @throw IoException If we failed to stat the file.
 */
extern inline bool isPlainFile(const vespalib::string & path) {
    FileInfo::UP info(stat(path));
    return (info.get() && info->_plainfile);
}

/**
 * Check if a file is a directory or not.
 *
 * @return True if it is a directory, false if it don't exist or isn't.
 * @throw IoException If we failed to stat the file.
 */
extern bool isDirectory(const vespalib::string & path);

/**
 * Check whether a path is a symlink.
 *
 * @return True if path exists and is a symbolic link.
 * @throw IoException If there's an unexpected stat failure.
 */
extern inline bool isSymLink(const vespalib::string & path) {
    FileInfo::UP info(lstat(path));
    return (info.get() && info->_symlink);
}

/**
 * Creates a symbolic link named newPath which contains the string oldPath.
 *
 * IMPORTANT: from the spec:
 * "Symbolic links are interpreted at run time as if the contents of the link had
 * been substituted into the path being followed to find a file or directory."
 *
 * This means oldPath is _relative_ to the directory in which newPath resides!
 *
 * @param oldPath Target of symbolic link.
 * @param newPath Relative link to be created. See above note for semantics.
 * @throw IoException if we fail to create the symlink.
 */
extern void symlink(const vespalib::string & oldPath,
                    const vespalib::string & newPath);

/**
 * Read and return the contents of symbolic link at the given path.
 *
 * @param path Path to symbolic link.
 * @return Contents of symbolic link.
 * @throw IoException if we cannot read the link.
 */
extern vespalib::string readLink(const vespalib::string & path);

/**
 * Remove the given file.
 *
 * @param filename name of file.
 * @return True if file was removed, false if it did not exist.
 * @throw IoException If we failed to unlink the file.
 */
extern bool unlink(const vespalib::string & filename);

/**
 * Rename the file at frompath to topath.
 *
 * @param frompath old name of file.
 * @param topath new name of file.
 *
 * @param copyDeleteBetweenFilesystems whether a copy-and-delete
 * operation should be performed if rename crosses a file system
 * boundary, or not.
 *
 * @param createTargetDirectoryIfMissing whether the target directory
 * should be created if it's missing, or not.
 *
 * @throw IoException If we failed to rename the file.
 * @throw std::filesystem::filesystem_error If we failed to create a target directory
 * @return True if file was renamed, false if frompath did not exist.
 */
extern bool rename(const vespalib::string & frompath,
                   const vespalib::string & topath,
                   bool copyDeleteBetweenFilesystems = true,
                   bool createTargetDirectoryIfMissing = false);

/**
 * Copies a file to a destination using Direct IO.
 */
extern void copy(const vespalib::string & frompath,
                 const vespalib::string & topath,
                 bool createTargetDirectoryIfMissing = false,
                 bool useDirectIO = true);

/**
 * List the contents of the given directory.
 */
typedef std::vector<vespalib::string> DirectoryList;
extern DirectoryList listDirectory(const vespalib::string & path);

extern MallocAutoPtr getAlignedBuffer(size_t size);

string dirname(stringref name);
string getOpenErrorString(const int osError, stringref name);

} // vespalib
