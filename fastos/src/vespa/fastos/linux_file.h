// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
******************************************************************************
* @author  Oivind H. Danielsen
* @date    Creation date: 2000-09-21
* @file
* Class definition for FastOS_Linux_File.
*****************************************************************************/

#pragma once

#include "unix_file.h"

/**
 * This is the Linux implementation of @ref FastOS_File. Most
 * methods are inherited from @ref FastOS_UNIX_File.
 */
class FastOS_Linux_File : public FastOS_UNIX_File
{
public:
    using FastOS_UNIX_File::ReadBuf;
protected:
    int64_t _cachedSize;
    int64_t _filePointer;   // Only maintained/used in directio mode

public:
    FastOS_Linux_File (const char *filename = nullptr);
    ~FastOS_Linux_File ();
    bool GetDirectIORestrictions(size_t &memoryAlignment, size_t &transferGranularity, size_t &transferMaximum) override;
    bool DirectIOPadding(int64_t offset, size_t length, size_t &padBefore, size_t &padAfter) override;
    void EnableDirectIO() override;
    bool SetPosition(int64_t desiredPosition) override;
    int64_t GetPosition() override;
    bool SetSize(int64_t newSize) override;
    void ReadBuf(void *buffer, size_t length, int64_t readOffset) override;
    void *AllocateDirectIOBuffer(size_t byteSize, void *&realPtr) override;


    [[nodiscard]] ssize_t Read(void *buffer, size_t len) override;
    [[nodiscard]] ssize_t Write2(const void *buffer, size_t len) override;
    bool Open(unsigned int openFlags, const char *filename) override;

    static size_t getMaxDirectIOMemAlign();
    static int count_open_files();
private:
    ssize_t internalWrite2(const void *buffer, size_t len);
    ssize_t readUnalignedEnd(void *buffer, size_t length, int64_t readOffset);
    ssize_t writeUnalignedEnd(const void *buffer, size_t length, int64_t readOffset);
    ssize_t ReadBufInternal(void *buffer, size_t length, int64_t readOffset);
    ssize_t readInternal(int fh, void *buffer, size_t length, int64_t readOffset);
    ssize_t readInternal(int fh, void *buffer, size_t length);
    ssize_t writeInternal(int fh, const void *buffer, size_t length, int64_t writeOffset);
    ssize_t writeInternal(int fh, const void *buffer, size_t length);

    static const size_t _directIOFileAlign;
    static const size_t _directIOMemAlign;
};
