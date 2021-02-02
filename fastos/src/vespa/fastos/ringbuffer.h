// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

struct FastOS_RingBufferData
{
    union
    {
        unsigned int _messageSize;
        uint8_t _buffer[1];
    };
};


class FastOS_RingBuffer
{
private:
    FastOS_RingBuffer (const FastOS_RingBuffer&);
    FastOS_RingBuffer& operator=(const FastOS_RingBuffer&);

    bool _closed;
    FastOS_RingBufferData *_data;
    int _bufferSize;
    int _dataIndex, _dataSize;

    int GetWriteIndex (int offset)
    {
        return (_dataIndex + _dataSize + offset) % _bufferSize;
    }

    int GetReadIndex (int offset)
    {
        return (_dataIndex + offset) % _bufferSize;
    }

    std::mutex _mutex;

public:
    void Reset ()
    {
        _dataIndex = 0;
        _dataSize = 0;
        _closed = false;
    }

    FastOS_RingBuffer (int bufferSize)
        : _closed(false),
          _data(0),
          _bufferSize(bufferSize),
          _dataIndex(0),
          _dataSize(0),
          _mutex()
    {
        _data = static_cast<FastOS_RingBufferData *>
                (malloc(sizeof(FastOS_RingBufferData) + bufferSize));
        Reset();
    }

    ~FastOS_RingBuffer ()
    {
        free(_data);
    }

    uint8_t *GetWritePtr (int offset=0)
    {
        return &_data->_buffer[GetWriteIndex(offset)];
    }

    uint8_t *GetReadPtr (int offset=0)
    {
        return &_data->_buffer[GetReadIndex(offset)];
    }

    void Consume (int bytes)
    {
        _dataSize -= bytes;
        _dataIndex = (_dataIndex + bytes) % _bufferSize;
    }

    void Produce (int bytes)
    {
        _dataSize += bytes;
    }

    int GetWriteSpace ()
    {
        int spaceLeft = _bufferSize - _dataSize;
        int continuousBufferLeft = _bufferSize - GetWriteIndex(0);

        if(continuousBufferLeft > spaceLeft)
            continuousBufferLeft = spaceLeft;

        return continuousBufferLeft;
    }

    int GetReadSpace ()
    {
        int dataLeft = _dataSize;
        int continuousBufferLeft = _bufferSize - _dataIndex;
        if(continuousBufferLeft > dataLeft)
            continuousBufferLeft = dataLeft;
        return continuousBufferLeft;
    }

    void Close ()
    {
        _closed = true;
    }

    bool GetCloseFlag ()
    {
        return _closed;
    }

    std::unique_lock<std::mutex> getGuard() { return std::unique_lock<std::mutex>(_mutex); }
};

