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

    RegRpcSrvData(SBEnv &e, const char *n, const char *s, FRT_RPCRequest *r)
        : _state(RDC_INIT), env(e), name(n), spec(s), registerRequest(r)
    {}
};

RegRpcSrvCommand
RegRpcSrvCommand::makeRegRpcSrvCmd(SBEnv &env,
                                   const char *name, const char *spec,
                                   FRT_RPCRequest *req)
{
    RegRpcSrvData *data = new RegRpcSrvData(env, name, spec, req);
    return RegRpcSrvCommand(data);
}

RegRpcSrvCommand
RegRpcSrvCommand::makeRemRemCmd(SBEnv &env,
                                const char *name, const char *spec)
{
    RegRpcSrvData *data = new RegRpcSrvData(env, name, spec, NULL);
    data->_state = RegRpcSrvData::XCH_IGNORE;
    return RegRpcSrvCommand(data);
}


void
RegRpcSrvCommand::doRequest()
{
    LOG_ASSERT(_data->_state == RegRpcSrvData::RDC_INIT);
    doneHandler(OkState());
}

void
RegRpcSrvCommand::cleanupReservation()
{
    RpcServerMap &map = _data->env._rpcsrvmap;
    ReservedName *rsvp = map.getReservation(_data->name.c_str());
    if (rsvp != NULL && rsvp->isLocal) {
        map.removeReservation(_data->name.c_str());
    }
}

void
RegRpcSrvCommand::doneHandler(OkState result)
{
    LOG_ASSERT(_data != NULL);

    if (result.failed()) {
        LOG(warning, "failed in state %d: %s",
            _data->_state, result.errorMsg.c_str());
        cleanupReservation();
        // XXX should handle different state errors differently?
        if (_data->registerRequest != NULL) {
            _data->registerRequest->SetError(FRTE_RPC_METHOD_FAILED,
                                             result.errorMsg.c_str());
            _data->registerRequest->Return();
        } else {
            LOG(warning, "ignored: %s", result.errorMsg.c_str());
        }
        goto alldone;
    }
    if (_data->_state == RegRpcSrvData::RDC_INIT) {
        LOG(spam, "phase wantAdd(%s,%s)",
            _data->name.c_str(), _data->spec.c_str());
        _data->_state = RegRpcSrvData::XCH_WANTADD;
        _data->env._exchanger.wantAdd(_data->name.c_str(), _data->spec.c_str(), *this);
        return;
    } else if (_data->_state == RegRpcSrvData::XCH_WANTADD) {
        LOG(spam, "phase addManaged(%s,%s)",
            _data->name.c_str(), _data->spec.c_str());
        _data->_state = RegRpcSrvData::CHK_RPCSRV;
        _data->env._rpcsrvmanager.addManaged(_data->name.c_str(), _data->spec.c_str(), *this);
        return;
    } else if (_data->_state == RegRpcSrvData::CHK_RPCSRV) {
        LOG(spam, "phase doAdd(%s,%s)", _data->name.c_str(), _data->spec.c_str());
        _data->_state = RegRpcSrvData::XCH_DOADD;
        _data->env._exchanger.doAdd(_data->name.c_str(), _data->spec.c_str(), *this);
        return;
    } else if (_data->_state == RegRpcSrvData::XCH_DOADD) {
        LOG(debug, "done doAdd(%s,%s)", _data->name.c_str(), _data->spec.c_str());
        _data->_state = RegRpcSrvData::RDC_INVAL;
        // all OK
        _data->registerRequest->Return();
        goto alldone;
    } else if (_data->_state == RegRpcSrvData::XCH_IGNORE) {
        goto alldone;
    }
    // no other state should be possible
    LOG_ABORT("should not be reached");
 alldone:
    cleanupReservation();
    delete _data;
    _data = NULL;
}

//-----------------------------------------------------------------------------

} // namespace slobrok
