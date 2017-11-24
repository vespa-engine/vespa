// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "slimeconfigresponse.h"
#include <vespa/config/common/configvalue.h>

class FRT_RPCRequest;
class FRT_Values;

namespace config {

/**
 * Baseclass for config responses.
 */
class FRTConfigResponseV2 : public SlimeConfigResponse {
private:
    FRTConfigResponseV2& operator=(const FRTConfigResponseV2&);
public:
    FRTConfigResponseV2(FRT_RPCRequest * request);

private:
    static const vespalib::string RESPONSE_TYPES;
    const vespalib::string & getResponseTypes() const override;
    const ConfigValue readConfigValue() const override;
};

} // namespace config

