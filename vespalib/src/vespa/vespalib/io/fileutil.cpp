// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileutil.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <filesystem>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.io.fileutil");

namespace fs = std::filesystem;

namespace vespalib {

namespace {

FileInfo::UP
processStat(struct stat& filestats, bool result, stringref path) {
    FileInfo::UP resval;
    if (result) {
        resval = std::make_unique<FileInfo>();
        resval->_plainfile = S_ISREG(filestats.st_mode);
        resval->_directory = S_ISDIR(filestats.st_mode);
        resval->_size = filestats.st_size;
    } else if (errno != ENOENT) {
        asciistream ost;
        ost << "An IO error occured while statting '" << path << "'. "
            << "errno(" << errno << "): " << getErrorString(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    LOG(debug, "stat(%s): Existed? %s, Plain file? %s, Directory? %s, Size: %" PRIu64,
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

File::File(stringref filename)
    : _fd(-1),
      _filename(filename)
{ }

File::~File()
{
    if (_fd != -1) close();
}

namespace {
int openAndCreateDirsIfMissing(const string & filename, int flags, bool createDirsIfMissing)
{
    int fd = ::open(filename.c_str(), flags, 0644);
    if (fd < 0 && errno == ENOENT && ((flags & O_CREAT) != 0)
        && createDirsIfMissing)
    {
        auto pos = filename.rfind('/');
        if (pos != string::npos) {
            string path(filename.substr(0, pos));
            fs::create_directories(fs::path(path));
            LOG(spam, "open(%s, %d): Retrying open after creating parent directories.", filename.c_str(), flags);
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
            throw IllegalArgumentException("Cannot use READONLY and CREATE options at the same time", VESPA_STRLOC);
        }
        if ((flags & File::TRUNC) != 0) {
            throw IllegalArgumentException("Cannot use READONLY and TRUNC options at the same time", VESPA_STRLOC);
        }
        if (autoCreateDirectories) {
            throw IllegalArgumentException("No point in auto-creating directories on read only access", VESPA_STRLOC);
        }
    }
    int openflags = ((flags & File::READONLY) != 0 ? O_RDONLY : O_RDWR)
                  | ((flags & File::CREATE)  != 0 ? O_CREAT : 0)
                  | ((flags & File::TRUNC) != 0 ? O_TRUNC: 0);
    int fd = openAndCreateDirsIfMissing(_filename, openflags, autoCreateDirectories);
    if (fd < 0) {
        asciistream ost;
        ost << "open(" << _filename << ", 0x" << hex << flags << dec
            << "): Failed, errno(" << errno << "): " << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    if (_fd != -1) close();
    _fd = fd;
    LOG(debug, "open(%s, %d). File opened with file descriptor %d.", _filename.c_str(), flags, fd);
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
        result = processStat(filestats, ::stat(_filename.c_str(), &filestats) == 0, _filename);
            // If the file does not exist yet, act like it does. It will
            // probably be created when opened.
        if ( ! result) {
            result = std::make_unique<FileInfo>();
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
        ost << "resize(" << _filename << ", " << size << "): Failed, errno(" << errno << "): " << safeStrerror(errno);
        throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
    }
    LOG(debug, "resize(%s): Resized to %" PRIu64 " bytes.", _filename.c_str(), size);
}

off_t
File::write(const void *buf, size_t bufsize, off_t offset)
{
    size_t left = bufsize;
    LOG(debug, "write(%s): Writing %zu bytes at offset %" PRIu64 ".", _filename.c_str(), bufsize, offset);

    while (left > 0) {
        ssize_t written = ::pwrite(_fd, buf, left, offset);
        if (written > 0) {
            LOG(spam, "write(%s): Wrote %zd bytes at offset %" PRIu64 ".", _filename.c_str(), written, offset);
            left -= written;
            buf = ((const char*) buf) + written;
            offset += written;
        } else if (written == 0) {
            LOG(spam, "write(%s): Wrote %zd bytes at offset %" PRIu64 ".", _filename.c_str(), written, offset);
            assert(false); // Can this happen?
        } else if (errno != EINTR && errno != EAGAIN) {
            asciistream ost;
            ost << "write(" << _fd << ", " << buf << ", " << left << ", " << offset
                << "), Failed, errno(" << errno << "): " << safeStrerror(errno);
            throw IoException(ost.str(), IoException::getErrorType(errno), VESPA_STRLOC);
        }
    }
    return bufsize;
}

size_t
File::read(void *buf, size_t bufsize, off_t offset) const
{
    size_t remaining = bufsize;
    LOG(debug, "read(%s): Reading %zu bytes from offset %" PRIu64 ".", _filename.c_str(), bufsize, offset);

    while (remaining > 0) {
        ssize_t bytesread = ::pread(_fd, buf, remaining, offset);
        if (bytesread > 0) {
            LOG(spam, "read(%s): Read %zd bytes from offset %" PRIu64 ".", _filename.c_str(), bytesread, offset);
            remaining -= bytesread;
            buf = ((char*) buf) + bytesread;
            offset += bytesread;
        } else if (bytesread == 0) { // EOF
            LOG(spam, "read(%s): Found EOF. Zero bytes read from offset %" PRIu64 ".", _filename.c_str(), offset);
            break;
        } else if (errno != EINTR && errno != EAGAIN) {
            asciistream ost;
            ost << "read(" << _fd << ", " << buf << ", " << remaining << ", "
                << offset << "): Failed, errno(" << errno << "): " << safeStrerror(errno);
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
    return fs::remove(fs::path(_filename));
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
    os << "error="  << osError << "(\"" << getErrorString(osError) << "\") fileStat";
    addStat(os, filename);
    os << " dirStat";
    addStat(os, dirName);
    return os.str();
}

} // vespalib
