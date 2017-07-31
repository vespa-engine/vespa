// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <mutex>
#include <vector>
#include <condition_variable>

namespace proton {

class StoreOnlyFeedView;

class WriteTokenQ {
    using SerialNum = search::SerialNum;
public:
    WriteTokenQ();
    ~WriteTokenQ();
    class WriteToken {
    public:
        WriteToken() : WriteToken(nullptr, -1) { }
        WriteToken(WriteTokenQ * feedView, SerialNum serialNum);

        WriteToken(WriteToken && rhs) noexcept
                : _feedView(rhs._feedView), _serialNum(rhs._serialNum)
        {
            rhs._feedView = nullptr;
        }

        WriteToken(const WriteToken &) = delete;
        WriteToken & operator =(const WriteToken &) = delete;

        ~WriteToken() {
            cleanup();
        }
    private:
        void cleanup();
        WriteTokenQ * _feedView;
        SerialNum _serialNum;
    };
    class WriteTokenProducer {
    public:
        WriteTokenProducer() : WriteTokenProducer(nullptr, -1) { }
        WriteTokenProducer(WriteTokenQ * feedView, SerialNum serialNum);
        WriteTokenProducer(WriteTokenProducer && rhs) noexcept
            : _feedView(rhs._feedView), _serialNum(rhs._serialNum)
        {
            rhs._feedView = nullptr;
        }
        WriteTokenProducer(const WriteTokenProducer &) = delete;
        WriteTokenProducer & operator = (const WriteTokenProducer &) = delete;
        ~WriteTokenProducer() {
            getWriteToken();
        }
        WriteToken getWriteToken() {
            WriteTokenQ * f = _feedView;
            _feedView = nullptr;
            return WriteToken(f, _serialNum);
        }
    private:
        WriteTokenQ  *_feedView;
        SerialNum     _serialNum;
    };
private:
    void addSerialNumToProcess(SerialNum serial);
    void waitForSerialNum(SerialNum serial);
    void releaseSerialNum(SerialNum serial);
    std::mutex              _orderLock;
    std::condition_variable _released;
    std::vector<SerialNum>  _processOrder;
};

}