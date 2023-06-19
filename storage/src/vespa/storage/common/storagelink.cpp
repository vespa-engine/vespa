// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagelink.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/vespalib/util/backtrace.h>
#include <sstream>
#include <cassert>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".application.link");

using namespace storage::api;

namespace storage {

StorageLink::~StorageLink() {
    LOG(debug, "Destructing link %s.", toString().c_str());
}

void StorageLink::push_back(StorageLink::UP link)
{
    if (getState() != CREATED) {
        LOG(error, "Attempted to alter chain by adding link %s after link %s while state is %s",
            link->toString().c_str(), toString().c_str(), stateToString(getState()));
        assert(false);
    }
    assert(link);
    if (isBottom()) {
        link->_up = this;
        _down = std::move(link);
    } else {
        _down->push_back(std::move(link));
    }
}

void StorageLink::open()
{
    // First tag all states as opened, as components are allowed to send
    // messages both ways in onOpen call, in case any component send message
    // up, the link receiving them should have their state as opened.
    StorageLink* link = this;
    while (true) {
        if (link->getState() != CREATED) {
            LOG(error, "During open(), link %s should be in CREATED state, not in state %s.",
                toString().c_str(), stateToString(link->getState()));
            assert(false);
        }
        link->_state = OPENED;
        if (!link->_down) {
            break;
        }
        link = link->_down.get();
    }
    // When give all links an onOpen call, bottoms up. Do it bottoms up, as
    // links are more likely to send messages down in their onOpen() call
    // than up. Thus, chances are best that the component is ready to
    // receive messages sent during onOpen().
    while (link != nullptr) {
        link->onOpen();
        link = link->_up;
    }
}

void StorageLink::doneInit()
{
    StorageLink* link = this;
    while (true) {
        link->onDoneInit();
        if (!link->_down) {
            break;
        }
        link = link->_down.get();
    }
}

void StorageLink::close()
{
    _state = CLOSING;
    LOG(debug, "Start close link %s.", toString().c_str());
    onClose();
    if (!isBottom()) {
        _down->close();
    }
    LOG(debug, "End close link %s.", toString().c_str());
}

void StorageLink::closeNextLink() {
    LOG(debug, "Start closeNextLink link %s.", toString().c_str());
    _down.reset();
    LOG(debug, "End closeNextLink link %s.", toString().c_str());
}

void StorageLink::flush()
{
    if (getState() != CLOSING) {
        LOG(error, "During flush(), link %s should be in CLOSING state, not in state %s.",
            toString().c_str(), stateToString(getState()));
        assert(false);
    }
    // First flush down to get all requests out of the system.
    _state = FLUSHINGDOWN;
    LOG(debug, "Flushing link %s on the way down.", toString().c_str());
    onFlush(true);
    LOG(debug, "Flushed link %s on the way down.", toString().c_str());
    if (!isBottom()) {
        _down->flush();
        // Then flush up to get replies out of the system
        LOG(debug, "Flushing link %s on the way back up.", toString().c_str());
        _state = FLUSHINGUP;
        onFlush(false);
        LOG(debug, "Flushed link %s on the way back up.", toString().c_str());
    } else {
        // Then flush up to get replies out of the system
        LOG(debug, "Flushing link %s on the way back up.", toString().c_str());
        _state = FLUSHINGUP;
        onFlush(false);
        LOG(debug, "Flushed link %s on the way back up.", toString().c_str());
    }
    _state = CLOSED;
    LOG(debug, "Link %s is now closed and should do nothing more.", toString().c_str());
}

void StorageLink::sendDown(const StorageMessage::SP& msg)
{
    // Verify acceptable state to send messages down
    switch(getState()) {
        case OPENED:
        case CLOSING:
        case FLUSHINGDOWN:
            break;
        default:
            LOG(error, "Link %s trying to send %s down while in state %s",
                toString().c_str(), msg->toString().c_str(), stateToString(getState()));
            assert(false);
    }
    assert(msg);
    LOG(spam, "Storage Link %s to handle %s", toString().c_str(), msg->toString().c_str());
    if (isBottom()) {
        LOG(spam, "Storage link %s at bottom of chain got message %s.", toString().c_str(), msg->toString().c_str());
        std::ostringstream ost;
        ost << "Unhandled message at bottom of chain " << *msg << " (message type "
            << msg->getType().getName() << "). " << vespalib::getStackTrace(0);
        if (!msg->getType().isReply()) {
            LOGBP(warning, "%s", ost.str().c_str());
            auto& cmd = dynamic_cast<StorageCommand&>(*msg);
            std::shared_ptr<StorageReply> reply(cmd.makeReply());

            if (reply) {
                reply->setResult(ReturnCode(ReturnCode::NOT_IMPLEMENTED, msg->getType().getName()));
                sendUp(reply);
            }
        } else {
            ost << " Return code: " << dynamic_cast<const StorageReply&>(*msg).getResult();
            LOGBP(warning, "%s", ost.str().c_str());
        }
    } else if (!_down->onDown(msg)) {
        _down->sendDown(msg);
    } else {
        LOG(spam, "Storage link %s handled message %s.",
            _down->toString().c_str(), msg->toString().c_str());
    }
}

void StorageLink::sendUp(const std::shared_ptr<StorageMessage> & msg)
{
    // Verify acceptable state to send messages up
    switch(getState()) {
        case OPENED:
        case CLOSING:
        case FLUSHINGDOWN:
        case FLUSHINGUP:
            break;
        default:
            LOG(error, "Link %s trying to send %s up while in state %s",
                toString().c_str(), msg->toString(true).c_str(), stateToString(getState()));
            assert(false);
    }
    assert(msg);
    if (isTop()) {
        std::ostringstream ost;
        ost << "Unhandled message at top of chain " << *msg << ".";
        ost << vespalib::getStackTrace(0);
        if (!msg->getType().isReply()) {
            LOGBP(warning, "%s", ost.str().c_str());
            auto& cmd = dynamic_cast<StorageCommand&>(*msg);
            std::shared_ptr<StorageReply> reply(cmd.makeReply());

            if (reply.get()) {
                reply->setResult(ReturnCode(ReturnCode::NOT_IMPLEMENTED, msg->getType().getName()));
                sendDown(reply);
            }
        } else {
            ost << " Return code: " << dynamic_cast<const StorageReply&>(*msg).getResult();
            LOGBP(warning, "%s", ost.str().c_str());
        }
    } else if (!_up->onUp(msg)) {
        _up->sendUp(msg);
    }
}

void StorageLink::printChain(std::ostream& out, std::string indent) const {
    out << indent << "StorageChain(" << size();
    if (!isTop()) out << ", not top";
    out << ")";
    const StorageLink* lastlink = _up;
    for (const StorageLink* link = this; link != nullptr; link = link->_down.get()) {
        out << "\n";
        link->print(out, false, indent + "  ");
        if (link->_up != lastlink) out << ", broken linkage";
        lastlink = link;
    }
}

bool StorageLink::onDown(const std::shared_ptr<StorageMessage> & msg)
{
    return msg->callHandler(*this, msg);
}

bool StorageLink::onUp(const std::shared_ptr<StorageMessage> & msg)
{
    return msg->callHandler(*this, msg);
}

void
StorageLink::print(std::ostream& out, bool, const std::string&) const
{
    out << getName();
}

const char*
StorageLink::stateToString(State state)
{
    switch (state) {
    case CREATED:
        return "CREATED";
    case OPENED:
        return "OPENED";
    case CLOSING:
        return "CLOSING";
    case FLUSHINGDOWN:
        return "FLUSHINGDOWN";
    case FLUSHINGUP:
        return "FLUSHINGUP";
    case CLOSED:
        return "CLOSED";
    default:
        abort();
    }
}

std::ostream&
operator<<(std::ostream& out, StorageLink& link) {
    link.printChain(out);
    return out;
}

Queue::Queue() = default;
Queue::~Queue() = default;

bool
Queue::getNext(std::shared_ptr<api::StorageMessage>& msg, vespalib::duration timeout) {
    std::unique_lock sync(_lock);
    bool first = true;
    while (true) { // Max twice
        if (!_queue.empty()) {
            LOG(spam, "Picking message from queue");
            msg = std::move(_queue.front());
            _queue.pop();
            return true;
        }
        if ((timeout == vespalib::duration::zero()) || !first) {
            return false;
        }
        _cond.wait_for(sync, timeout);
        first = false;
    }

    return false;
}

void
Queue::enqueue(std::shared_ptr<api::StorageMessage> msg) {
    {
        std::lock_guard sync(_lock);
        _queue.emplace(std::move(msg));
    }
    _cond.notify_one();
}

void
Queue::signal() {
    _cond.notify_one();
}

size_t
Queue::size() const {
    std::lock_guard guard(_lock);
    return _queue.size();
}

}
