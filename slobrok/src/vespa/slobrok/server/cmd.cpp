// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "cmd.h"
#include "reserved_name.h"
#include "remote_slobrok.h"
#include "sbenv.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.cmd");

namespace slobrok {

//-----------------------------------------------------------------------------

struct ScriptData {
    SBEnv &env;
    const std::string name;
    const std::string spec;
    FRT_RPCRequest * const registerRequest;

    enum {
        RDC_INIT, XCH_WANTADD, CHK_RPCSRV, XCH_DOADD, XCH_IGNORE, RDC_INVAL
    } _state;

    ScriptData(SBEnv &e, const std::string &n, const std::string &s, FRT_RPCRequest *r)
      : env(e), name(n), spec(s), registerRequest(r), _state(RDC_INIT)
    {}
};

//-----------------------------------------------------------------------------

const std::string &
ScriptCommand::name() { return _data->name; }

const std::string &
ScriptCommand::spec() { return _data->spec; }

ScriptCommand::ScriptCommand(std::unique_ptr<ScriptData> data)
  : _data(std::move(data))
{}

ScriptCommand::ScriptCommand(ScriptCommand &&) = default;
ScriptCommand&
ScriptCommand::operator= (ScriptCommand &&) = default;
ScriptCommand::~ScriptCommand() = default;

ScriptCommand
ScriptCommand::makeIgnoreCmd(SBEnv &env, const std::string & name, const std::string &spec)
{
    auto data = std::make_unique<ScriptData>(env, name, spec, nullptr);
    data->_state = ScriptData::XCH_IGNORE;
    return ScriptCommand(std::move(data));
}

void
ScriptCommand::doneHandler(OkState result)
{
    LOG_ASSERT(_data);
    std::unique_ptr<ScriptData> dataUP = std::move(_data);
    LOG_ASSERT(! _data);
    ScriptData & data = *dataUP;
    const char *name_p = data.name.c_str();
    const char *spec_p = data.spec.c_str();

    if (result.failed()) {
        LOG(warning, "failed [%s->%s] in state %d: %s", name_p, spec_p, data._state, result.errorMsg.c_str());
    }
}

//-----------------------------------------------------------------------------

} // namespace slobrok
