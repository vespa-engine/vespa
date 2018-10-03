// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "cmd.h"
#include "rpc_server_map.h"
#include "reserved_name.h"
#include "remote_slobrok.h"
#include "sbenv.h"

#include <vespa/log/log.h>
LOG_SETUP(".cmd");

namespace slobrok {

//-----------------------------------------------------------------------------

struct RegRpcSrvData
{
private:
    RegRpcSrvData(const RegRpcSrvData &);
    RegRpcSrvData &operator=(const RegRpcSrvData &);

public:
    enum {
        RDC_INIT, XCH_WANTADD, CHK_RPCSRV, XCH_DOADD, XCH_IGNORE, RDC_INVAL
    } _state;

    SBEnv                 &env;
    const std::string      name;
    const std::string      spec;
    FRT_RPCRequest * const registerRequest;

    RegRpcSrvData(SBEnv &e, const std::string &n, const std::string &s, FRT_RPCRequest *r)
        : _state(RDC_INIT), env(e), name(n), spec(s), registerRequest(r)
    {}
};

RegRpcSrvCommand::RegRpcSrvCommand(std::unique_ptr<RegRpcSrvData> data)
    : _data(std::move(data))
{}

RegRpcSrvCommand::RegRpcSrvCommand(RegRpcSrvCommand &&) = default;
RegRpcSrvCommand & RegRpcSrvCommand::operator =(RegRpcSrvCommand &&) = default;
RegRpcSrvCommand::~RegRpcSrvCommand() = default;

RegRpcSrvCommand
RegRpcSrvCommand::makeRegRpcSrvCmd(SBEnv &env,
                                   const std::string &name, const std::string &spec,
                                   FRT_RPCRequest *req)
{
    return RegRpcSrvCommand(std::make_unique<RegRpcSrvData>(env, name, spec, req));
}

RegRpcSrvCommand
RegRpcSrvCommand::makeRemRemCmd(SBEnv &env, const std::string & name, const std::string &spec)
{
    auto data = std::make_unique<RegRpcSrvData>(env, name, spec, nullptr);
    data->_state = RegRpcSrvData::XCH_IGNORE;
    return RegRpcSrvCommand(std::move(data));
}


void
RegRpcSrvCommand::doRequest()
{
    LOG_ASSERT(_data->_state == RegRpcSrvData::RDC_INIT);
    doneHandler(OkState());
}

void
RegRpcSrvCommand::cleanupReservation(RegRpcSrvData & data)
{
    RpcServerMap &map = data.env._rpcsrvmap;
    const ReservedName *rsvp = map.getReservation(data.name.c_str());
    if (rsvp != nullptr && rsvp->isLocal) {
        map.removeReservation(data.name.c_str());
    }
}

void
RegRpcSrvCommand::doneHandler(OkState result)
{
    LOG_ASSERT(_data != nullptr);
    std::unique_ptr<RegRpcSrvData> dataUP = std::move(_data);
    RegRpcSrvData & data = *dataUP;
    if (result.failed()) {
        LOG(warning, "failed in state %d: %s", data._state, result.errorMsg.c_str());
        cleanupReservation(data);
        // XXX should handle different state errors differently?
        if (data.registerRequest != nullptr) {
            data.registerRequest->SetError(FRTE_RPC_METHOD_FAILED, result.errorMsg.c_str());
            data.registerRequest->Return();
        } else {
            LOG(warning, "ignored: %s", result.errorMsg.c_str());
        }
        goto alldone;
    }
    if (data._state == RegRpcSrvData::RDC_INIT) {
        LOG(spam, "phase wantAdd(%s,%s)", data.name.c_str(), data.spec.c_str());
        data._state = RegRpcSrvData::XCH_WANTADD;
        data.env._exchanger.wantAdd(data.name.c_str(), data.spec.c_str(), std::move(dataUP));
        return;
    } else if (data._state == RegRpcSrvData::XCH_WANTADD) {
        LOG(spam, "phase addManaged(%s,%s)", data.name.c_str(), data.spec.c_str());
        data._state = RegRpcSrvData::CHK_RPCSRV;
        data.env._rpcsrvmanager.addManaged(data.name, data.spec.c_str(), std::move(dataUP));
        return;
    } else if (data._state == RegRpcSrvData::CHK_RPCSRV) {
        LOG(spam, "phase doAdd(%s,%s)", data.name.c_str(), data.spec.c_str());
        data._state = RegRpcSrvData::XCH_DOADD;
        data.env._exchanger.doAdd(data.name.c_str(), data.spec.c_str(), std::move(dataUP));
        return;
    } else if (data._state == RegRpcSrvData::XCH_DOADD) {
        LOG(debug, "done doAdd(%s,%s)", data.name.c_str(), data.spec.c_str());
        data._state = RegRpcSrvData::RDC_INVAL;
        // all OK
        data.registerRequest->Return();
        goto alldone;
    } else if (data._state == RegRpcSrvData::XCH_IGNORE) {
        goto alldone;
    }
    // no other state should be possible
    LOG_ABORT("should not be reached");
 alldone:
    cleanupReservation(data);
}

//-----------------------------------------------------------------------------

} // namespace slobrok
