// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    vespalib::Monitor _waitMonitor;
    mbus::Error _autoReplyError;
    bool _autoReply;

public:
    typedef std::unique_ptr<TestVisitorMessageSession> UP;

    VisitorThread& thread;
    Visitor& visitor;
    std::atomic<uint32_t> pendingCount;

    ~TestVisitorMessageSession();

    std::deque<std::unique_ptr<documentapi::DocumentMessage> > sentMessages;

    TestVisitorMessageSession(VisitorThread& t,
                              Visitor& v,
                              const mbus::Error& autoReplyError,
                              bool autoReply);

    void reply(mbus::Reply::UP rep);
    uint32_t pending() override { return pendingCount; }
    mbus::Result send(std::unique_ptr<documentapi::DocumentMessage> message) override;
    void waitForMessages(unsigned int msgCount);
    vespalib::Monitor& getMonitor() { return _waitMonitor; }
};

struct TestVisitorMessageSessionFactory : public VisitorMessageSessionFactory
{
    vespalib::Lock _accessLock;
    std::vector<TestVisitorMessageSession*> _visitorSessions;
    mbus::Error _autoReplyError;
    bool _createAutoReplyVisitorSessions;
    PriorityConverter _priConverter;

    TestVisitorMessageSessionFactory(vespalib::stringref configId = "")
        : _createAutoReplyVisitorSessions(false),
          _priConverter(configId) {}

    VisitorMessageSession::UP createSession(Visitor& v, VisitorThread& vt) override {
        vespalib::LockGuard lock(_accessLock);
        TestVisitorMessageSession::UP session(new TestVisitorMessageSession(vt, v, _autoReplyError,
                                                                            _createAutoReplyVisitorSessions));
        _visitorSessions.push_back(session.get());
        return VisitorMessageSession::UP(std::move(session));
    }

    documentapi::Priority::Value toDocumentPriority(uint8_t storagePriority) const override {
        return _priConverter.toDocumentPriority(storagePriority);
    }

};

} // storage
