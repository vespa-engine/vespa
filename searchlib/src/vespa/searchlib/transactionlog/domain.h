// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "domainconfig.h"
#include <vespa/vespalib/util/threadexecutor.h>
#include <atomic>
#include <mutex>
#include <condition_variable>

namespace search::common { class FileHeaderContext; }
namespace search::transactionlog {

class DomainPart;
class Session;

class Domain : public Writer
{
public:
    using SP = std::shared_ptr<Domain>;
    using DomainPartSP = std::shared_ptr<DomainPart>;
    using FileHeaderContext = common::FileHeaderContext;
    Domain(const vespalib::string &name, const vespalib::string &baseDir, vespalib::Executor & executor,
           const DomainConfig & cfg, const FileHeaderContext &fileHeaderContext);

    ~Domain() override;

    DomainInfo getDomainInfo() const;
    const vespalib::string & name() const { return _name; }
    bool erase(SerialNum to);

    void append(const Packet & packet, Writer::DoneCallback onDone) override;
    [[nodiscard]] CommitResult startCommit(DoneCallback onDone) override;
    int visit(const Domain::SP & self, SerialNum from, SerialNum to, std::unique_ptr<Destination> dest);

    SerialNum begin() const;
    SerialNum end() const;
    SerialNum getSynced() const;
    void triggerSyncNow(std::unique_ptr<vespalib::IDestructorCallback> after_sync);
    bool getMarkedDeleted() const { return _markedDeleted; }
    void markDeleted() { _markedDeleted = true; }

    size_t byteSize() const;
    size_t getNumSessions() const { return _sessions.size(); }

    int startSession(int sessionId);
    int closeSession(int sessionId);

    SerialNum findOldestActiveVisit() const;
    DomainPartSP findPart(SerialNum s);

    static vespalib::string
    getDir(const vespalib::string & base, const vespalib::string & domain) {
        return base + "/" + domain;
    }
    uint64_t size() const;
    Domain & setConfig(const DomainConfig & cfg);
private:
    using UniqueLock = std::unique_lock<std::mutex>;
    DomainPartSP getActivePart();
    void verifyLock(const UniqueLock & guard) const;
    void commitIfFull(const UniqueLock & guard);
    void commitAndTransferResponses(const UniqueLock & guard);

    std::unique_ptr<CommitChunk> grabCurrentChunk(const UniqueLock & guard);
    void commitChunk(std::unique_ptr<CommitChunk> chunk, const UniqueLock & chunkOrderGuard);
    void doCommit(const SerializedChunk & serialized);
    SerialNum begin(const UniqueLock & guard) const;
    SerialNum end(const UniqueLock & guard) const;
    size_t byteSize(const UniqueLock & guard) const;
    uint64_t size(const UniqueLock & guard) const;
    void cleanSessions();
    vespalib::string dir() const { return getDir(_baseDir, _name); }
    void addPart(SerialNum partId, bool isLastPart);
    DomainPartSP optionallyRotateFile(SerialNum serialNum);

    using SerialNumList = std::vector<SerialNum>;

    SerialNumList scanDir();

    using SessionList = std::map<int, std::shared_ptr<Session>>;
    using DomainPartList = std::map<SerialNum, DomainPartSP>;
    using DurationSeconds = std::chrono::duration<double>;
    using Executor = vespalib::Executor;

    DomainConfig                 _config;
    std::unique_ptr<CommitChunk> _currentChunk;
    SerialNum                    _lastSerial;
    std::unique_ptr<Executor>    _singleCommitter;
    Executor                    &_executor;
    std::atomic<int>             _sessionId;
    vespalib::string             _name;
    DomainPartList               _parts;
    mutable std::mutex           _partsMutex;
    std::mutex                   _currentChunkMutex;
    mutable std::mutex           _sessionMutex;
    SessionList                  _sessions;
    DurationSeconds              _maxSessionRunTime;
    vespalib::string             _baseDir;
    const FileHeaderContext     &_fileHeaderContext;
    bool                         _markedDeleted;
};

}
