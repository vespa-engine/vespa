// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigresponsev3.h"
#include "compressioninfo.h"
#include <vespa/fnet/frt/frt.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.frtconfigresponsev3");

using namespace vespalib;
using namespace vespalib::slime;
using namespace vespalib::slime::convenience;
using namespace config::protocol;
using namespace config::protocol::v2;
using namespace config::protocol::v3;

namespace config {

std::string make_json(const Slime &slime, bool compact) {
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, compact);
    return buf.get().make_string();
}

class V3Payload : public Payload
{
public:
    V3Payload(const SlimePtr & data)
        : _data(data)
    {
    }

    const Inspector & getSlimePayload() const override {
        return _data->get();
    }
private:
    SlimePtr _data;
};

const vespalib::string FRTConfigResponseV3::RESPONSE_TYPES = "sx";

FRTConfigResponseV3::FRTConfigResponseV3(FRT_RPCRequest * request)
    : SlimeConfigResponse(request)
{
}

const vespalib::string &
FRTConfigResponseV3::getResponseTypes() const
{
    return RESPONSE_TYPES;
}

const ConfigValue
FRTConfigResponseV3::readConfigValue() const
{
    vespalib::string md5(_data->get()[RESPONSE_CONFIG_MD5].asString().make_string());
    CompressionInfo info;
    info.deserialize(_data->get()[RESPONSE_COMPRESSION_INFO]);
    Slime * rawData = new Slime();
    SlimePtr payloadData(rawData);
    DecompressedData data(decompress(((*_returnValues)[1]._data._buf), ((*_returnValues)[1]._data._len), info.compressionType, info.uncompressedSize));
    if (data.memRef.size > 0) {
        size_t consumedSize = JsonFormat::decode(data.memRef, *rawData);
        if (consumedSize == 0) {
            std::string json(make_json(*payloadData, true));
            LOG(error, "Error decoding JSON. Consumed size: %lu, uncompressed size: %u, compression type: %s, assumed uncompressed size(%u), compressed size: %u, slime(%s)", consumedSize, data.size, compressionTypeToString(info.compressionType).c_str(), info.uncompressedSize, ((*_returnValues)[1]._data._len), json.c_str());
            LOG_ABORT("Error decoding JSON");
        }
    }
    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "read config value md5(%s), payload size: %lu", md5.c_str(), data.memRef.size);
    }
    return ConfigValue(PayloadPtr(new V3Payload(payloadData)), md5);
}

} // namespace config
