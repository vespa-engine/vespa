
#include "writetokenq.h"
#include <cassert>

namespace proton {

WriteTokenQ::WriteTokenQ()
    : _orderLock(),
      _released(),
      _processOrder()
{}

WriteTokenQ::~WriteTokenQ() { }

WriteTokenQ::WriteToken::WriteToken(WriteTokenQ * feedView, SerialNum serialNum)
: _feedView(feedView), _serialNum(serialNum)
{
    if (_feedView) {
        _feedView->waitForSerialNum(_serialNum);
    }
}

void WriteTokenQ::WriteToken::cleanup() {
    if (_feedView) {
        _feedView->releaseSerialNum(_serialNum);
        _feedView = nullptr;
    }
}

WriteTokenQ::WriteTokenProducer::WriteTokenProducer(WriteTokenQ * feedView, SerialNum serialNum)
    : _feedView(feedView), _serialNum(serialNum)
{
    if (_feedView) {
        _feedView->addSerialNumToProcess(_serialNum);
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
