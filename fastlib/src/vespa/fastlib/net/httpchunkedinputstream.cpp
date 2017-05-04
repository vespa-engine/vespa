// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author Markus Bjartveit Krüger
* @date            Creation date: 2001-11-21
* @version         $Id$
*
* @file
*
* HTTP chunked input stream.
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/

#include <vespa/fastos/fastos.h>
#include <string.h>
#include <vespa/fastlib/net/httpchunkedinputstream.h>

Fast_HTTPChunkedInputStream::Fast_HTTPChunkedInputStream(Fast_InputStream &in)
    : Fast_FilterInputStream(in), _chunkSize(0), _inChunk(false),
      _isClosed(false)
{
}




Fast_HTTPChunkedInputStream::~Fast_HTTPChunkedInputStream(void)
{
}




bool Fast_HTTPChunkedInputStream::ReadChunkHeader(void)
{
    char chunkHeader[100];
    char *pos = chunkHeader;

    // Read chunk size into chunkHeader.
    for (;;)
    {
        if (Fast_FilterInputStream::Read(pos, 1) != 1)
        {
            return false;
        }
        if (*pos == ';' || *pos == '\n')
        {
            break;
        }
        pos++;
        if (static_cast<size_t>(pos - chunkHeader) > sizeof(chunkHeader))
        {
            return false;
        }
    }

    _chunkSize = strtoul(chunkHeader, NULL, 16);

    // Finish reading eventual extensions.
    char c = *pos;
    while (c != '\n')
    {
        if (Fast_FilterInputStream::Read(&c, 1) != 1)
        {
            return false;
        }
    }

    // If this was the last chunk, read optional trailer and final CRLF.
    if (_chunkSize == 0)
    {
        for (;;) {
            if (Fast_FilterInputStream::Read(&c, 1) != 1)
            {
                return false;
            }
            if (c == '\r')
            {
                if (Fast_FilterInputStream::Read(&c, 1) != 1)
                {
                    return false;
                }
            }
            if (c == '\n')
            {
                // Empty line, end of last chunk.
                break;
            }

            // In trailing header; read rest of line.
            do
            {
                if (Fast_FilterInputStream::Read(&c, 1) != 1)
                {
                    return false;
                }
            } while (c != '\n');
        }
        _inChunk = false;
        _isClosed = true;
    }
    else
    {
        _inChunk = true;
    }

    return true;
}




ssize_t Fast_HTTPChunkedInputStream::Available(void)
{
    if (_isClosed || !_inChunk)
    {
        return 0;
    }

    ssize_t slaveAvailable = Fast_FilterInputStream::Available();
    if (slaveAvailable < 0)
    {
        return slaveAvailable;
    }
    else
    {
        return (static_cast<size_t>(slaveAvailable) < _chunkSize)
            ? slaveAvailable : _chunkSize;
    }
}




bool Fast_HTTPChunkedInputStream::Close(void)
{
    _isClosed = true;
    return true;
}




ssize_t Fast_HTTPChunkedInputStream::Read(void *targetBuffer, size_t length)
{
    if (_isClosed)
    {
        return 0;
    }

    if (!_inChunk)
    {
        // Read new header chunk, check if end of chunked entity is reached.
        if (!ReadChunkHeader())
        {
            _isClosed = true;
            return -1;
        }
        else if (_isClosed)
        {
            return 0;
        }
    }

    size_t blockLength = (length < _chunkSize) ? length : _chunkSize;
    ssize_t numBytesRead = _in->Read(targetBuffer, blockLength);
    if (numBytesRead > 0)
    {
        _chunkSize -= numBytesRead;
    }
    else
    {
        _isClosed = true;
        _inChunk = false;
        return (numBytesRead < 0) ? numBytesRead : -1;
    }

    if (_chunkSize == 0)
    {
        // End of chunk reached.  Mark this, and read CRLF following
        // chunk.

        _inChunk = false;

        bool ok;
        char c;
        ok = _in->Read(&c, 1) == 1;
        if (ok && c == '\r')
        {
            ok = _in->Read(&c, 1) == 1;
        }
        ok = ok && c == '\n';
        if (!ok)
        {
            _isClosed = true;
            return -1;
        }
    }

    return numBytesRead;
}




ssize_t Fast_HTTPChunkedInputStream::Skip(size_t skipNBytes)
{
    if (_isClosed)
    {
        return -1;
    }
    else if (!_inChunk)
    {
        return 0;
    }
    else
    {
        return _in->Skip((skipNBytes < _chunkSize) ? skipNBytes : _chunkSize);
    }
}
