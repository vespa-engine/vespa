// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "domain.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/fastos/file.h>
#include <algorithm>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".transactionlog.domain");

using vespalib::string;
using vespalib::make_string;
using vespalib::LockGuard;
using vespalib::makeTask;
using vespalib::makeClosure;
using vespalib::Monitor;
using vespalib::MonitorGuard;
using search::common::FileHeaderContext;
using std::runtime_error;
using namespace std::chrono_literals;

namespace search::transactionlog {

Domain::Domain(const string &domainName, const string & baseDir, Executor & commitExecutor,
               Executor & sessionExecutor, uint64_t domainPartSize, DomainPart::Crc defaultCrcType,
               const FileHeaderContext &fileHeaderContext) :
    _defaultCrcType(defaultCrcType),
    _commitExecutor(commitExecutor),
    _sessionExecutor(sessionExecutor),
    _sessionId(1),
    _syncMonitor(),
    _pendingSync(false),
    _name(domainName),
    _domainPartSize(domainPartSize),
    _parts(),
    _lock(),
    _sessionLock(),
    _sessions(),
    _maxSessionRunTime(),
    _baseDir(baseDir),
    _fileHeaderContext(fileHeaderContext),
    _markedDeleted(false)
{
    int retval(0);
    if ((retval = makeDirectory(_baseDir.c_str())) != 0) {
        throw runtime_error(make_string("Failed creating basedirectory %s r(%d), e(%d)", _baseDir.c_str(), retval, errno));
    }
    if ((retval = makeDirectory(dir().c_str())) != 0) {
        throw runtime_error(make_string("Failed creating domaindir %s r(%d), e(%d)", dir().c_str(), retval, errno));
    }
    SerialNumList partIdVector = scanDir();
    const int64_t lastPart = partIdVector.empty() ? 0 : partIdVector.back();
    for (const int64_t partId : partIdVector) {
        if ( partId != -1) {
            _sessionExecutor.execute(makeTask(makeClosure(this, &Domain::addPart, partId, partId == lastPart)));
        }
    }
    _sessionExecutor.sync();
    if (_parts.empty() || _parts.crbegin()->second->isClosed()) {
        _parts[lastPart].reset(new DomainPart(_name, dir(), lastPart, _defaultCrcType, _fileHeaderContext, false));
    }
}

void Domain::addPart(int64_t partId, bool isLastPart) {
    DomainPart::SP dp(new DomainPart(_name, dir(), partId, _defaultCrcType, _fileHeaderContext, isLastPart));
    if (dp->size() == 0) {
        // Only last domain part is allowed to be truncated down to
        // empty size.
        assert(isLastPart);
        dp->erase(dp->range().to() + 1);
    } else {
        {
            LockGuard guard(_lock);
            _parts[partId] = dp;
        }
        if (! isLastPart) {
            dp->close();
        }
    }
}

class Sync : public vespalib::Executor::Task
{
public:
    Sync(Monitor &syncMonitor, const DomainPart::SP &dp, bool &pendingSync) :
        _syncMonitor(syncMonitor),
        _dp(dp),
        _pendingSync(pendingSync)
    { }
private:
    void run() override {
        _dp->sync();
        MonitorGuard guard(_syncMonitor);
        _pendingSync = false;
        guard.broadcast();
    }
    
