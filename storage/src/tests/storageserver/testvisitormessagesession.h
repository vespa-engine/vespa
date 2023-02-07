// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/visiting/visitormessagesession.h>
#include <vespa/storage/visiting/visitorthread.h>
#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/storage/storageserver/priorityconverter.h>
#include <vespa/config/subscription/configuri.h>

#include <atomic>
#include <deque>

namespace storage {

class TestVisitorMessageSession : public VisitorMessageSession
{
private:
    std::mutex _waitMonitor;
    std::condition_variable _waitCond;
    mbus::Error _autoReplyError;
    bool _autoReply;

public:
    using UP = std::unique_ptr<TestVisitorMessageSession>;

    VisitorThread& thread;
    Visitor& visitor;
    std::atomic<uint32_t> pendingCount;

    ~TestVisitorMessageSession() override;

    std::deque<std::unique_ptr<documentapi::DocumentMessage> > sentMessages;

    TestVisitorMessageSession(VisitorThread& t, Visitor& v, const mbus::Error& autoReplyError, bool autoReply);

    void reply(mbus::Reply::UP rep);
    uint32_t pending() override { return pendingCount; }
    mbus::Result send(std::unique_ptr<documentapi::DocumentMessage> message) override;
    void waitForMessages(unsigned int msgCount);
    std::mutex & getMonitor() { return _waitMonitor; }
};

struct TestVisitorMessageSessionFactory : public VisitorMessageSessionFactory
{
    std::mutex _accessLock;
    std::vector<TestVisitorMessageSession*> _visitorSessions;
    mbus::Error _autoReplyError;
    bool _createAutoReplyVisitorSessions;
    PriorityConverter _priConverter;

    TestVisitorMessageSessionFactory(vespalib::stringref configId = "")
        : _createAutoReplyVisitorSessions(false),
          _priConverter(config::ConfigUri(configId)) {}

    VisitorMessageSession::UP createSession(Visitor& v, VisitorThread& vt) override {
        std::lock_guard lock(_accessLock);
        auto session = std::make_unique<TestVisitorMessageSession>(vt, v, _autoReplyError, _createAutoReplyVisitorSessions);
        _visitorSessions.push_back(session.get());
        return session;
    }

    documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const override {
        return _priConverter.toDocumentPriority(storagePriority);
    }

};

} // storage
