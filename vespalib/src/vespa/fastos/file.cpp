// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_FileInterface methods.
 *
 * @author  Div, Oivind H. Danielsen
 */

#include "file.h"
#include <sstream>
#include <cstring>
#include <fcntl.h>
#include <cstdlib>
#include <cassert>

DirectIOException::DirectIOException(const char * fileName, const void * buffer, size_t length, int64_t offset) :
    std::exception(),
    _what(),
    _fileName(fileName),
    _buffer(buffer),
    _length(length),
    _offset(offset)
{
    std::ostringstream os;
    os << "DirectIO failed for file '" << fileName << "' buffer=0x" << std::hex << reinterpret_cast<size_t>(buffer);
    os << " length=0x" << length << " offset=0x" << offset;
    _what = os.str();
}

DirectIOException::~DirectIOException() {}

#ifdef __linux__
int FastOS_FileInterface::_defaultFAdviseOptions = POSIX_FADV_NORMAL;
#else
int FastOS_FileInterface::_defaultFAdviseOptions = 0;
#endif

static const size_t MAX_CHUNK_SIZE = 0x4000000; // 64 MB

FastOS_FileInterface::FastOS_FileInterface(const char *filename)
    : _fAdviseOptions(_defaultFAdviseOptions),
      _chunkSize(MAX_CHUNK_SIZE),
      _filename(),
      _openFlags(0),
      _directIOEnabled(false),
      _syncWritesEnabled(false)
{
    if (filename != nullptr)
        SetFileName(filename);
}


FastOS_FileInterface::~FastOS_FileInterface() = default;

void
FastOS_FileInterface::ReadBuf(void *buffer, size_t length)
{
    ssize_t readResult = Read(buffer, length);

    if ((readResult == -1) || (static_cast<size_t>(readResult) != length)) {
        std::string errorString = readResult != -1 ?
                                  std::string("short read") :
                                  FastOS_FileInterface::getLastErrorString();
        std::ostringstream os;
        os << "Fatal: Reading " << length << " bytes from '" << GetFileName() << "' failed: " << errorString;
        throw std::runtime_error(os.str());
    }
}

void
FastOS_FileInterface::WriteBuf(const void *buffer, size_t length)
{
    WriteBufInternal(buffer, length);
}

void
FastOS_FileInterface::WriteBufInternal(const void *buffer, size_t length)
{
    ssize_t writeResult = Write2(buffer, length);
    if (length - writeResult != 0) {
        std::string errorString = writeResult != -1 ?
                                  std::string("short write") :
                                  FastOS_FileInterface::getLastErrorString();
        std::ostringstream os;
        os << "Fatal: Writing " << length << " bytes to '" << GetFileName() << "' failed (wrote " << writeResult << "): " << errorString;
        throw std::runtime_error(os.str());
    }
}

bool
FastOS_FileInterface::CheckedWrite(const void *buffer, size_t len)
{
    ssize_t writeResult = Write2(buffer, len);
    if (writeResult < 0) {
        std::string errorString = FastOS_FileInterface::getLastErrorString();
        fprintf(stderr, "Writing %lu bytes to '%s' failed: %s\n",
                static_cast<unsigned long>(len),
                GetFileName(),
                errorString.c_str());
        return false;
    }
    if (writeResult != (ssize_t)len) {
        fprintf(stderr, "Short write, tried to write %lu bytes to '%s', only wrote %lu bytes\n",
                static_cast<unsigned long>(len),
                GetFileName(),
                static_cast<unsigned long>(writeResult));
        return false;
    }
    return true;
}


void
FastOS_FileInterface::ReadBuf(void *buffer, size_t length, int64_t readOffset)
{
    if (!SetPosition(readOffset)) {
        std::string errorString = FastOS_FileInterface::getLastErrorString();
        std::ostringstream os;
        os << "Fatal: Setting fileoffset to " << readOffset << " in '" << GetFileName() << "' : " << errorString;
        throw std::runtime_error(os.str());
    }
    ReadBuf(buffer, length);
}


void
FastOS_FileInterface::EnableDirectIO()
{
    // Only subclasses with support for DirectIO do something here.
}


void
FastOS_FileInterface::EnableSyncWrites()
{
    if (!IsOpened())
        _syncWritesEnabled = true;
}


bool
FastOS_FileInterface::
GetDirectIORestrictions(size_t &memoryAlignment,
                        size_t &transferGranularity,
                        size_t &transferMaximum)
{
    memoryAlignment = 1;
    transferGranularity = 1;
    transferMaximum = 0x7FFFFFFF;
    return false;
}

bool
FastOS_FileInterface::DirectIOPadding(int64_t offset,
                                      size_t buflen,
                                      size_t &padBefore,
                                      size_t &padAfter)
{
    (void)offset;
    (void)buflen;
    padBefore = 0;
    padAfter = 0;
    return false;
}


