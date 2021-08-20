// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include <memory>

class FRT_RPCRequest;

namespace slobrok {

class SBEnv;
struct ScriptData;

class ScriptCommand
{
private:
    std::unique_ptr<ScriptData> _data;
    ScriptCommand(std::unique_ptr<ScriptData> data);
public:
    const std::string &name();
    const std::string &spec();

    ScriptCommand(ScriptCommand &&);
    ScriptCommand& operator= (ScriptCommand &&);
    ~ScriptCommand();

    static ScriptCommand makeRegRpcSrvCmd(SBEnv &env, const std::string &name, const std::string &spec, FRT_RPCRequest *req);
    static ScriptCommand makeIgnoreCmd(SBEnv &env, const std::string &name, const std::string &spec);

    void doneHandler(OkState result);
    void doRequest();
};

} // namespace slobrok
