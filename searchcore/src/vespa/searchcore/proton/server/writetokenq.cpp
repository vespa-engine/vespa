
#include "writetokenq.h"
#include <cassert>

namespace proton {

WriteTokenQ::WriteTokenQ(bool allowMultiThreading)
    : _allowMultiThreading(allowMultiThreading),
      _orderLock(),
      _released(),
      _processOrder()
{}

WriteTokenQ::~WriteTokenQ() { }

WriteTokenQ::WriteToken::WriteToken(WriteTokenQ * tokenQ, SerialNum serialNum)
    : _tokenQ(tokenQ), _serialNum(serialNum)
{
    if (_tokenQ) {
        _tokenQ->waitForSerialNum(_serialNum);
    }
}

void WriteTokenQ::WriteToken::cleanup() {
    if (_tokenQ) {
        _tokenQ->releaseSerialNum(_serialNum);
        _tokenQ = nullptr;
    }
}

WriteTokenQ::WriteTokenProducer::WriteTokenProducer(WriteTokenQ * tokenQ, SerialNum serialNum)
    : _tokenQ(tokenQ), _serialNum(serialNum)
{
    if (_tokenQ) {
        _tokenQ->addSerialNumToProcess(_serialNum);
    }
}

using Guard = std::unique_lock<std::mutex>;
void WriteTokenQ::addSerialNumToProcess(SerialNum serial) {
    Guard guard(_orderLock);
    _processOrder.push_back(serial);
}

void WriteTokenQ::waitForSerialNum(SerialNum serial) {
    Guard guard(_orderLock);
    while (_processOrder.front() != serial) {
        _released.wait(guard);
    }
}

void WriteTokenQ::releaseSerialNum(SerialNum serial) {
    Guard guard(_orderLock);
    assert(_processOrder.front() == serial);
    _processOrder.erase(_processOrder.begin());
    _released.notify_all();
}

}
