// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconfigresponse.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configstate.h>
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
public:
    SlimeConfigResponse(FRT_RPCRequest * request);
    SlimeConfigResponse& operator=(const SlimeConfigResponse&) = delete;
    ~SlimeConfigResponse() override;

    const ConfigKey & getKey() const override { return _key; }
    const ConfigValue & getValue() const override { return _value; }
    const ConfigState & getConfigState() const override { return _state; }
    const Trace & getTrace() const override { return _trace; }

    vespalib::string getHostName() const;

    void fill() override;

protected:
    virtual ConfigValue readConfigValue() const = 0;

private:
    ConfigKey   _key;
    ConfigValue _value;
    ConfigState _state;
    Trace       _trace;
    bool        _filled;

    ConfigKey readKey() const;
    ConfigState readState() const;
    void readTrace();

protected:
    SlimePtr _data;
};

} // namespace config

