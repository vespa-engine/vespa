// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedinputstream.h"
#include <cstring>
#include <cstdint>

Fast_BufferedInputStream::Fast_BufferedInputStream(Fast_InputStream &in, size_t bufferSize)
    : Fast_FilterInputStream(in),
      _buffer(new char[bufferSize]),
      _bufferSize((_buffer != nullptr) ? bufferSize : 0),
      _bufferUsed(0),
      _bufferRead(0),
      _nextWillFail(false)
{
}

Fast_BufferedInputStream::~Fast_BufferedInputStream()
{
    delete [] _buffer;
}

ssize_t
Fast_BufferedInputStream::Available()
{
    return _in->Available() + _bufferUsed - _bufferRead;
}

bool
Fast_BufferedInputStream::Close()
{
    return _in->Close();
}

ssize_t
Fast_BufferedInputStream::Skip(size_t skipNBytes)
{
    ssize_t numBytesSkipped = 0;

    if (_nextWillFail) {
        _nextWillFail = false;
        return -1;
    }

    if (skipNBytes > _bufferUsed - _bufferRead) {
        // First, skip all bytes in buffer
        numBytesSkipped = _bufferUsed - _bufferRead;
        _bufferUsed = _bufferRead = 0;

        // Skip rest of bytes in slave stream
        ssize_t slaveSkipped = _in->Skip(skipNBytes - numBytesSkipped);
        if (slaveSkipped < 0) {
            if (numBytesSkipped > 0) {
                _nextWillFail = true;
            } else {
                numBytesSkipped = slaveSkipped;
            }
        } else {
            numBytesSkipped += slaveSkipped;
        }

    } else {
        // Skip all skipNBytes in buffer
        _bufferRead += skipNBytes;
        if (_bufferRead == _bufferUsed) {
            _bufferUsed = _bufferRead = 0;
        }
        numBytesSkipped = skipNBytes;
    }

    return numBytesSkipped;
}

ssize_t
Fast_BufferedInputStream::Read(void *targetBuffer, size_t length)
{

    // This function will under no circumstance read more than once from
    // its slave stream, in order to prevent blocking on input.

    if (_nextWillFail) {
        _nextWillFail = false;
        return -1;
    }

    ssize_t numBytesRead = 0;
    char* to = static_cast<char*>(targetBuffer);
    size_t bufferRemain = _bufferUsed - _bufferRead;

    if (length <= bufferRemain) {
        memcpy(to, &_buffer[_bufferRead], length);
        numBytesRead += length;
        _bufferRead  += length;
        if (_bufferRead == _bufferUsed) {
            _bufferRead = _bufferUsed = 0;
        }
    } else {
        // Use the data currently in the buffer, then read from slave stream.

        if (bufferRemain > 0) {
            memcpy(to, &_buffer[_bufferRead], bufferRemain);
            numBytesRead += bufferRemain;
            length       -= bufferRemain;
            to           += bufferRemain;
        }
        _bufferUsed = 0;
        _bufferRead = 0;
        ssize_t slaveRead;

        // If remaining data to be read can fit in the buffer, put it
        // there, otherwise read directly to receiver and empty the buffer.
        if (length < _bufferSize) {
            slaveRead = Fast_FilterInputStream::Read(_buffer, _bufferSize);
        } else {
            slaveRead = Fast_FilterInputStream::Read(to, length);
        }

        if (slaveRead > 0) {
            if (length < _bufferSize) {
                // We read to buffer, so copy from buffer to receiver.
                if (length < static_cast<size_t>(slaveRead)) {
                    memcpy(to, _buffer, length);
                    numBytesRead += length;
                    _bufferUsed = slaveRead;
                    _bufferRead = length;
                } else {
                    memcpy(to, _buffer, slaveRead);
                    numBytesRead += slaveRead;
                }
            } else {
                // We read directly to receiver, no need to copy.
                numBytesRead += slaveRead;
            }
        } else if (slaveRead == 0) {
            // Do nothing
        } else {
            // slaveRead < 0, so an error occurred while reading from the
            // slave.  If there was data in the buffer, report success and
            // fail on next operation instead.
            if (numBytesRead > 0) {
                _nextWillFail = true;
            } else {
                numBytesRead = slaveRead;
            }
        }

    } // End of slave read

    return numBytesRead;
}

ssize_t
Fast_BufferedInputStream::ReadBufferFullUntil(void *targetBuffer, size_t maxlength, char stopChar)
{

    if (maxlength > _bufferSize)
        maxlength = _bufferSize;

    // This function will under no circumstance read more than once from
    // its slave stream, in order to prevent blocking on input.

    if (_nextWillFail) {
        _nextWillFail = false;
        return -1;
    }

    uint32_t offset = 0;
    ssize_t numBytesRead = 0;
    char* to = static_cast<char*>(targetBuffer);
    size_t bufferRemain = _bufferUsed - _bufferRead;

    // Check if we should scan for stopChar in buffer
    if (bufferRemain > 0) {
        for (offset = _bufferRead; offset < _bufferUsed; offset++) {
            if(_buffer[offset] == stopChar) {
                break;
            }
        }
        // Found character in buffer
        if (offset < _bufferUsed) {
            maxlength = offset - _bufferRead + 1;
        }
    }

    if (maxlength <= bufferRemain) {
        memcpy(to, &_buffer[_bufferRead], maxlength);
        numBytesRead += maxlength;
        _bufferRead  += maxlength;
        if (_bufferRead == _bufferUsed) {
            _bufferRead = _bufferUsed = 0;
        }
    } else {
        // Use the data currently in the buffer, then read from slave stream.

        if (bufferRemain > 0) {
            memcpy(to, &_buffer[_bufferRead], bufferRemain);
            numBytesRead += bufferRemain;
            maxlength    -= bufferRemain;
            to           += bufferRemain;
        }
        _bufferUsed = 0;
        _bufferRead = 0;
        ssize_t slaveRead;

        slaveRead = Fast_FilterInputStream::Read(_buffer, _bufferSize);
        if (slaveRead > 0) {
            for (offset = 0; offset < static_cast<uint32_t>(slaveRead); offset++) {
                if(_buffer[offset] == stopChar) {
                    break;
                }
            }

            if (offset >= maxlength) {
                // Discard data if character was not present
                numBytesRead = -1;
            } else {
                // Found character in buffer
                if (offset < static_cast<uint32_t>(slaveRead)) {
                    maxlength = offset + 1;
                }
                // We read to buffer, so copy from buffer to receiver.
                if (maxlength < static_cast<size_t>(slaveRead)) {
                    memcpy(to, _buffer, maxlength);
                    numBytesRead += maxlength;
                    _bufferUsed = slaveRead;
                    _bufferRead = maxlength;
                } else {
                    memcpy(to, _buffer, slaveRead);
                    numBytesRead += slaveRead;
                }
            }
        } else if (slaveRead == 0) {
            // Do nothing
        } else {
            // slaveRead < 0, so an error occurred while reading from the
            // slave.  If there was data in the buffer, report success and
            // fail on next operation instead.
            if (numBytesRead > 0) {
                _nextWillFail = true;
            } else {
                numBytesRead = slaveRead;
            }
        }
    } // End of slave read

    return numBytesRead;

}
