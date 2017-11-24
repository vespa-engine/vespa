// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigresponsev2.h"
#include <vespa/fnet/frt/frt.h>

using namespace vespalib;
using namespace vespalib::slime;
using namespace vespalib::slime::convenience;
using namespace config::protocol::v2;

namespace config {

class V2Payload : public protocol::Payload {
public:
    V2Payload(const SlimePtr & data)
        : _data(data)
    {}
    const Inspector & getSlimePayload() const override {
        return extractPayload(*_data);
    }
private:
    SlimePtr _data;
};

const vespalib::string FRTConfigResponseV2::RESPONSE_TYPES = "s";

FRTConfigResponseV2::FRTConfigResponseV2(FRT_RPCRequest * request)
    : SlimeConfigResponse(request)
{
}

const vespalib::string &
FRTConfigResponseV2::getResponseTypes() const
{
    return RESPONSE_TYPES;
}

const ConfigValue
FRTConfigResponseV2::readConfigValue() const
{
    vespalib::string md5(_data->get()[RESPONSE_CONFIG_MD5].asString().make_string());
    return ConfigValue(PayloadPtr(new V2Payload(_data)), md5);
}

} // namespace config
