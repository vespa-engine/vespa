// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <list>
#include <sstream>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <string>
#include <vector>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageapi/message/internal.h>

namespace storage {

class DummyStorageLink : public StorageLink {

    mutable std::mutex _lock; // to protect below containers:
    std::vector<api::StorageMessage::SP> _commands;
    std::vector<api::StorageMessage::SP> _replies;
    std::list<api::StorageMessage::SP> _injected;

    bool _autoReply;
    bool _useDispatch;
    bool _ignore;
    static DummyStorageLink* _last;
    std::mutex _waitMonitor;
    std::condition_variable _waitCond;

public:
    DummyStorageLink();
    ~DummyStorageLink() override;

    bool onDown(const api::StorageMessage::SP&) override;
    bool onUp(const api::StorageMessage::SP&) override;

    void addOnTopOfChain(StorageLink& link) {
        link.addTestLinkOnTop(this);
    }

    void print(std::ostream& ost, bool verbose, const std::string& indent) const override
    {
        (void) verbose;
        ost << indent << "DummyStorageLink("
            << "autoreply = " << (_autoReply ? "on" : "off")
            << ", dispatch = " << (_useDispatch ? "on" : "off")
            << ", " << _commands.size() << " commands"
            << ", " << _replies.size() << " replies";
        if (_injected.size() > 0)
            ost << ", " << _injected.size() << " injected";
        ost << ")";
    }

    void injectReply(api::StorageReply* reply);
    void reset();
    void setAutoreply(bool autoReply) { _autoReply = autoReply; }
    void setIgnore(bool ignore) { _ignore = ignore; }
    // Timeout is given in seconds
    void waitForMessages(unsigned int msgCount = 1, int timeout = -1);
    // Wait for a single message of a given type
    void waitForMessage(const api::MessageType&, int timeout = -1);

    api::StorageMessage::SP getCommand(size_t i) const {
        std::lock_guard guard(_lock);
        api::StorageMessage::SP ret = _commands[i];
        return ret;
    }
    api::StorageMessage::SP getReply(size_t i) const {
        std::lock_guard guard(_lock);
        api::StorageMessage::SP ret = _replies[i];
        return ret;
    }
    size_t getNumCommands() const {
        std::lock_guard guard(_lock);
        return _commands.size();
    }
    size_t getNumReplies() const {
        std::lock_guard guard(_lock);
        return _replies.size();
    }

    const std::vector<api::StorageMessage::SP>& getCommands() const
        { return _commands; }
    const std::vector<api::StorageMessage::SP>& getReplies() const
        { return _replies; }

    std::vector<api::StorageMessage::SP> getCommandsOnce() {
        std::lock_guard lock(_waitMonitor);
        std::vector<api::StorageMessage::SP> retval;
        {
            std::lock_guard guard(_lock);
            retval.swap(_commands);
        }
        return retval;
    }

    std::vector<api::StorageMessage::SP> getRepliesOnce() {
        std::lock_guard lock(_waitMonitor);
        std::vector<api::StorageMessage::SP> retval;
        {
            std::lock_guard guard(_lock);
            retval.swap(_replies);
        }
        return retval;
    }

    api::StorageMessage::SP getAndRemoveMessage(const api::MessageType&);

    static DummyStorageLink* getLast() { return _last; }
private:
    /**
     * Auto-reply with an injected message if one is available and return
     * whether such an injection took place.
     */
    bool handleInjectedReply();
};

}