void *
FastOS_FileInterface::allocateGenericDirectIOBuffer(size_t byteSize, void *&realPtr)
{
    realPtr = malloc(byteSize);    // Default - use malloc allignment
    return realPtr;
}

size_t
FastOS_FileInterface::getMaxDirectIOMemAlign()
{
    return 1u;
}

void *
FastOS_FileInterface::AllocateDirectIOBuffer(size_t byteSize, void *&realPtr)
{
    return allocateGenericDirectIOBuffer(byteSize, realPtr);
}

void
FastOS_FileInterface::enableMemoryMap(int mmapFlags)
{
    // Only subclases with support for memory mapping do something here.
    (void) mmapFlags;
}


void *
FastOS_FileInterface::MemoryMapPtr(int64_t position) const
{
    // Only subclases with support for memory mapping do something here.
    (void) position;
    return nullptr;
}


bool
FastOS_FileInterface::IsMemoryMapped() const
{
    // Only subclases with support for memory mapping do something here.
    return false;
}

void
FastOS_FileInterface::SetFileName(const char *filename)
{
    _filename = filename;
}


const char *
FastOS_FileInterface::GetFileName() const
{
    return _filename.c_str();
}


bool
FastOS_FileInterface::OpenReadWrite(const char *filename)
{
    return Open(FASTOS_FILE_OPEN_READ |
                FASTOS_FILE_OPEN_WRITE, filename);
}


bool
FastOS_FileInterface::OpenExisting(bool abortIfNotExist,
                                   const char *filename)
{
    bool rc = Open(FASTOS_FILE_OPEN_READ |
                   FASTOS_FILE_OPEN_WRITE |
                   FASTOS_FILE_OPEN_EXISTING,
                   filename);

    if (abortIfNotExist && (!rc)) {
        std::string errorString =
            FastOS_FileInterface::getLastErrorString();
        fprintf(stderr,
                "Cannot open %s: %s\n",
                filename,
                errorString.c_str());
        abort();
    }

    return rc;
}


bool
FastOS_FileInterface::OpenReadOnlyExisting(bool abortIfNotExist,
        const char *filename)
{
    bool rc = Open(FASTOS_FILE_OPEN_READ |
                   FASTOS_FILE_OPEN_EXISTING,
                   filename);

    if (abortIfNotExist && (!rc)) {
        std::string errorString =
            FastOS_FileInterface::getLastErrorString();
        fprintf(stderr,
                "Cannot open %s: %s\n",
                filename,
                errorString.c_str());
        abort();
    }

    return rc;
}


bool
FastOS_FileInterface::OpenWriteOnlyTruncate(const char *filename)
{
    // printf("********* OpenWriteOnlyTruncate %s\n", filename);
    return  Open(FASTOS_FILE_OPEN_WRITE |
                 FASTOS_FILE_OPEN_CREATE |
                 FASTOS_FILE_OPEN_TRUNCATE,
                 filename);
}


bool
FastOS_FileInterface::OpenWriteOnlyExisting(bool abortIfNotExist,
        const char *filename)
{
    bool rc = Open(FASTOS_FILE_OPEN_WRITE |
                   FASTOS_FILE_OPEN_EXISTING,
                   filename);

    if (abortIfNotExist && (!rc)) {
        std::string errorString =
            FastOS_FileInterface::getLastErrorString();
        fprintf(stderr,
                "Cannot open %s: %s\n",
                filename,
                errorString.c_str());
        abort();
    }

    return rc;
}

bool
FastOS_FileInterface::OpenReadOnly(const char *filename)
{
    return Open(FASTOS_FILE_OPEN_READ |
                FASTOS_FILE_OPEN_EXISTING,
                filename);
}


bool
FastOS_FileInterface::OpenWriteOnly(const char *filename)
{
    return Open(FASTOS_FILE_OPEN_WRITE, filename);
}

FastOS_File::Error
FastOS_FileInterface::GetLastError()
{
    return FastOS_File::TranslateError(FastOS_File::GetLastOSError());
}


std::string
FastOS_FileInterface::getLastErrorString()
{
    int err = FastOS_File::GetLastOSError();
    return FastOS_File::getErrorString(err);
}

bool FastOS_FileInterface::Rename (const char *newFileName)
{
    bool rc=false;
    if (FastOS_File::Rename(GetFileName(), newFileName)) {
        SetFileName(newFileName);
        rc = true;
    }
    return rc;
}

void FastOS_FileInterface::dropFromCache() const
{
}

FastOS_DirectoryScanInterface::FastOS_DirectoryScanInterface(const char *path)
    : _searchPath(path)
{
}

FastOS_DirectoryScanInterface::~FastOS_DirectoryScanInterface() = default;
