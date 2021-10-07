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

static const size_t MAX_WRITE_CHUNK_SIZE = 0x4000000; // 64 MB

FastOS_FileInterface::FastOS_FileInterface(const char *filename)
    : _fAdviseOptions(_defaultFAdviseOptions),
      _writeChunkSize(MAX_WRITE_CHUNK_SIZE),
      _filename(nullptr),
      _openFlags(0),
      _directIOEnabled(false),
      _syncWritesEnabled(false)
{
    if (filename != nullptr)
        SetFileName(filename);
}


FastOS_FileInterface::~FastOS_FileInterface()
{
    free(_filename);
}

bool FastOS_FileInterface::InitializeClass ()
{
    return true;
}

bool FastOS_FileInterface::CleanupClass ()
{
    return true;
}

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
FastOS_FileInterface::SetWriteChunkSize(size_t writeChunkSize)
{
    _writeChunkSize = writeChunkSize;
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

bool
FastOS_FileInterface::CopyFile( const char *src, const char *dst )
{
    FastOS_File s, d;
    FastOS_StatInfo statInfo;
    bool success = false;

    if ( src != nullptr &&
        dst != nullptr &&
        strcmp(src, dst) != 0 &&
        FastOS_File::Stat( src, &statInfo )) {

        if ( s.OpenReadOnly( src ) && d.OpenWriteOnlyTruncate( dst ) ) {

            unsigned int bufSize = 1024*1024;
            int64_t bufSizeBound = statInfo._size;
            if (bufSizeBound < 1)
                bufSizeBound = 1;
            if (bufSizeBound < static_cast<int64_t>(bufSize))
                bufSize = static_cast<unsigned int>(bufSizeBound);
            char *tmpBuf = new char[ bufSize ];

            if ( tmpBuf != nullptr ) {
                int64_t copied = 0;
                success = true;
                do {
                    unsigned int readBytes = s.Read( tmpBuf, bufSize );
                    if (readBytes > 0) {

                        if ( !d.CheckedWrite( tmpBuf, readBytes)) {
                            success = false;
                        }
                        copied += readBytes;
                    } else {
                        // Could not read from src.
                        success = false;
                    }
                } while (copied < statInfo._size && success);

                delete [] tmpBuf;
            } // else out of memory ?

            s.Close();
            d.Close();
        } // else Could not open source or destination file.
    } // else Source file does not exist, or input args are invalid.

    return success;
}


bool
FastOS_FileInterface::MoveFile(const char* src, const char* dst)
{
    bool rc = FastOS_File::Rename(src, dst);
    if (!rc) {
        // Try copy and remove.
        if (CopyFile(src, dst)) {
            rc = FastOS_File::Delete(src);
        }
    }
    return rc;
}


void
FastOS_FileInterface::EmptyDirectory( const char *dir,
                                     const char *keepFile /* = nullptr */ )
{
    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(dir, &statInfo))
        return;             // Fail if the directory does not exist
    FastOS_DirectoryScan dirScan( dir );

    while (dirScan.ReadNext()) {
        if (strcmp(dirScan.GetName(), ".") != 0 &&
            strcmp(dirScan.GetName(), "..") != 0 &&
            (keepFile == nullptr || strcmp(dirScan.GetName(), keepFile) != 0))
        {
            std::string name = dir;
            name += GetPathSeparator();
            name += dirScan.GetName();
            if (dirScan.IsDirectory()) {
                EmptyAndRemoveDirectory(name.c_str());
            } else {
                if ( ! FastOS_File::Delete(name.c_str()) ) {
                    std::ostringstream os;
                    os << "Failed deleting file '" << name << "' due to " << getLastErrorString();
                    throw std::runtime_error(os.str().c_str());
                }
            }
        }
    }
}


void
FastOS_FileInterface::EmptyAndRemoveDirectory(const char *dir)
{
    EmptyDirectory(dir);
    FastOS_File::RemoveDirectory(dir);
}

void
FastOS_FileInterface::MakeDirIfNotPresentOrExit(const char *name)
{
    FastOS_StatInfo statInfo;

    if (FastOS_File::Stat(name, &statInfo)) {
        if (statInfo._isDirectory)
            return;

        fprintf(stderr, "%s is not a directory\n", name);
        std::_Exit(1);
    }

    if (statInfo._error != FastOS_StatInfo::FileNotFound) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not stat %s: %s\n", name, ec.message().c_str());
        std::_Exit(1);
    }

    if (!FastOS_File::MakeDirectory(name)) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Could not mkdir(\"%s\", 0775): %s\n", name, ec.message().c_str());
        std::_Exit(1);
    }
}

void
FastOS_FileInterface::SetFileName(const char *filename)
{
    if (_filename != nullptr) {
        free(_filename);
    }

    _filename = strdup(filename);
}


const char *
FastOS_FileInterface::GetFileName() const
{
    return (_filename != nullptr) ? _filename : "";
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
    : _searchPath(strdup(path))
{
}

FastOS_DirectoryScanInterface::~FastOS_DirectoryScanInterface()
{
    free(_searchPath);
}
