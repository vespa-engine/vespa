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
* HTTP chunked output stream.
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/

#include <vespa/fastos/fastos.h>
#include <string.h>
#include <vespa/fastlib/net/httpchunkedoutputstream.h>

Fast_HTTPChunkedOutputStream::Fast_HTTPChunkedOutputStream(Fast_OutputStream &out,
							   size_t chunkSize)
    : Fast_FilterOutputStream(out),
      _chunkSize(chunkSize),
      _buffer(NULL),
      _bufferUsed(0),
      _writeHasFailed(false)
{
    _buffer = new char[_chunkSize+2]; // Leave room for CRLF at end of chunk
    assert(_buffer != NULL);
}



Fast_HTTPChunkedOutputStream::~Fast_HTTPChunkedOutputStream(void)
{
    delete [] _buffer;
}



bool Fast_HTTPChunkedOutputStream::WriteChunk(void)
{
    // Don't write an empty block, as this will end the entity.
    if (_bufferUsed == 0)
    {
        return true;
    }

    if (_writeHasFailed)
        return false;

    char chunkHeader[100];
    char *from;
    size_t bytesLeft;
    ssize_t bytesWritten;
    bytesLeft = sprintf(chunkHeader, "%x\r\n",
                        static_cast<unsigned int>(_bufferUsed));
    from = chunkHeader;
    while (bytesLeft > 0)
    {
        bytesWritten = Fast_FilterOutputStream::Write(from, bytesLeft);
        if (bytesWritten < 0)
        {
            _writeHasFailed = true;
            return false;
        }
        else
        {
            from += bytesWritten;
            bytesLeft -= bytesWritten;
        }
    }

    _buffer[_bufferUsed++] = '\r';
    _buffer[_bufferUsed++] = '\n';
    bytesLeft = _bufferUsed;
    from = _buffer;
    while (bytesLeft > 0)
    {
        bytesWritten = Fast_FilterOutputStream::Write(from, bytesLeft);
        if (bytesWritten < 0)
        {
            _bufferUsed -= 2;
            _writeHasFailed = true;
            return false;
        }
        else
        {
            from += bytesWritten;
            bytesLeft -= bytesWritten;
        }
    }
    _bufferUsed = 0;
    return true;
}



bool Fast_HTTPChunkedOutputStream::Close(void)
{
    WriteChunk();
    char chunkHeader[] = { '0', '\r', '\n', '\r', '\n' };
    char *from = chunkHeader;
    size_t bytesLeft = sizeof(chunkHeader);
    ssize_t bytesWritten;
    while (bytesLeft > 0)
    {
        bytesWritten = Fast_FilterOutputStream::Write(from, bytesLeft);
        if (bytesWritten < 0)
        {
            break;
        }
        else
        {
            from += bytesWritten;
            bytesLeft -= bytesWritten;
        }
    }

    return (bytesLeft == 0);
}



ssize_t
Fast_HTTPChunkedOutputStream::Write(const void *sourceBuffer, size_t length)
{
    const char *from = static_cast<const char*>(sourceBuffer);
    size_t numBytesWritten = length;
    while (length > 0)
    {
        size_t bufferRemain = _chunkSize - _bufferUsed;
        if (bufferRemain > 0) {
            size_t blockLength = (length < bufferRemain) ? length : bufferRemain;
            memcpy(_buffer + _bufferUsed, from, blockLength);
            _bufferUsed += blockLength;
            from        += blockLength;
            length      -= blockLength;
        }
        if (length > 0)
        {
            if (!WriteChunk())
            {
                return -1;
            }
        }
    }
    return numBytesWritten;
}



void
Fast_HTTPChunkedOutputStream::Flush(void)
{
    WriteChunk();
    Fast_FilterOutputStream::Flush();
}
