// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/transactionlog/domainpart.h>
#include <vespa/searchlib/transactionlog/session.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

namespace search {
namespace transactionlog {

struct PartInfo {
    SerialNumRange range;
    size_t numEntries;
    size_t byteSize;
    vespalib::string file;
    PartInfo(SerialNumRange range_in, size_t numEntries_in,
             size_t byteSize_in,
             vespalib::stringref file_in)
        : range(range_in), numEntries(numEntries_in), byteSize(byteSize_in),
          file(file_in) {}
};

struct DomainInfo {
    SerialNumRange range;
    size_t numEntries;
    size_t byteSize;
    std::vector<PartInfo> parts;
    DomainInfo(SerialNumRange range_in, size_t numEntries_in, size_t byteSize_in)
        : range(range_in), numEntries(numEntries_in), byteSize(byteSize_in), parts() {}
    DomainInfo()
        : range(), numEntries(0), byteSize(0), parts() {}
};

typedef std::map<vespalib::string, DomainInfo> DomainStats;

class Domain
{
public:
    typedef std::shared_ptr<Domain> SP;
    Domain(const vespalib::string &name,
           const vespalib::string &baseDir,
           vespalib::ThreadStackExecutor & executor,
           uint64_t domainPartSize,
           bool useFsync,
           DomainPart::Crc defaultCrcType,
           const common::FileHeaderContext &fileHeaderContext);

    virtual ~Domain();

    DomainInfo getDomainInfo() const;

    const vespalib::string & name() const { return _name; }
    bool erase(const SerialNum & to);

    void commit(const Packet & packet);
    int
    visit(const Domain::SP & self,
          const SerialNum & from,
          const SerialNum & to,
          FRT_Supervisor & supervisor,
          FNET_Connection *conn);

    int subscribe(const Domain::SP & self, const SerialNum & from, FRT_Supervisor & supervisor, FNET_Connection *conn);

    SerialNum begin() const;
    SerialNum end() const;
    SerialNum getSynced() const;
    void triggerSyncNow();
    bool getMarkedDeleted() const { return _markedDeleted; }
    void markDeleted() { _markedDeleted = true; }

    size_t byteSize() const;
    size_t getNumSessions() const { return _sessions.size(); }

    int startSession(int sessionId);
    int closeSession(int sessionId);

    SerialNum findOldestActiveVisit() const;
    DomainPart::SP findPart(SerialNum s);

    static vespalib::string
    getDir(const vespalib::string & base, const vespalib::string & domain) {
        return base + "/" + domain;
    }
    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) {
        return _executor.execute(std::move(task));
    }
    uint64_t size() const;
private:
    SerialNum begin(const vespalib::LockGuard & guard) const;
    SerialNum end(const vespalib::LockGuard & guard) const;
    size_t byteSize(const vespalib::LockGuard & guard) const;
    uint64_t size(const vespalib::LockGuard & guard) const;
    void cleanSessions();
    vespalib::string dir() const { return getDir(_baseDir, _name); }
    void addPart(int64_t partId, bool isLastPart);

    typedef std::vector<SerialNum> SerialNumList;

    SerialNumList scanDir();

    typedef std::map<int, Session::SP > SessionList;
    typedef std::map<int64_t, DomainPart::SP > DomainPartList;
    typedef vespalib::ThreadStackExecutor Executor;

    DomainPart::Crc     _defaultCrcType;
    Executor          & _executor;
    std::atomic<int>    _sessionId;
    const bool          _useFsync;
    vespalib::Monitor   _syncMonitor;
    bool                _pendingSync;
    vespalib::string    _name;
    uint64_t            _domainPartSize;
    DomainPartList      _parts;
    vespalib::Lock      _lock;
    vespalib::Lock      _sessionLock;
    SessionList         _sessions;
    vespalib::string    _baseDir;
    const common::FileHeaderContext &_fileHeaderContext;
    bool                _markedDeleted;
    bool                _urgentSync;
};

}
}