    Monitor           & _syncMonitor;
    DomainPart::SP      _dp;
    bool              & _pendingSync;
};

Domain::~Domain() { }

DomainInfo
Domain::getDomainInfo() const
{
    LockGuard guard(_lock);
    DomainInfo info(SerialNumRange(begin(guard), end(guard)), size(guard), byteSize(guard), _maxSessionRunTime);
    for (const auto &entry: _parts) {
        const DomainPart &part = *entry.second;
        info.parts.emplace_back(PartInfo(part.range(), part.size(), part.byteSize(), part.fileName()));
    }
    return info;
}

SerialNum
Domain::begin() const
{
    return begin(LockGuard(_lock));
}

SerialNum
Domain::begin(const LockGuard & guard) const
{
    (void) guard;
    assert(guard.locks(_lock));
    SerialNum s(0);
    if ( ! _parts.empty() ) {
        s = _parts.begin()->second->range().from();
    }
    return s;
}

SerialNum
Domain::end() const
{
    return end(LockGuard(_lock));
}

SerialNum
Domain::end(const LockGuard & guard) const
{
    (void) guard;
    assert(guard.locks(_lock));
    SerialNum s(0);
    if ( ! _parts.empty() ) {
        s = _parts.rbegin()->second->range().to();
    }
    return s;
}

size_t
Domain::byteSize() const
{
    return byteSize(LockGuard(_lock));
}

size_t
Domain::byteSize(const LockGuard & guard) const
{
    (void) guard;
    assert(guard.locks(_lock));
    size_t size = 0;
    for (const auto &entry : _parts) {
        const DomainPart &part = *entry.second;
        size += part.byteSize();
    }
    return size;
}

SerialNum
Domain::getSynced() const
{
    SerialNum s(0);
    LockGuard guard(_lock);
    if (_parts.empty()) {
        return s;
    }
    DomainPartList::const_iterator it(_parts.end());
    --it;
    s = it->second->getSynced();
    if (s == 0 && it != _parts.begin()) {
        --it;
        s = it->second->getSynced();
    }
    return s;
}


void
Domain::triggerSyncNow()
{
    MonitorGuard guard(_syncMonitor);
    if (!_pendingSync) {
        _pendingSync = true;
        DomainPart::SP dp(_parts.rbegin()->second);
        _commitExecutor.execute(Sync::UP(new Sync(_syncMonitor, dp, _pendingSync)));
    }
}

DomainPart::SP Domain::findPart(SerialNum s)
{
    LockGuard guard(_lock);
    DomainPartList::iterator it(_parts.upper_bound(s));
    if (!_parts.empty() && it != _parts.begin()) {
        DomainPartList::iterator prev(it);
        --prev;
        if (prev->second->range().to() > s) {
            return prev->second;
        }
    }
    if (it != _parts.end()) {
        return it->second;
    }
    return DomainPart::SP();
}

uint64_t Domain::size() const
{
    return size(LockGuard(_lock));
}

uint64_t Domain::size(const LockGuard & guard) const
{
    (void) guard;
    assert(guard.locks(_lock));
    uint64_t sz(0);
    for (const auto & part : _parts) {
        sz += part.second->size();
    }
    return sz;
}

SerialNum Domain::findOldestActiveVisit() const
{
    SerialNum oldestActive(std::numeric_limits<SerialNum>::max());
    LockGuard guard(_sessionLock);
    for (const auto & pair : _sessions) {
        Session * session(pair.second.get());
        if (!session->inSync()) {
            oldestActive = std::min(oldestActive, session->range().from());
        }
    }
    return oldestActive;
}

void Domain::cleanSessions()
{
    if ( _sessions.empty()) {
        return;
    }
    LockGuard guard(_sessionLock);
    for (SessionList::iterator it(_sessions.begin()), mt(_sessions.end()); it != mt; ) {
        Session * session(it->second.get());
        if (session->inSync()) {
            _sessions.erase(it++);
        } else if (session->finished()) {
            _sessions.erase(it++);
        } else {
            it++;
        }
    }
}

namespace {

void waitPendingSync(vespalib::Monitor &syncMonitor, bool &pendingSync)
{
    MonitorGuard guard(syncMonitor);
    while (pendingSync) {
        guard.wait();
    }
}

}

void Domain::commit(const Packet & packet)
{
    DomainPart::SP dp(_parts.rbegin()->second);
    vespalib::nbostream_longlivedbuf is(packet.getHandle().c_str(), packet.getHandle().size());
    Packet::Entry entry;
    entry.deserialize(is);
    if (dp->byteSize() > _domainPartSize) {
        waitPendingSync(_syncMonitor, _pendingSync);
        triggerSyncNow();
        waitPendingSync(_syncMonitor, _pendingSync);
        dp->close();
        dp.reset(new DomainPart(_name, dir(), entry.serial(), _defaultCrcType, _fileHeaderContext, false));
        {
            LockGuard guard(_lock);
            _parts[entry.serial()] = dp;
        }
        dp = _parts.rbegin()->second;
    }
    dp->commit(entry.serial(), packet);
    cleanSessions();
}

bool Domain::erase(SerialNum to)
{
    bool retval(true);
    /// Do not erase the last element
    for (DomainPartList::iterator it(_parts.begin()); (_parts.size() > 1) && (it->second.get()->range().to() < to); it = _parts.begin()) {
        DomainPart::SP dp(it->second);
        {
            LockGuard guard(_lock);
            _parts.erase(it);
        }
        retval = retval && dp->erase(to);
    }
    if (_parts.begin()->second->range().to() >= to) {
        _parts.begin()->second->erase(to);
    }
    return retval;
}

int Domain::visit(const Domain::SP & domain, SerialNum from, SerialNum to,
                  FRT_Supervisor & supervisor, FNET_Connection *conn)
{
    assert(this == domain.get());
    cleanSessions();
    SerialNumRange range(from, to);
    Session * session = new Session(_sessionId++, range, domain, supervisor, conn);
    LockGuard guard(_sessionLock);
    _sessions[session->id()] = Session::SP(session);
    return session->id();
}

int Domain::startSession(int sessionId)
{
    int retval(-1);
    LockGuard guard(_sessionLock);
    SessionList::iterator found = _sessions.find(sessionId);
    if (found != _sessions.end()) {
        found->second->setStartTime(std::chrono::steady_clock::now());
        if ( execute(Session::createTask(found->second)).get() == nullptr ) {
            retval = 0;
        } else {
            _sessions.erase(sessionId);
        }
    }
    return retval;
}

int Domain::closeSession(int sessionId)
{
    _commitExecutor.sync();
    int retval(-1);
    DurationSeconds sessionRunTime(0);
    {
        LockGuard guard(_sessionLock);
        SessionList::iterator found = _sessions.find(sessionId);
        if (found != _sessions.end()) {
            sessionRunTime = (std::chrono::steady_clock::now() - found->second->getStartTime());
            retval = 1;
        }
    }
    while (retval == 1) {
        std::this_thread::sleep_for(10ms);
        LockGuard guard(_sessionLock);
        SessionList::iterator found = _sessions.find(sessionId);
        if (found != _sessions.end()) {
            if ( ! found->second->isVisitRunning()) {
                _sessions.erase(sessionId);
                retval = 0;
            }
        } else {
            retval = 0;
        }
    }
    {
        LockGuard guard(_lock);
        if (sessionRunTime > _maxSessionRunTime) {
            _maxSessionRunTime = sessionRunTime;
        }
    }
    return retval;
}

Domain::SerialNumList
Domain::scanDir()
{
    SerialNumList res;

    FastOS_DirectoryScan dirScan(dir().c_str());

    const char *wantPrefix = _name.c_str();
    size_t wantPrefixLen = strlen(wantPrefix);

    while (dirScan.ReadNext()) {
        const char *ename = dirScan.GetName();
        if (strcmp(ename, ".") == 0 ||
            strcmp(ename, "..") == 0)
            continue;
        if (strncmp(ename, wantPrefix, wantPrefixLen) != 0)
            continue;
        if (ename[wantPrefixLen] != '-')
            continue;
        const char *p = ename + wantPrefixLen + 1;
        uint64_t num = strtoull(p, NULL, 10);
        string checkName = make_string("%s-%016" PRIu64, _name.c_str(), num);
        if (strcmp(checkName.c_str(), ename) != 0)
            continue;
        res.push_back(static_cast<SerialNum>(num));
    }
    std::sort(res.begin(), res.end());
    return res;
}

}
