// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileutil.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <ostream>
#include <cassert>
#include <filesystem>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.io.fileutil");

namespace vespalib {

namespace {

    FileInfo::UP
    processStat(struct stat& filestats, bool result, stringref path) {
        FileInfo::UP resval;
        if (result) {
            resval.reset(new FileInfo);
            resval->_plainfile = S_ISREG(filestats.st_mode);
            resval->_directory = S_ISDIR(filestats.st_mode);
            resval->_symlink = S_ISLNK(filestats.st_mode);
            resval->_size = filestats.st_size;
        } else if (errno != ENOENT) {
            asciistream ost;
            ost << "An IO error occured while statting '" << path << "'. "
                << "errno(" << errno << "): " << getErrorString(errno);
            throw IoException(ost.str(), IoException::getErrorType(errno),
                              VESPA_STRLOC);
        }
        LOG(debug, "stat(%s): Existed? %s, Plain file? %s, Directory? %s, "
                   "Size: %" PRIu64,
            string(path).c_str(),
            resval.get() ? "true" : "false",
            resval.get() && resval->_plainfile ? "true" : "false",
            resval.get() && resval->_directory ? "true" : "false",
            resval.get() ? resval->_size : 0);
        return resval;
    }

string
safeStrerror(int errnum)
{
    return getErrorString(errnum);
}

}

bool
FileInfo::operator==(const FileInfo& fi) const
{
    return (_size == fi._size && _plainfile == fi._plainfile
            && _directory == fi._directory);
}

std::ostream&
operator<<(std::ostream& out, const FileInfo& info)
{
    out << "FileInfo(size: " << info._size;
    if (info._plainfile) out << ", plain file";
    if (info._directory) out << ", directory";
    out << ")";
    return out;
}

File::File(stringref filename)
    : _fd(-1),
      _flags(0),
      _filename(filename),
      _close(true),
      _fileReads(0),
      _fileWrites(0)
{
}

File::File(int fileDescriptor, stringref filename)
    : _fd(fileDescriptor),
      _flags(0),
      _filename(filename),
      _close(true),
      _fileReads(0),
      _fileWrites(0)
{
}

File::~File()
{
    if (_close && _fd != -1) close();
}

File::File(File& f)
    : _fd(f._fd),
      _flags(f._flags),
      _filename(f._filename),
      _close(f._close),
      _fileReads(f._fileReads),
      _fileWrites(f._fileWrites)
{
    f._fd = -1;
    f._flags = 0;
    f._close = true;
    f._fileReads = 0;
    f._fileWrites = 0;
}

File&
File::operator=(File& f)
{
    if (_close && _fd != -1) close();
    _fd = f._fd;
    _flags = f._flags;
    _filename = f._filename;
    _close = f._close;
    _fileReads = f._fileReads;
    _fileWrites = f._fileWrites;
    f._fd = -1;
    f._flags = 0;
    f._close = true;
    f._fileReads = 0;
    f._fileWrites = 0;
    return *this;
}

void
File::setFilename(stringref filename)
{
    if (_filename == filename) return;
    if (_close && _fd != -1) close();
    _filename = filename;
    _fd = -1;
    _flags = 0;
    _close = true;
}

namespace {
    int openAndCreateDirsIfMissing(const string & filename, int flags,
                                   bool createDirsIfMissing)
    {
        int fd = ::open(filename.c_str(), flags, 0644);
        if (fd < 0 && errno == ENOENT && ((flags & O_CREAT) != 0)
            && createDirsIfMissing)
        {
            auto pos = filename.rfind('/');
            if (pos != string::npos) {
                string path(filename.substr(0, pos));
                std::filesystem::create_directories(std::filesystem::path(path));
                LOG(spam, "open(%s, %d): Retrying open after creating parent "
                          "directories.", filename.c_str(), flags);
                fd = ::open(filename.c_str(), flags, 0644);
            }
        }
        return fd;
    }
}

void
File::open(int flags, bool autoCreateDirectories) {
    if ((flags & File::READONLY) != 0) {
        if ((flags & File::CREATE) != 0) {
            throw IllegalArgumentException(
                    "Cannot use READONLY and CREATE options at the same time",
                    VESPA_STRLOC);
        }
        if ((flags & File::TRUNC) != 0) {
            throw IllegalArgumentException(
                    "Cannot use READONLY and TRUNC options at the same time",
                    VESPA_STRLOC);
        }
        if (autoCreateDirectories) {
            throw IllegalArgumentException(
                    "No point in auto-creating directories on read only access",
                    VESPA_STRLOC);
        }
    }
    int openflags = ((flags & File::READONLY) != 0 ? O_RDONLY : O_RDWR)
                  | ((flags & File::CREATE)  != 0 ? O_CREAT : 0)
#ifdef __linux__
                  | ((flags & File::DIRECTIO) != 0 ? O_DIRECT : 0)
#endif
                  | ((flags & File::TRUNC) != 0 ? O_TRUNC: 0);
    int fd = openAndCreateDirsIfMissing(_filename, openflags, autoCreateDirectories);
#ifdef __linux__
    if (fd < 0 && ((flags & File::DIRECTIO) != 0)) {
        openflags = (openflags ^ O_DIRECT);
        flags = (flags ^ DIRECTIO);
        LOG(debug, "open(%s, %d): Retrying without direct IO due to failure "
                   "opening with errno(%d): %s",
            _filename.c_str(), flags, errno, safeStrerror(errno).c_str());
        fd = openAndCreateDirsIfMissing(_filename, openflags, autoCreateDirectories);
    }
#endif
    if (fd < 0) {
        asciistream ost;
        ost << "open(" << _filename << ", 0x"
            << hex << flags << dec << "): Failed, errno(" << errno
            << "): " << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    _flags = flags;
    if (_close && _fd != -1) close();
    _fd = fd;
    LOG(debug, "open(%s, %d). File opened with file descriptor %d.",
        _filename.c_str(), flags, fd);
}

void
File::closeFileWhenDestructed(bool closeOnDestruct)
{
    _close = closeOnDestruct;
}

FileInfo
File::stat() const
{
    struct ::stat filestats;
    FileInfo::UP result;
    if (isOpen()) {
        result = processStat(filestats, fstat(_fd, &filestats) == 0, _filename);
        assert(result.get()); // The file must exist in a file instance
    } else {
        result = processStat(filestats,
                             ::stat(_filename.c_str(), &filestats) == 0,
                             _filename);
            // If the file does not exist yet, act like it does. It will
            // probably be created when opened.
        if (result.get() == 0) {
            result.reset(new FileInfo());
            result->_size = 0;
            result->_directory = false;
            result->_plainfile = true;
        }
    }
    return *result;
}

void
File::resize(off_t size)
{
    if (ftruncate(_fd, size) != 0) {
        asciistream ost;
        ost << "resize(" << _filename << ", " << size << "): Failed, errno("
            << errno << "): " << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    LOG(debug, "resize(%s): Resized to %" PRIu64 " bytes.",
        _filename.c_str(), size);
}

void
File::verifyDirectIO(uint64_t buf, size_t bufsize, off_t offset) const
{
    if (offset % 512 != 0) {
        LOG(error,
            "Access to file %s failed because offset %" PRIu64 " wasn't 512-byte "
            "aligned. Buffer memory address was %" PRIx64 ", length %zu",
            _filename.c_str(), static_cast<uint64_t>(offset), buf, bufsize);
        assert(false);
    }
    if (buf % 512 != 0) {
        LOG(error,
            "Access to file %s failed because buffer memory address %" PRIx64 " "
            "wasn't 512-byte aligned. Offset was %" PRIu64 ", length %zu",
            _filename.c_str(), buf, static_cast<uint64_t>(offset), bufsize);
        assert(false);
    }
    if (bufsize % 512 != 0) {
        LOG(error,
            "Access to file %s failed because buffer size %zu wasn't 512-byte "
            "aligned. Buffer memory address was %" PRIx64 ", offset %" PRIu64,
            _filename.c_str(), bufsize, buf, static_cast<uint64_t>(offset));
        assert(false);
    }
}

off_t
File::write(const void *buf, size_t bufsize, off_t offset)
{
    ++_fileWrites;
    size_t left = bufsize;
    LOG(debug, "write(%s): Writing %zu bytes at offset %" PRIu64 ".",
        _filename.c_str(), bufsize, offset);

    if (_flags & DIRECTIO) {
        verifyDirectIO((uint64_t)buf, bufsize, offset);
    }

    while (left > 0) {
        ssize_t written = ::pwrite(_fd, buf, left, offset);
        if (written > 0) {
            LOG(spam, "write(%s): Wrote %zd bytes at offset %" PRIu64 ".",
                _filename.c_str(), written, offset);
            left -= written;
            buf = ((const char*) buf) + written;
            offset += written;
        } else if (written == 0) {
            LOG(spam, "write(%s): Wrote %zd bytes at offset %" PRIu64 ".",
                _filename.c_str(), written, offset);
            assert(false); // Can this happen?
        } else if (errno != EINTR && errno != EAGAIN) {
            asciistream ost;
            ost << "write(" << _fd << ", " << buf
                << ", " << left << ", " << offset << "), Failed, errno("
                << errno << "): " << safeStrerror(errno);
            throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
        }
    }
    return bufsize;
}

size_t
File::read(void *buf, size_t bufsize, off_t offset) const
{
    ++_fileReads;
    size_t remaining = bufsize;
    LOG(debug, "read(%s): Reading %zu bytes from offset %" PRIu64 ".",
        _filename.c_str(), bufsize, offset);

    if (_flags & DIRECTIO) {
        verifyDirectIO((uint64_t)buf, bufsize, offset);
    }

    while (remaining > 0) {
        ssize_t bytesread = ::pread(_fd, buf, remaining, offset);
        if (bytesread > 0) {
            LOG(spam, "read(%s): Read %zd bytes from offset %" PRIu64 ".",
                _filename.c_str(), bytesread, offset);
            remaining -= bytesread;
            buf = ((char*) buf) + bytesread;
            offset += bytesread;
            if (((_flags & DIRECTIO) != 0) && ((bytesread % 512) != 0) && (offset == getFileSize())) {
                LOG(spam, "read(%s): Found EOF. Directio read to unaligned file end at offset %" PRIu64 ".",
                    _filename.c_str(), offset);
                break;
            }
        } else if (bytesread == 0) { // EOF
            LOG(spam, "read(%s): Found EOF. Zero bytes read from offset %" PRIu64 ".",
                _filename.c_str(), offset);
            break;
        } else if (errno != EINTR && errno != EAGAIN) {
            asciistream ost;
            ost << "read(" << _fd << ", " << buf << ", " << remaining << ", "
                << offset << "): Failed, errno(" << errno << "): "
                << safeStrerror(errno);
            throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
        }
    }
    return bufsize - remaining;
}

vespalib::string
File::readAll() const
{
    vespalib::string content;

    // Limit ourselves to 4K on the stack. If this becomes a problem we should
    // allocate on the heap.
    char buffer[4_Ki];
    off_t offset = 0;

    while (true) {
        size_t numRead = read(buffer, sizeof(buffer), offset);
        offset += numRead;
        content.append(buffer, numRead);

        if (numRead < sizeof(buffer)) {
            // EOF
            return content;
        }
    }
}

vespalib::string
File::readAll(vespalib::stringref path)
{
    File file(path);
    file.open(File::READONLY);
    return file.readAll();
}

void
File::sync()
{
    if (_fd != -1) {
        if (::fsync(_fd) == 0) {
            LOG(debug, "sync(%s): File synchronized with disk.", _filename.c_str());
        } else {
            LOG(warning, "fsync(%s): Failed to sync file. errno(%d): %s",
                _filename.c_str(), errno, safeStrerror(errno).c_str());
        }
    } else {
        LOG(debug, "sync(%s): Called on closed file.", _filename.c_str());
    }
}

void
File::sync(vespalib::stringref path)
{
    File file(path);
    file.open(READONLY);
    file.sync();
    file.close();
}

bool
File::close()
{
    if (_fd != -1) {
        if (::close(_fd) == 0) {
            LOG(debug, "close(%s): Closed file with descriptor %i.", _filename.c_str(), _fd);
            _fd = -1;
            return true;
        } else {
            LOG(warning, "close(%s): Failed to close file. errno(%d): %s",
                _filename.c_str(), errno, safeStrerror(errno).c_str());
            _fd = -1;
            return false;
        }
    } else {
        LOG(debug, "close(%s): Called on closed file.", _filename.c_str());
    }
    return true;
}

bool
File::unlink()
{
    close();
    return vespalib::unlink(_filename);
}

string
getCurrentDirectory()
{
    MallocAutoPtr ptr = getcwd(0, 0);
    if (ptr.get() != 0) {
        return string(static_cast<char*>(ptr.get()));
    }
    asciistream ost;
    ost << "getCurrentDirectory(): Failed, errno(" << errno << "): " << safeStrerror(errno);
    throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
}

void
symlink(const string & oldPath, const string & newPath)
{
    if (::symlink(oldPath.c_str(), newPath.c_str())) {
        asciistream ss;
        const int err = errno;
        ss << "symlink(" << oldPath << ", " << newPath
           << "): Failed, errno(" << err << "): "
           << safeStrerror(err);
        throw IoException(ss.str(), IoException::getErrorType(err), VESPA_STRLOC);
    }
}

string
readLink(const string & path)
{
    char buf[256];
    ssize_t bytes(::readlink(path.c_str(), buf, sizeof(buf)));
    if (bytes < 0) {
        asciistream ss;
        const int err = errno;
        ss << "readlink(" << path << "): Failed, errno(" << err << "): "
           << safeStrerror(err);
        throw IoException(ss.str(), IoException::getErrorType(err), VESPA_STRLOC);
    }
    return string(buf, bytes);
}

void
chdir(const string & directory)
{
    if (::chdir(directory.c_str()) != 0) {
        asciistream ost;
        ost << "chdir(" << directory << "): Failed, errno(" << errno << "): "
            << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    LOG(debug, "chdir(%s): Working directory changed.", directory.c_str());
}

FileInfo::UP
stat(const string & path)
{
    struct ::stat filestats;
    return processStat(filestats, ::stat(path.c_str(), &filestats) == 0, path);
}

FileInfo::UP
lstat(const string & path)
{
    struct ::stat filestats;
    return processStat(filestats, ::lstat(path.c_str(), &filestats) == 0, path);
}

bool
fileExists(const string & path) {
    return (stat(path).get() != 0);
}

bool
unlink(const string & filename)
{
    if (::unlink(filename.c_str()) != 0) {
        if (errno == ENOENT) {
            return false;
        }
        asciistream ost;
        ost << "unlink(" << filename << "): Failed, errno(" << errno << "): "
            << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    LOG(debug, "unlink(%s): File deleted.", filename.c_str());
    return true;
}

bool
rename(const string & frompath, const string & topath,
       bool copyDeleteBetweenFilesystems, bool createTargetDirectoryIfMissing)
{
    LOG(spam, "rename(%s, %s): Renaming file%s.",
        frompath.c_str(), topath.c_str(),
        createTargetDirectoryIfMissing
            ? " recursively creating target directory if missing" : "");
    if (::rename(frompath.c_str(), topath.c_str()) != 0) {
        if (errno == ENOENT) {
            if (!fileExists(frompath)) return false;
            if (createTargetDirectoryIfMissing) {
                string::size_type pos = topath.rfind('/');
                if (pos != string::npos) {
                    string path(topath.substr(0, pos));
                    std::filesystem::create_directories(std::filesystem::path(path));
                    LOG(debug, "rename(%s, %s): Created target directory. Calling recursively.",
                        frompath.c_str(), topath.c_str());
                    return rename(frompath, topath, copyDeleteBetweenFilesystems, false);
                }
            } else {
                asciistream ost;
                ost << "rename(" << frompath << ", " << topath
                    << (copyDeleteBetweenFilesystems ? ", revert to copy" : "")
                    << (createTargetDirectoryIfMissing
                            ? ", create missing target" : "")
                    << "): Failed, target path does not exist.";
                throw IoException(ost.str(), IoException::NOT_FOUND,
                                  VESPA_STRLOC);
            }
        } else if (errno == EXDEV && copyDeleteBetweenFilesystems) {
            if (!fileExists(frompath)) {
                LOG(debug, "rename(%s, %s): Renaming non-existing file across "
                           "filesystems returned EXDEV rather than ENOENT.",
                    frompath.c_str(), topath.c_str());
                return false;
            }
            LOG(debug, "rename(%s, %s): Cannot rename across filesystems. "
                       "Copying and deleting instead.",
                frompath.c_str(), topath.c_str());
            copy(frompath, topath, createTargetDirectoryIfMissing);
            unlink(frompath);
            return true;
        }
        asciistream ost;
        ost << "rename(" << frompath << ", " << topath
            << (copyDeleteBetweenFilesystems ? ", revert to copy" : "")
            << (createTargetDirectoryIfMissing ? ", create missing target" : "")
            << "): Failed, errno(" << errno << "): " << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno),
                          VESPA_STRLOC);
    }
    LOG(debug, "rename(%s, %s): Renamed.", frompath.c_str(), topath.c_str());
    return true;
}

namespace {

    uint32_t bufferSize = 1_Mi;
    uint32_t diskAlignmentSize = 4_Ki;

}

void
copy(const string & frompath, const string & topath,
     bool createTargetDirectoryIfMissing, bool useDirectIO)
{
        // Get aligned buffer, so it works with direct IO
    LOG(spam, "copy(%s, %s): Copying file%s.",
        frompath.c_str(), topath.c_str(),
        createTargetDirectoryIfMissing
            ? " recursively creating target directory if missing" : "");
    MallocAutoPtr buffer(getAlignedBuffer(bufferSize));

    File source(frompath);
    File target(topath);
    source.open(File::READONLY | (useDirectIO ? File::DIRECTIO : 0));
    size_t sourceSize = source.getFileSize();
    if (useDirectIO && sourceSize % diskAlignmentSize != 0) {
        LOG(warning, "copy(%s, %s): Cannot use direct IO to write new file, "
                     "as source file has size %zu, which is not "
                     "dividable by the disk alignment size of %u.",
            frompath.c_str(), topath.c_str(), sourceSize, diskAlignmentSize);
        useDirectIO = false;
    }
    target.open(File::CREATE | File::TRUNC | (useDirectIO ? File::DIRECTIO : 0),
                createTargetDirectoryIfMissing);
    off_t offset = 0;
    for (;;) {
        size_t bytesRead = source.read(buffer.get(), bufferSize, offset);
        target.write(buffer.get(), bytesRead, offset);
        if (bytesRead < bufferSize) break;
        offset += bytesRead;
    }
    LOG(debug, "copy(%s, %s): Completed.", frompath.c_str(), topath.c_str());
}

DirectoryList
listDirectory(const string & path)
{
    DIR* dir = ::opendir(path.c_str());
    struct dirent* entry;
    DirectoryList result;
    if (dir) while ((entry = readdir(dir))) {
        string name(reinterpret_cast<const char*>(&entry->d_name));
        assert(!name.empty());
        if (name[0] == '.' && (name.size() == 1
                               || (name.size() == 2 && name[1] == '.')))
        {
            continue; // Ignore '.' and '..' files
        }
        result.push_back(name);
    } else {
        throw IoException("Failed to list directory '" + path + "'",
                          IoException::getErrorType(errno), VESPA_STRLOC);
    }
    ::closedir(dir);
    return result;
}

MallocAutoPtr
getAlignedBuffer(size_t size)
{
    void *ptr;
    int result = posix_memalign(&ptr, diskAlignmentSize, size);
    assert(result == 0);
    (void)result;
    return MallocAutoPtr(ptr);
}

string dirname(stringref name)
{
    size_t found = name.rfind('/');
    if (found == string::npos) {
        return string(".");
    } else if (found == 0) {
        return string("/");
    } else {
        return name.substr(0, found);
    }
}

namespace {

void addStat(asciistream &os, const string & name)
{
    struct ::stat filestat;
    memset(&filestat, '\0', sizeof(filestat));
    int statres = ::stat(name.c_str(), &filestat);
    int err = 0;
    if (statres != 0) {
        err = errno;
    }
    os << "[name=" << name;
    if (statres != 0) {
        std::error_code ec(err, std::system_category());
        os << " errno=" << err << "(\"" << ec.message() << "\")";
    } else {
        os << " mode=" << oct << filestat.st_mode << dec <<
            " uid=" << filestat.st_uid << " gid=" << filestat.st_gid <<
            " size=" << filestat.st_size << " mtime=" << filestat.st_mtime;
    }
    os << "]";
}

}

string
getOpenErrorString(const int osError, stringref filename)
{
    asciistream os;
    string dirName(dirname(filename));
    os << "error="  << osError << "(\"" <<
        getErrorString(osError) << "\") fileStat";
    addStat(os, filename);
    os << " dirStat";
    addStat(os, dirName);
    return os.str();
}

bool
isDirectory(const string & path) {
    FileInfo::UP info(stat(path));
    return (info.get() && info->_directory);
}

} // vespalib
