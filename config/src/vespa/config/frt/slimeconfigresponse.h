// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconfigresponse.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/misc.h>
#include <vespa/vespalib/data/slime/slime.h>
#include "protocol.h"

class FRT_RPCRequest;
class FRT_Values;

namespace config {

/**
 * Baseclass for config responses.
 */
class SlimeConfigResponse : public FRTConfigResponse {
private:
    SlimeConfigResponse& operator=(const SlimeConfigResponse&);
public:
    SlimeConfigResponse(FRT_RPCRequest * request);
    virtual ~SlimeConfigResponse() {}

    const ConfigKey & getKey() const { return _key; }
    const ConfigValue & getValue() const { return _value; }
    const ConfigState & getConfigState() const { return _state; }
    const Trace & getTrace() const { return _trace; }

    vespalib::string getHostName() const;
    vespalib::string getConfigMd5() const;

    void fill();

protected:
    virtual const ConfigValue readConfigValue() const = 0;

private:
    ConfigKey _key;
    ConfigValue _value;
    ConfigState _state;
    Trace _trace;
    bool _filled;

    const ConfigKey readKey() const;
    const ConfigState readState() const;
    void readTrace();

protected:
    SlimePtr _data;
};

} // namespace config

