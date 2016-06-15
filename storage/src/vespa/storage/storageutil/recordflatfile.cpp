// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/storage/storageutil/recordflatfile.h>

#include <sys/stat.h>
#include <sys/types.h>

using document::IoException;
using namespace std;
using namespace storage;

namespace {

    string getLastError(FastOS_File&) {
          // Potential memory leak if string's operator new throws bad_alloc
          // or other exception.
        char *ptr = FastOS_File::GetLastErrorString();
        string error(ptr);
        free(ptr);
        return error;
    }

}

ExceptionThrowingFile::
ExceptionThrowingFile(const string& filename)
    : _file(filename.c_str())
{
}

void ExceptionThrowingFile::openReadOnly()
throw (IoException)
{
    if (!_file.OpenReadOnly()) {
        throw IoException(
            "FastOS_File.OpenReadOnly reported: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

void ExceptionThrowingFile::openWriteOnly()
throw (IoException)
{
    if (!_file.OpenWriteOnly()) {
        throw IoException(
            "FastOS_File.OpenWriteOnly reported: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

void ExceptionThrowingFile::openReadWrite()
throw (IoException)
{
    if (!_file.OpenReadWrite()) {
        throw IoException(
            "FastOS_File.OpenReadWrite reported: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

void ExceptionThrowingFile::read(void* buffer, unsigned int length)
throw (IoException)
{
      // Can't do arithmetics on void*, so casting first.
    char* cbuffer = static_cast<char*>(buffer);
    unsigned int totalRead = 0;
    while (totalRead < length) {
        unsigned int readSize = length - totalRead;
        ssize_t count = _file.Read(cbuffer + totalRead, readSize);
        if (count == -1) {
            throw IoException(
                "FastOS_File.Read reported: "+getLastError(_file),
                VESPA_STRLOC);
        } else if (count == 0) {
            throw IoException("FastOS_File.Read returned 0",
                              VESPA_STRLOC);
        }
        totalRead += count;
    }
}

void ExceptionThrowingFile::
write(const void* buffer, unsigned int length)
throw (IoException)
{
    if (!_file.CheckedWrite(buffer, length)) {
        throw IoException(
            "Call to FastOS_File.CheckedWrite() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

void ExceptionThrowingFile::setPosition(int64_t position)
throw (IoException)
{
    if (!_file.SetPosition(position)) {
        throw IoException(
            "Call to FastOS_File.SetPosition() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

int64_t ExceptionThrowingFile::getPosition()
throw (IoException)
{
    int64_t position = _file.GetPosition();
    if (position == -1) {
        throw IoException(
            "Call to FastOS_File.GetPosition() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
    assert(position >= 0);
    return position;
}

int64_t ExceptionThrowingFile::getSize()
throw (IoException)
{
    int64_t size = _file.GetSize();
    if (size == -1) {
        throw IoException(
            "Call to FastOS_File.GetSize() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
    assert(size >= 0);
    return size;
}

void ExceptionThrowingFile::setSize(int64_t size)
throw (IoException)
{
    if (!_file.SetSize(size)) {
        throw IoException(
            "Call to FastOS_File.SetSize() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

void ExceptionThrowingFile::remove()
throw (IoException)
{
    if (!_file.Delete()) {
        throw IoException(
            "Call to FastOS_File.Remove() failed: "+getLastError(_file),
            VESPA_STRLOC);
    }
}

bool ExceptionThrowingFile::exists()
throw (IoException)
{
    struct stat fileinfo;
    return (stat(_file.GetFileName(), &fileinfo) == 0);
}
