// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author Markus Bjartveit Krüger
* @date            Creation date: 2001-10-30
* @version         $Id$
*
* @file
*
* Generic buffered output stream
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#include <vespa/fastos/fastos.h>
#include "bufferedoutputstream.h"




Fast_BufferedOutputStream::Fast_BufferedOutputStream(Fast_OutputStream &out,
                                                     size_t bufferSize)
    : Fast_FilterOutputStream(out),
      _buffer(new char[bufferSize]),
      _bufferSize((_buffer != NULL) ? bufferSize : 0),
      _bufferUsed(0),
      _bufferWritten(0),
      _nextWillFail(false)
{
}



Fast_BufferedOutputStream::~Fast_BufferedOutputStream(void)
{
    delete [] _buffer;
};



bool Fast_BufferedOutputStream::Close(void)
{
    Flush();
    return Fast_FilterOutputStream::Close();
}



void Fast_BufferedOutputStream::Flush(void)
{
    if (_nextWillFail)
    {
        _nextWillFail = false;
        return;
    }
    while (_bufferWritten < _bufferUsed)
    {
        ssize_t slaveWritten;
        slaveWritten = Fast_FilterOutputStream::Write(&_buffer[_bufferWritten], _bufferUsed - _bufferWritten);
        if (slaveWritten >= 0)
        {
            _bufferWritten += slaveWritten;
        }
        else
        {
            break;
        }
    }
    _bufferUsed = 0;
    _bufferWritten = 0;

    Fast_FilterOutputStream::Flush();
}



ssize_t Fast_BufferedOutputStream::Write(const void *sourceBuffer, size_t length)
{

    // This function will under no circumstance write more than once to
    // its slave stream, in order to prevent blocking on output.

    if (_nextWillFail)
    {
        _nextWillFail = false;
        return -1;
    }
    ssize_t numBytesWritten = 0;
    const char* from = static_cast<const char *>(sourceBuffer);
    size_t bufferRemain = _bufferUsed - _bufferWritten;

    if (length <= _bufferSize - _bufferUsed)
    {
        memcpy(&_buffer[_bufferUsed], from, length);
        numBytesWritten += length;
        _bufferUsed  += length;
    }
    else if (length <= _bufferSize - bufferRemain)
    {
        memmove(_buffer, &_buffer[_bufferWritten], bufferRemain);
        memcpy(&_buffer[bufferRemain], from, length);
        _bufferUsed = bufferRemain + length;
        _bufferWritten = 0;
    }
    else
    {
        ssize_t slaveWritten;
        bool writeFromBuffer = bufferRemain > 0;

        if (writeFromBuffer)
        {
            // Fill up buffer before write.
            memcpy(&_buffer[_bufferUsed], from, _bufferSize - _bufferUsed);
            from += _bufferSize - _bufferUsed;
            length -= _bufferSize - _bufferUsed;
            numBytesWritten += _bufferSize - _bufferUsed;

            slaveWritten = Fast_FilterOutputStream::Write(_buffer, _bufferSize);
        }
        else
        {
            slaveWritten = Fast_FilterOutputStream::Write(from, length);
        }

        if (slaveWritten >= 0)
        {
            if (writeFromBuffer)
            {
                // We wrote from buffer, so shuffle remainder of buffer before
                // filling it up.
                memmove(_buffer, &_buffer[slaveWritten], _bufferSize - slaveWritten);
                _bufferUsed = _bufferSize - slaveWritten;
            }
            else
            {
                // Buffer was empty, all data written from sender.
                numBytesWritten += slaveWritten;
                from += slaveWritten;
                length -= slaveWritten;
                _bufferUsed = 0;
            }
            size_t freeBuffer = _bufferSize - _bufferUsed;
            size_t refill = (length < freeBuffer) ? length : freeBuffer;
            memcpy(&_buffer[_bufferUsed], from, refill);
            numBytesWritten += refill;
            _bufferUsed     += refill;
            _bufferWritten = 0;
        }
        else
        {
            // slaveWritten < 0, so an error occurred while writing to the
            // slave.  If there was data in the buffer, report success and
            // fail on next operation instead.
            if (numBytesWritten > 0)
            {
                _nextWillFail = true;
            }
            else
            {
                numBytesWritten = slaveWritten;
            }
        }

    } // End of slave write

    return numBytesWritten;
}
