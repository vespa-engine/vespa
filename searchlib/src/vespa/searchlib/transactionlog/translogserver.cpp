// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "translogserver.h"
#include "domain.h"
#include "client_common.h"
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/transport.h>
#include <fstream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".transactionlog.server");

using vespalib::make_string;
using vespalib::stringref;
using vespalib::IllegalArgumentException;
using search::common::FileHeaderContext;
using std::make_shared;
using std::runtime_error;
using namespace std::chrono_literals;

namespace search::transactionlog {

namespace {

class SyncHandler : public std::enable_shared_from_this<SyncHandler>
{
    std::atomic<bool>         & _closed;
    FRT_RPCRequest            & _req;
    Domain::SP                  _domain;
    TransLogServer::Session::SP _session; 
    SerialNum                   _syncTo;
    
public:
    SyncHandler(std::atomic<bool>& closed, FRT_RPCRequest *req, const Domain::SP &domain,
                const TransLogServer::Session::SP &session, SerialNum syncTo) noexcept;

    ~SyncHandler();
    void poll();
};

SyncHandler::SyncHandler(std::atomic<bool>& closed, FRT_RPCRequest *req, const Domain::SP &domain,
                         const TransLogServer::Session::SP &session, SerialNum syncTo) noexcept
    : _closed(closed),
      _req(*req),
      _domain(domain),
      _session(session),
      _syncTo(syncTo)
{
}

SyncHandler::~SyncHandler() = default;

void
SyncHandler::poll()
{
    SerialNum synced(_domain->getSynced());
    if (_session->getDown() ||
        _domain->getMarkedDeleted() ||
        _closed.load(std::memory_order_acquire) ||
        synced >= _syncTo)
    {
        FRT_Values &rvals = *_req.GetReturn();
        rvals.AddInt32(0);
        rvals.AddInt64(synced);
        _req.Return();
    } else {
        _domain->triggerSyncNow(vespalib::makeLambdaTask([self = shared_from_this()]() { self->poll(); }));
    }
}

VESPA_THREAD_STACK_TAG(tls_executor);

}

TransLogServer::TransLogServer(const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                               const FileHeaderContext &fileHeaderContext)
    : TransLogServer(name, listenPort, baseDir, fileHeaderContext,
                     DomainConfig().setEncoding(Encoding(Encoding::xxh64, Encoding::Compression::none))
                                        .setPartSizeLimit(0x10000000).setChunkSizeLimit(0x40000))
{}

TransLogServer::TransLogServer(const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                               const FileHeaderContext &fileHeaderContext, const DomainConfig &  cfg)
    : TransLogServer(name, listenPort, baseDir, fileHeaderContext, cfg, 4)
{}

TransLogServer::TransLogServer(const vespalib::string &name, int listenPort, const vespalib::string &baseDir,
                               const FileHeaderContext &fileHeaderContext, const DomainConfig & cfg, size_t maxThreads)
    : FRT_Invokable(),
      _name(name),
      _baseDir(baseDir),
      _domainConfig(cfg),
      _executor(maxThreads, 128_Ki, tls_executor),
      _threadPool(std::make_unique<FastOS_ThreadPool>(120_Ki)),
      _transport(std::make_unique<FNET_Transport>()),
      _supervisor(std::make_unique<FRT_Supervisor>(_transport.get())),
      _domains(),
      _reqQ(),
      _fileHeaderContext(fileHeaderContext),
      _closed(false)
{
    int retval(0);
    if ((retval = makeDirectory(_baseDir.c_str())) == 0) {
        if ((retval = makeDirectory(dir().c_str())) == 0) {
            std::ifstream domainDir(domainList().c_str());
            while (domainDir.good() && !domainDir.eof()) {
                vespalib::string domainName;
                domainDir >> domainName;
                if ( ! domainName.empty()) {
                    try {
                        auto domain = make_shared<Domain>(domainName, dir(), _executor, cfg, _fileHeaderContext);
                        _domains[domain->name()] = domain;
                    } catch (const std::exception & e) {
                        LOG(warning, "Failed creating %s domain on startup. Exception = %s", domainName.c_str(), e.what());
                    }
                }
            }
            exportRPC(*_supervisor);
            char listenSpec[32];
            sprintf(listenSpec, "tcp/%d", listenPort);
            bool listenOk(false);
            for (int i(600); !listenOk && i; i--) {
                if (_supervisor->Listen(listenSpec)) {
                    _transport->Start(_threadPool.get());
                    listenOk = true;
                } else {
                    LOG(warning, "Failed listening at port %s trying for %d seconds more.", listenSpec, i);
                    std::this_thread::sleep_for(1s);
                }
            }
            if ( ! listenOk ) {
                throw std::runtime_error(make_string("Failed listening at port %s. Giving up. Requires manual intervention.", listenSpec));
            }
        } else {
            throw std::runtime_error(make_string("Failed creating tls dir %s r(%d), e(%d). Requires manual intervention.", dir().c_str(), retval, errno));
        }
    } else {
        throw std::runtime_error(make_string("Failed creating tls base dir %s r(%d), e(%d). Requires manual intervention.", _baseDir.c_str(), retval, errno));
    }
    start(*_threadPool);
}

TransLogServer::~TransLogServer()
{
    _closed = true;
    stop();
    join();
    _executor.sync();
    _executor.shutdown();
    _executor.sync();
    _transport->ShutDown(true);
}

bool
TransLogServer::onStop()
{
    LOG(info, "Stopping TLS");
    _reqQ.push(nullptr);
    return true;
}

void
TransLogServer::run()
{
    FRT_RPCRequest *req(nullptr);
    bool hasPacket(false);
    do {
        for (req = nullptr; (hasPacket = _reqQ.pop(req, 60000)) && (req != nullptr); req = nullptr) {
            bool immediate = true;
            if (strcmp(req->GetMethodName(), "domainSessionClose") == 0) {
                domainSessionClose(req);
            } else if (strcmp(req->GetMethodName(), "domainVisit") == 0) {
                domainVisit(req);
            } else if (strcmp(req->GetMethodName(), "createDomain") == 0) {
                createDomain(req);
            } else if (strcmp(req->GetMethodName(), "deleteDomain") == 0) {
                deleteDomain(req);
            } else if (strcmp(req->GetMethodName(), "openDomain") == 0) {
                openDomain(req);
            } else if (strcmp(req->GetMethodName(), "listDomains") == 0) {
                listDomains(req);
            } else if (strcmp(req->GetMethodName(), "domainStatus") == 0) {
                domainStatus(req);
            } else if (strcmp(req->GetMethodName(), "domainCommit") == 0) {
                domainCommit(req);
            } else if (strcmp(req->GetMethodName(), "domainPrune") == 0) {
                domainPrune(req);
            } else if (strcmp(req->GetMethodName(), "domainSessionRun") == 0) {
                domainSessionRun(req);
            } else if (strcmp(req->GetMethodName(), "domainSync") == 0) {
                immediate = false;
                domainSync(req);
            } else {
                LOG(warning, "Received unknown RPC command %s", req->GetMethodName());
            }
            if (immediate) {
                req->Return();
            }
        }
    } while (running() && !(hasPacket && (req == nullptr)));
    LOG(info, "TLS Stopped");
}

TransLogServer &
TransLogServer::setDomainConfig(const DomainConfig & cfg) {
    WriteGuard domainGuard(_domainMutex);
    _domainConfig = cfg;
    for(auto &domain: _domains) {
        domain.second->setConfig(cfg);
    }
    return *this;
}

DomainStats
TransLogServer::getDomainStats() const
{
    DomainStats retval;
    ReadGuard domainGuard(_domainMutex);
    for (const auto &elem : _domains) {
        retval[elem.first] = elem.second->getDomainInfo();
    }
    return retval;
}

std::vector<vespalib::string>
TransLogServer::getDomainNames()
{
    std::vector<vespalib::string> names;
    ReadGuard guard(_domainMutex);
    for(const auto &domain: _domains) {
        names.push_back(domain.first);
    }
    return names;
}

Domain::SP
TransLogServer::findDomain(stringref domainName) const
{
    ReadGuard domainGuard(_domainMutex);
    auto found(_domains.find(domainName));
    if (found != _domains.end()) {
        return found->second;
    }
    return DomainSP();
}

void
TransLogServer::exportRPC(FRT_Supervisor & supervisor)
{
    _supervisor->SetSessionInitHook(FRT_METHOD(TransLogServer::initSession), this);
    _supervisor->SetSessionFiniHook(FRT_METHOD(TransLogServer::finiSession), this);
    _supervisor->SetSessionDownHook(FRT_METHOD(TransLogServer::downSession), this);
    FRT_ReflectionBuilder rb( & supervisor);

    //-- Create Domain -----------------------------------------------------------
    rb.DefineMethod("createDomain", "s", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Create a new domain.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ReturnDesc("handle", "A handle(int) to the domain. Negative number indicates error.");

    //-- Delete Domain -----------------------------------------------------------
    rb.DefineMethod("deleteDomain", "s", "is", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Create a new domain.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ReturnDesc("retval", "0 on success. Negative number indicates error.");
    rb.ReturnDesc("errormsg", "Message describing the error, if any.");

    //-- Open Domain -----------------------------------------------------------
    rb.DefineMethod("openDomain", "s", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Open an existing domain.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ReturnDesc("handle", "A handle(int) to the domain. Negative number indicates error.");

    //-- List Domains -----------------------------------------------------------
    rb.DefineMethod("listDomains", "", "is", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Will return a list of all the domains.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");
    rb.ReturnDesc("domains", "List of all the domains in a newline separated string");

    //-- Domain Status -----------------------------------------------------------
    rb.DefineMethod("domainStatus", "s", "illl", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("This will return key status information about the domain.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");
    rb.ReturnDesc("begin", "The id of the first element in the log.");
    rb.ReturnDesc("end", "The id of the last element in the log.");
    rb.ReturnDesc("size", "Number of elements in the log.");

    //-- Domain Commit -----------------------------------------------------------
    rb.DefineMethod("domainCommit", "sx", "is", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Will commit the data to the log.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("packet", "The data to commit to the domain.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");
    rb.ReturnDesc("message", "A textual description of the result code.");

    //-- Domain Prune -----------------------------------------------------------
    rb.DefineMethod("domainPrune", "sl", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Will erase all operations prior to the serial number.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("to", "Will erase all up and including.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");

    //-- Domain Visit -----------------------------------------------------------
    rb.DefineMethod("domainVisit", "sll", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("This will create a visitor that return all operations in the range.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("from", "Will return all entries following(not including) <from>.");
    rb.ParamDesc("to", "Will return all entries including <to>.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error. Positive number is the sessionid");

    //-- Domain Session Run -----------------------------------------------------------
    rb.DefineMethod("domainSessionRun", "si", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("This will start the session thread.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("sessionid", "The session identifier.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");

    //-- Domain Session Close -----------------------------------------------------------
    rb.DefineMethod("domainSessionClose", "si", "i", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("This will close the session.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("sessionid", "The session identifier.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error. 1 means busy -> retry. 0 is OK.");

    //-- Domain Sync --
    rb.DefineMethod("domainSync", "sl", "il", FRT_METHOD(TransLogServer::relayToThreadRPC), this);
    rb.MethodDesc("Sync domain to given entry");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("syncto", "Entry to sync to");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Negative number indicates error.");
    rb.ReturnDesc("syncedto", "Entry synced to");
}

namespace {

constexpr double NEVER(-1.0);

void
writeDomainDir(std::shared_lock<std::shared_mutex> &guard,
               vespalib::string dir,
               vespalib::string domainList,
               const std::map<vespalib::string, std::shared_ptr<Domain>> &domains)
{
    (void) guard;
    vespalib::string domainListTmp(domainList + ".tmp");
    vespalib::unlink(domainListTmp);
    std::ofstream domainDir(domainListTmp.c_str(), std::ios::trunc);
    for (const auto &domainEntry : domains) {
        domainDir << domainEntry.first << std::endl;
    }
    domainDir.close();
    vespalib::File::sync(domainListTmp);
    vespalib::rename(domainListTmp, domainList, false, false);
    vespalib::File::sync(dir);
}

class RPCDestination : public Destination {
public:
    RPCDestination(FRT_Supervisor & supervisor, FNET_Connection * connection)
        : _supervisor(supervisor), _connection(connection), _ok(true)
    {
        _connection->AddRef();
    }
    ~RPCDestination() override { _connection->SubRef(); }

    bool ok() const override {
        return _ok;
    }

    bool send(int32_t id, const vespalib::string & domain, const Packet & packet) override {
        FRT_RPCRequest *req = _supervisor.AllocRPCRequest();
        req->SetMethodName("visitCallback");
        req->GetParams()->AddString(domain.c_str());
        req->GetParams()->AddInt32(id);
        req->GetParams()->AddData(packet.getHandle().data(), packet.getHandle().size());
        return send(req);
    }

    bool sendDone(int32_t id, const vespalib::string & domain) override {
        FRT_RPCRequest *req = _supervisor.AllocRPCRequest();
        req->SetMethodName("eofCallback");
        req->GetParams()->AddString(domain.c_str());
        req->GetParams()->AddInt32(id);
        bool retval(send(req));
        return retval;
    }
    bool connected() const override {
        return (_connection->GetState() <= FNET_Connection::FNET_CONNECTED);
    }
private:
    bool send(FRT_RPCRequest * req) {
        int32_t retval = rpc(req);
        if ( ! ((retval == client::RPC::OK) || (retval == FRTE_RPC_CONNECTION)) ) {
            LOG(error, "Return value != OK(%d) in send for method 'visitCallback'.", retval);
        }
        req->SubRef();

        return (retval == client::RPC::OK);
    }
    int32_t rpc(FRT_RPCRequest * req) {
        int32_t retval(-7);
        LOG(debug, "rpc %s starting.", req->GetMethodName());
        FRT_Supervisor::InvokeSync(_supervisor.GetTransport(), _connection, req, NEVER);
        if (req->GetErrorCode() == FRTE_NO_ERROR) {
            retval = (req->GetReturn()->GetValue(0)._intval32);
            LOG(debug, "rpc %s = %d\n", req->GetMethodName(), retval);
        } else if (req->GetErrorCode() == FRTE_RPC_TIMEOUT) {
            LOG(warning, "rpc %s timed out. Will allow to continue: error(%d): %s\n", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
            retval = -req->GetErrorCode();
        } else {
            if (req->GetErrorCode() != FRTE_RPC_CONNECTION) {
                LOG(warning, "rpc %s: error(%d): %s\n", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
            }
            retval = -req->GetErrorCode();
            _ok = false;
        }
        return retval;
    }
    FRT_Supervisor   & _supervisor;
    FNET_Connection  * _connection;
    bool               _ok;
};

}

void
TransLogServer::createDomain(FRT_RPCRequest *req)
{
    uint32_t retval(0);
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();

    const char * domainName = params[0]._string._str;
    LOG(debug, "createDomain(%s)", domainName);

    std::lock_guard createDeleteGuard(_fileLock);
    Domain::SP domain(findDomain(domainName));
    if ( !domain ) {
        try {
            domain = std::make_shared<Domain>(domainName, dir(), _executor, _domainConfig, _fileHeaderContext);
            {
                WriteGuard domainGuard(_domainMutex);
                _domains[domain->name()] = domain;
            }
            ReadGuard domainGuard(_domainMutex);
            writeDomainDir(domainGuard, dir(), domainList(), _domains);
        } catch (const std::exception & e) {
            LOG(warning, "Failed creating %s domain. Exception = %s", domainName, e.what());
            retval = uint32_t(-1);
        }
    }

    ret.AddInt32(retval);
}

void
TransLogServer::deleteDomain(FRT_RPCRequest *req)
{
    uint32_t retval(0);
    vespalib::string msg("ok");
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();

    const char * domainName = params[0]._string._str;
    LOG(debug, "deleteDomain(%s)", domainName);

    std::lock_guard createDeleteGuard(_fileLock);
    Domain::SP domain(findDomain(domainName));
    if ( !domain || (domain->getNumSessions() == 0)) {
        try {
            if (domain) {
                domain->markDeleted();
                WriteGuard domainGuard(_domainMutex);
                _domains.erase(domainName);
            }
            vespalib::rmdir(Domain::getDir(dir(), domainName), true);
            vespalib::File::sync(dir());
            ReadGuard domainGuard(_domainMutex);
            writeDomainDir(domainGuard, dir(), domainList(), _domains);
        } catch (const std::exception & e) {
            msg = make_string("Failed deleting %s domain. Exception = %s", domainName, e.what());
            retval = -1;
            LOG(warning, "%s", msg.c_str());
        }
    } else {
        retval = -2;
        msg = vespalib::make_string("Domain '%s' is open. Can not delete open domains.", domainName);
        LOG(warning, "%s", msg.c_str());
    }
    ret.AddInt32(retval);
    ret.AddString(msg.c_str());
}

void
TransLogServer::openDomain(FRT_RPCRequest *req)
{
    uint32_t retval(0);
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();

    const char * domainName = params[0]._string._str;
    LOG(debug, "openDomain(%s)", domainName);

    Domain::SP domain(findDomain(domainName));
    if ( !domain ) {
        retval = uint32_t(-1);
    }

    ret.AddInt32(retval);
}

void
TransLogServer::listDomains(FRT_RPCRequest *req)
{
    FRT_Values & ret    = *req->GetReturn();
    LOG(debug, "listDomains()");

    vespalib::string domains;
    ReadGuard domainGuard(_domainMutex);
    for(DomainList::const_iterator it(_domains.begin()), mt(_domains.end()); it != mt; it++) {
        domains += it->second->name();
        domains += "\n";
    }
    ret.AddInt32(0);
    ret.AddString(domains.c_str());
}

void
TransLogServer::domainStatus(FRT_RPCRequest *req)
{
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    LOG(debug, "domainStatus(%s)", domainName);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        ret.AddInt32(0);
        ret.AddInt64(domain->begin());
        ret.AddInt64(domain->end());
        ret.AddInt64(domain->size());
    } else {
        ret.AddInt32(uint32_t(-1));
        ret.AddInt64(0);
        ret.AddInt64(0);
        ret.AddInt64(0);
    }
}

std::shared_ptr<Writer>
TransLogServer::getWriter(const vespalib::string & domainName) const
{
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        return domain;
    } else {
        throw IllegalArgumentException("Could not find domain " + domainName);
    }
}

void
TransLogServer::domainCommit(FRT_RPCRequest *req)
{
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    LOG(debug, "domainCommit(%s)(%d)", domainName, params[1]._data._len);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        Packet packet(params[1]._data._buf, params[1]._data._len);
        try {
            vespalib::Gate gate;
            {
                // Need to scope in order to drain out all the callbacks.
                domain->append(packet, make_shared<vespalib::GateCallback>(gate));
                auto keep = domain->startCommit(make_shared<vespalib::IgnoreCallback>());
            }
            gate.await();
            ret.AddInt32(0);
            ret.AddString("ok");
        } catch (const std::exception & e) {
            ret.AddInt32(-2);
            ret.AddString(make_string("Exception during commit on %s : %s", domainName, e.what()).c_str());
        }
    } else {
        ret.AddInt32(-1);
        ret.AddString(make_string("Could not find domain %s", domainName).c_str());
    }
}

void
TransLogServer::domainVisit(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    LOG(debug, "domainVisit(%s)", domainName);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        SerialNum from(params[1]._intval64);
        SerialNum to(params[2]._intval64);
        LOG(debug, "domainVisit(%s, %" PRIu64 ", %" PRIu64 ")", domainName, from, to);
        retval = domain->visit(domain, from, to, std::make_unique<RPCDestination>(*_supervisor, req->GetConnection()));
    }
    ret.AddInt32(retval);
}

void
TransLogServer::domainSessionRun(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int sessionId(params[1]._intval32);
    LOG(debug, "domainSessionRun(%s, %d)", domainName, sessionId);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        LOG(debug, "Valid domain domainSessionRun(%s, %d)", domainName, sessionId);
        retval = domain->startSession(sessionId);
    }
    ret.AddInt32(retval);
}

void
TransLogServer::relayToThreadRPC(FRT_RPCRequest *req)
{
    req->Detach();
    _reqQ.push(req);
}

void
TransLogServer::domainSessionClose(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int sessionId(params[1]._intval32);
    LOG(debug, "domainSessionClose(%s, %d)", domainName, sessionId);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        LOG(debug, "Valid domain domainSessionClose(%s, %d)", domainName, sessionId);
        retval = domain->closeSession(sessionId);
    }
    LOG(debug, "domainSessionClose(%s, %d) = %d", domainName, sessionId, retval);
    ret.AddInt32(retval);
}

void
TransLogServer::domainPrune(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    LOG(debug, "domainPrune(%s)", domainName);
    Domain::SP domain(findDomain(domainName));
    if (domain) {
        SerialNum to(params[1]._intval64);
        SerialNum oldestActive = domain->findOldestActiveVisit();
        if (oldestActive < to) {
            retval = 1;
        } else if (domain->erase(to)) {
            retval = 0;
        }
    }
    ret.AddInt32(retval);
}

const TransLogServer::Session::SP &
TransLogServer::getSession(FRT_RPCRequest *req)
{
    FNET_Connection *conn = req->GetConnection();
    void *vctx = conn->GetContext()._value.VOIDP;
    Session::SP *sessionspp = static_cast<Session::SP *>(vctx);
    return *sessionspp;
}

void
TransLogServer::initSession(FRT_RPCRequest *req)
{
    req->GetConnection()->SetContext(new Session::SP(new Session()));
}

void
TransLogServer::finiSession(FRT_RPCRequest *req)
{
    FNET_Connection *conn = req->GetConnection();
    void *vctx = conn->GetContext()._value.VOIDP;
    conn->GetContextPT()->_value.VOIDP = nullptr;
    Session::SP *sessionspp = static_cast<Session::SP *>(vctx);
    delete sessionspp;
}

void
TransLogServer::downSession(FRT_RPCRequest *req)
{
    getSession(req)->setDown();
}

void
TransLogServer::domainSync(FRT_RPCRequest *req)
{
    FRT_Values & params = *req->GetParams();
    const char * domainName = params[0]._string._str;
    SerialNum syncTo(params[1]._intval64);
    LOG(debug, "domainSync(%s, %" PRIu64 ")", domainName, syncTo);
    Domain::SP domain(findDomain(domainName));
    Session::SP session(getSession(req));

    if ( ! domain) {
        FRT_Values &rvals = *req->GetReturn();
        rvals.AddInt32(0);
        rvals.AddInt64(0);
        req->Return();
        return;
    }

    auto syncHandler = std::make_shared<SyncHandler>(_closed, req, domain, session, syncTo);
    syncHandler->poll();
}

}
