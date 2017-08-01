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
    class WriteToken {
    public:
        WriteToken() : WriteToken(nullptr, -1) { }
        WriteToken(WriteTokenQ * tokenQ, SerialNum serialNum);

        WriteToken(WriteToken && rhs) noexcept
            : _tokenQ(rhs._tokenQ), _serialNum(rhs._serialNum)
        {
            rhs._tokenQ = nullptr;
        }

        WriteToken(const WriteToken &) = delete;
        WriteToken & operator =(const WriteToken &) = delete;

        ~WriteToken() {
            cleanup();
        }
    private:
        void cleanup();
        WriteTokenQ * _tokenQ;
        SerialNum _serialNum;
    };
    class WriteTokenProducer {
    public:
        WriteTokenProducer() : WriteTokenProducer(nullptr, -1) { }
        WriteTokenProducer(WriteTokenProducer && rhs) noexcept
            : _tokenQ(rhs._tokenQ), _serialNum(rhs._serialNum)
        {
            rhs._tokenQ = nullptr;
        }
        WriteTokenProducer(const WriteTokenProducer &) = delete;
        WriteTokenProducer & operator = (const WriteTokenProducer &) = delete;
        ~WriteTokenProducer() {
            getWriteToken();
        }
        WriteToken getWriteToken() {
            WriteTokenQ * tmp = _tokenQ;
            _tokenQ = nullptr;
            return WriteToken(tmp, _serialNum);
        }
        bool isDispatchAllowed() const { return _tokenQ != nullptr; }
    private:
        friend class WriteTokenQ;
        WriteTokenProducer(WriteTokenQ * tokenQ, SerialNum serialNum);
        WriteTokenQ  *_tokenQ;
        SerialNum     _serialNum;
    };

    WriteTokenQ(bool allowMultiThreading);
    ~WriteTokenQ();
    WriteTokenProducer getTokenProducer(SerialNum serialNum) {
        return WriteTokenProducer(_allowMultiThreading ? this : nullptr, serialNum);
    }
private:
    void addSerialNumToProcess(SerialNum serial);
    void waitForSerialNum(SerialNum serial);
    void releaseSerialNum(SerialNum serial);
    const bool              _allowMultiThreading;
    std::mutex              _orderLock;
    std::condition_variable _released;
    std::vector<SerialNum>  _processOrder;
};

}