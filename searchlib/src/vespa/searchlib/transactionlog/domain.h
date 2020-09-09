// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "domainconfig.h"
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <atomic>

namespace search::common { class FileHeaderContext; }
namespace search::transactionlog {

class DomainPart;
class Session;

class Domain
{
public:
    using SP = std::shared_ptr<Domain>;
    using Executor = vespalib::SyncableThreadExecutor;
    using DomainPartSP = std::shared_ptr<DomainPart>;
    Domain(const vespalib::string &name, const vespalib::string &baseDir, Executor & commitExecutor,
           Executor & sessionExecutor, const DomainConfig & cfg, const common::FileHeaderContext &fileHeaderContext);

    ~Domain();

    DomainInfo getDomainInfo() const;
    const vespalib::string & name() const { return _name; }
    bool erase(SerialNum to);

    void commit(const Packet & packet, Writer::DoneCallback onDone);
    int visit(const Domain::SP & self, SerialNum from, SerialNum to, std::unique_ptr<Destination> dest);

    SerialNum begin() const;
    SerialNum end() const;
    SerialNum getSynced() const;
    void triggerSyncNow();
    bool commitIfStale();
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
    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) {
        return _sessionExecutor.execute(std::move(task));
    }
    uint64_t size() const;
    Domain & setConfig(const DomainConfig & cfg);
private:
    bool commitIfStale(const vespalib::MonitorGuard & guard);
    void commitIfFull(const vespalib::MonitorGuard & guard);
    class Chunk {
    public:
        Chunk();
        ~Chunk();
        void add(const Packet & packet, Writer::DoneCallback onDone);
        size_t sizeBytes() const { return _data.sizeBytes(); }
        const Packet & getPacket() const { return _data; }
        vespalib::duration age() const;
        size_t getNumCallBacks() const { return _callBacks.size(); }
    private:
        Packet                             _data;
        std::vector<Writer::DoneCallback>  _callBacks;
        vespalib::steady_time              _firstArrivalTime;
    };

    std::unique_ptr<Chunk> grabCurrentChunk(const vespalib::MonitorGuard & guard);
    bool commitChunk(std::unique_ptr<Chunk> chunk, const vespalib::MonitorGuard & chunkOrderGuard);
    void doCommit(std::unique_ptr<Chunk> chunk);
    SerialNum begin(const vespalib::LockGuard & guard) const;
    SerialNum end(const vespalib::LockGuard & guard) const;
    size_t byteSize(const vespalib::LockGuard & guard) const;
    uint64_t size(const vespalib::LockGuard & guard) const;
    void cleanSessions();
    vespalib::string dir() const { return getDir(_baseDir, _name); }
    void addPart(int64_t partId, bool isLastPart);

    using SerialNumList = std::vector<SerialNum>;

    SerialNumList scanDir();

    using SessionList = std::map<int, std::shared_ptr<Session>>;
    using DomainPartList = std::map<int64_t, DomainPartSP>;
    using DurationSeconds = std::chrono::duration<double>;

    DomainConfig           _config;
    std::unique_ptr<Chunk> _currentChunk;
    SerialNum              _lastSerial;
    std::unique_ptr<Executor> _singleCommiter;
    Executor             & _commitExecutor;
    Executor             & _sessionExecutor;
    std::atomic<int>       _sessionId;
    vespalib::Monitor      _syncMonitor;
    bool                   _pendingSync;
    vespalib::string       _name;
    DomainPartList         _parts;
    vespalib::Lock         _lock;
    vespalib::Monitor      _currentChunkMonitor;
    vespalib::Lock         _sessionLock;
    SessionList            _sessions;
    DurationSeconds        _maxSessionRunTime;
    vespalib::string       _baseDir;
    const common::FileHeaderContext &_fileHeaderContext;
    bool                   _markedDeleted;
};

}
