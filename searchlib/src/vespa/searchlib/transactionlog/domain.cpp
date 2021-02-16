// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "domain.h"
#include "domainpart.h"
#include "session.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>
#include <algorithm>
#include <thread>
#include <cassert>

#include <vespa/log/log.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

LOG_SETUP(".transactionlog.domain");

using vespalib::string;
using vespalib::make_string_short::fmt;
using vespalib::makeLambdaTask;
using std::runtime_error;
using std::make_shared;

namespace search::transactionlog {
namespace {

std::unique_ptr<CommitChunk>
createCommitChunk(const DomainConfig &cfg) {
    return std::make_unique<CommitChunk>(cfg.getChunkSizeLimit(), cfg.getChunkSizeLimit()/256);
}

}

Domain::Domain(const string &domainName, const string & baseDir, Executor & executor,
               const DomainConfig & cfg, const FileHeaderContext &fileHeaderContext)
    : _config(cfg),
      _currentChunk(createCommitChunk(cfg)),
      _lastSerial(0),
      _singleCommitter(std::make_unique<vespalib::ThreadStackExecutor>(1, 128_Ki)),
      _executor(executor),
      _sessionId(1),
      _syncMonitor(),
      _pendingSync(false),
      _done_sync_tasks(),
      _name(domainName),
      _parts(),
      _lock(),
      _currentChunkMonitor(),
      _sessionLock(),
      _sessions(),
      _maxSessionRunTime(),
      _baseDir(baseDir),
      _fileHeaderContext(fileHeaderContext),
      _markedDeleted(false)
{
    int retval(0);
    if ((retval = makeDirectory(_baseDir.c_str())) != 0) {
        throw runtime_error(fmt("Failed creating basedirectory %s r(%d), e(%d)", _baseDir.c_str(), retval, errno));
    }
    if ((retval = makeDirectory(dir().c_str())) != 0) {
        throw runtime_error(fmt("Failed creating domaindir %s r(%d), e(%d)", dir().c_str(), retval, errno));
    }
    SerialNumList partIdVector = scanDir();
    const SerialNum lastPart = partIdVector.empty() ? 0 : partIdVector.back();
    for (const SerialNum partId : partIdVector) {
        if ( partId != std::numeric_limits<SerialNum>::max()) {
            _executor.execute(makeLambdaTask([this, partId, lastPart]() { addPart(partId, partId == lastPart); }));
        }
    }
    _executor.sync();
    if (_parts.empty() || _parts.crbegin()->second->isClosed()) {
        _parts[lastPart] = std::make_shared<DomainPart>(_name, dir(), lastPart, _config.getEncoding(),
                                                        _config.getCompressionlevel(), _fileHeaderContext, false);
        vespalib::File::sync(dir());
    }
    _lastSerial = end();
}

vespalib::Executor::Task::UP
Domain::execute(vespalib::Executor::Task::UP task) {
    return _executor.execute(std::move(task));
}

Domain &
Domain::setConfig(const DomainConfig & cfg) {
    _config = cfg;
    return *this;
}

void
Domain::addPart(SerialNum partId, bool isLastPart) {
    auto dp = std::make_shared<DomainPart>(_name, dir(), partId, _config.getEncoding(),
                                           _config.getCompressionlevel(), _fileHeaderContext, isLastPart);
    if (dp->size() == 0) {
        // Only last domain part is allowed to be truncated down to
        // empty size.
        assert(isLastPart);
        dp->erase(dp->range().to() + 1);
    } else {
        {
            std::lock_guard guard(_lock);
            _parts[partId] = dp;
        }
        if (! isLastPart) {
            dp->close();
        }
    }
}

Domain::~Domain() {
    std::unique_lock guard(_currentChunkMonitor);
    _currentChunkCond.notify_all();
    commitChunk(grabCurrentChunk(guard), guard);
    _singleCommitter->shutdown().sync();
}

DomainInfo
Domain::getDomainInfo() const
{
    std::unique_lock guard(_lock);
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
    return begin(UniqueLock(_lock));
}

void
Domain::verifyLock(const UniqueLock & guard) const {
    assert(guard.mutex() == &_lock);
    assert(guard.owns_lock());
}
SerialNum
Domain::begin(const UniqueLock & guard) const
{
    verifyLock(guard);
    SerialNum s(0);
    if ( ! _parts.empty() ) {
        s = _parts.cbegin()->second->range().from();
    }
    return s;
}

SerialNum
Domain::end() const
{
    return end(UniqueLock(_lock));
}

SerialNum
Domain::end(const UniqueLock & guard) const
{
    verifyLock(guard);
    SerialNum s(0);
    if ( ! _parts.empty() ) {
        s = _parts.crbegin()->second->range().to();
    }
    return s;
}

size_t
Domain::byteSize() const
{
    return byteSize(UniqueLock(_lock));
}

size_t
Domain::byteSize(const UniqueLock & guard) const
{
    verifyLock(guard);
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
    UniqueLock guard(_lock);
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
Domain::triggerSyncNow(std::unique_ptr<vespalib::Executor::Task> done_sync_task)
{
    {
        std::unique_lock guard(_currentChunkMonitor);
        commitAndTransferResponses(guard);
    }
    if (done_sync_task) {
        // Need to protect against being called from the _singleCommitter as that will cause a deadlock
        // That is done from Domain::commitChunk.lamdba->Domain::doCommit()->optionallyRotateFile->triggerSyncNow({})
        _singleCommitter->sync();
    }
    std::unique_lock guard(_syncMonitor);
    if (done_sync_task) {
        _done_sync_tasks.push_back(std::move(done_sync_task));
    }
    if (!_pendingSync) {
        _pendingSync = true;
        _executor.execute(makeLambdaTask([this, domainPart= getActivePart()]() {
            domainPart->sync();
            std::lock_guard monitorGuard(_syncMonitor);
            _pendingSync = false;
            _syncCond.notify_all();
            for (auto &task : _done_sync_tasks) {
                auto failed_task = _executor.execute(std::move(task));
                assert(!failed_task);
            }
            _done_sync_tasks.clear();
        }));
    }
}

DomainPart::SP
Domain::findPart(SerialNum s)
{
    std::lock_guard guard(_lock);
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

DomainPart::SP
Domain::getActivePart() {
    std::lock_guard guard(_lock);
    return _parts.rbegin()->second;
}

uint64_t
Domain::size() const
{
    return size(UniqueLock(_lock));
}

uint64_t
Domain::size(const UniqueLock & guard) const
{
    verifyLock(guard);
    uint64_t sz(0);
    for (const auto & part : _parts) {
        sz += part.second->size();
    }
    return sz;
}

SerialNum
Domain::findOldestActiveVisit() const
{
    SerialNum oldestActive(std::numeric_limits<SerialNum>::max());
    std::lock_guard guard(_sessionLock);
    for (const auto & pair : _sessions) {
        Session * session(pair.second.get());
        if (!session->inSync()) {
            oldestActive = std::min(oldestActive, session->range().from());
        }
    }
    return oldestActive;
}

void
Domain::cleanSessions()
{
    if ( _sessions.empty()) {
        return;
    }
    std::lock_guard guard(_sessionLock);
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

void
waitPendingSync(std::mutex &syncMonitor, std::condition_variable & syncCond, bool &pendingSync)
{
    std::unique_lock guard(syncMonitor);
    while (pendingSync) {
        syncCond.wait(guard);
    }
}

}

DomainPart::SP
Domain::optionallyRotateFile(SerialNum serialNum) {
    DomainPart::SP dp = getActivePart();
    if (dp->byteSize() > _config.getPartSizeLimit()) {
        waitPendingSync(_syncMonitor, _syncCond, _pendingSync);
        triggerSyncNow({});
        waitPendingSync(_syncMonitor, _syncCond, _pendingSync);
        dp->close();
        dp = std::make_shared<DomainPart>(_name, dir(), serialNum, _config.getEncoding(),
                                          _config.getCompressionlevel(), _fileHeaderContext, false);
        {
            std::lock_guard guard(_lock);
            _parts[serialNum] = dp;
            assert(_parts.rbegin()->first == serialNum);
        }
        vespalib::File::sync(dir());
    }
    return dp;
}

void
Domain::append(const Packet & packet, Writer::DoneCallback onDone) {
    std::unique_lock guard(_currentChunkMonitor);
    if (_lastSerial >= packet.range().from()) {
        throw runtime_error(fmt("Incoming serial number(%" PRIu64 ") must be bigger than the last one (%" PRIu64 ").",
                                packet.range().from(), _lastSerial));
    } else {
        _lastSerial = packet.range().to();
    }
    _currentChunk->add(packet, std::move(onDone));
    commitIfFull(guard);
}

Domain::CommitResult
Domain::startCommit(DoneCallback onDone) {
    std::unique_lock guard(_currentChunkMonitor);
    if ( !_currentChunk->empty() ) {
        auto completed = grabCurrentChunk(guard);
        completed->setCommitDoneCallback(std::move(onDone));
        CommitResult result(completed->createCommitResult());
        commitChunk(std::move(completed), guard);
        return result;
    }
    return CommitResult();
}

void
Domain::commitIfFull(const UniqueLock &guard) {
    if (_currentChunk->sizeBytes() > _config.getChunkSizeLimit()) {
        commitAndTransferResponses(guard);
    }
}

void
Domain::commitAndTransferResponses(const UniqueLock &guard) {
    auto completed = std::move(_currentChunk);
    _currentChunk = std::make_unique<CommitChunk>(_config.getChunkSizeLimit(), completed->stealCallbacks());
    commitChunk(std::move(completed), guard);
}

std::unique_ptr<CommitChunk>
Domain::grabCurrentChunk(const UniqueLock & guard) {
    assert(guard.mutex() == &_currentChunkMonitor && guard.owns_lock());
    auto chunk = std::move(_currentChunk);
    _currentChunk = createCommitChunk(_config);
    return chunk;
}

void
Domain::commitChunk(std::unique_ptr<CommitChunk> chunk, const UniqueLock & chunkOrderGuard) {
    assert(chunkOrderGuard.mutex() == &_currentChunkMonitor && chunkOrderGuard.owns_lock());
    _singleCommitter->execute( makeLambdaTask([this, chunk = std::move(chunk)]() mutable {
        doCommit(std::move(chunk));
    }));
}

void
Domain::doCommit(std::unique_ptr<CommitChunk> chunk) {
    const Packet & packet = chunk->getPacket();
    if (packet.empty()) return;
    
    vespalib::nbostream_longlivedbuf is(packet.getHandle().data(), packet.getHandle().size());
    Packet::Entry entry;
    entry.deserialize(is);
    DomainPart::SP dp = optionallyRotateFile(entry.serial());
    dp->commit(entry.serial(), packet);
    if (_config.getFSyncOnCommit()) {
        dp->sync();
    }
    cleanSessions();
    LOG(debug, "Releasing %zu acks and %zu entries and %zu bytes.",
        chunk->getNumCallBacks(), chunk->getPacket().size(), chunk->sizeBytes());
}

bool
Domain::erase(SerialNum to)
{
    bool retval(true);
    /// Do not erase the last element
    UniqueLock guard(_lock);
    for (DomainPartList::iterator it(_parts.begin()); (_parts.size() > 1) && (it->second.get()->range().to() < to); it = _parts.begin()) {
        DomainPart::SP dp(it->second);
        _parts.erase(it);
        guard.unlock();
        retval = retval && dp->erase(to);
        vespalib::File::sync(dir());
        guard.lock();
    }
    if (_parts.begin()->second->range().to() >= to) {
        _parts.begin()->second->erase(to);
    }
    return retval;
}

int
Domain::visit(const Domain::SP & domain, SerialNum from, SerialNum to, std::unique_ptr<Destination> dest)
{
    assert(this == domain.get());
    cleanSessions();
    SerialNumRange range(from, to);
    auto session = std::make_shared<Session>(_sessionId++, range, domain, std::move(dest));
    int id = session->id();
    std::lock_guard guard(_sessionLock);
    _sessions[id] = std::move(session);
    return id;
}

int
Domain::startSession(int sessionId)
{
    int retval(-1);
    std::lock_guard guard(_sessionLock);
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

int
Domain::closeSession(int sessionId)
{
    _executor.sync();
    int retval(-1);
    DurationSeconds sessionRunTime(0);
    {
        std::lock_guard guard(_sessionLock);
        SessionList::iterator found = _sessions.find(sessionId);
        if (found != _sessions.end()) {
            sessionRunTime = (std::chrono::steady_clock::now() - found->second->getStartTime());
            retval = 1;
        }
    }
    while (retval == 1) {
        std::this_thread::sleep_for(10ms);
        std::lock_guard guard(_sessionLock);
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
        std::lock_guard guard(_lock);
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
        string checkName = fmt("%s-%016" PRIu64, _name.c_str(), num);
        if (strcmp(checkName.c_str(), ename) != 0)
            continue;
        res.push_back(static_cast<SerialNum>(num));
    }
    std::sort(res.begin(), res.end());
    return res;
}

}
