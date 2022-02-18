// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "slimeconfigrequest.h"
#include "connection.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configstate.h>
#include <vespa/config/common/configdefinition.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/vespa_version.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/data/simple_buffer.h>


using namespace vespalib;
using namespace vespalib::slime;
using namespace vespalib::slime::convenience;
using namespace config::protocol;
using namespace config::protocol::v2;
using namespace config::protocol::v3;

namespace config {

SlimeConfigRequest::SlimeConfigRequest(Connection * connection,
                                       const ConfigKey & key,
                                       const vespalib::string & configXxhash64,
                                       int64_t currentGeneration,
                                       const vespalib::string & hostName,
                                       duration serverTimeout,
                                       const Trace & trace,
                                       const VespaVersion & vespaVersion,
                                       int64_t protocolVersion,
                                       const CompressionType & compressionType,
                                       const vespalib::string & methodName)
    : FRTConfigRequest(connection, key),
      _data()
{
    populateSlimeRequest(key, configXxhash64, currentGeneration, hostName, serverTimeout, trace, vespaVersion, protocolVersion, compressionType);
    _request->SetMethodName(methodName.c_str());
    _parameters.AddString(createJsonFromSlime(_data).c_str());
}

SlimeConfigRequest::~SlimeConfigRequest() = default;

bool
SlimeConfigRequest::verifyState(const ConfigState & state) const
{
    return (state.xxhash64.compare(_data[REQUEST_CONFIG_XXHASH64].asString().make_stringref()) == 0 &&
            state.generation == _data[REQUEST_CURRENT_GENERATION].asLong());
}

void
SlimeConfigRequest::populateSlimeRequest(const ConfigKey & key,
                                         const vespalib::string & configXxhash64,
                                         int64_t currentGeneration,
                                         const vespalib::string & hostName,
                                         duration serverTimeout,
                                         const Trace & trace,
                                         const VespaVersion & vespaVersion,
                                         int64_t protocolVersion,
                                         const CompressionType & compressionType)
{
    Cursor & root(_data.setObject());
    root.setLong(REQUEST_VERSION, protocolVersion);
    root.setString(REQUEST_DEF_NAME, Memory(key.getDefName()));
    root.setString(REQUEST_DEF_NAMESPACE, Memory(key.getDefNamespace()));
    root.setString(REQUEST_DEF_MD5, Memory(key.getDefMd5()));
    ConfigDefinition def(key.getDefSchema());
    def.serialize(root.setArray(REQUEST_DEF_CONTENT));
    root.setString(REQUEST_CLIENT_CONFIGID, Memory(key.getConfigId()));
    root.setString(REQUEST_CLIENT_HOSTNAME, Memory(hostName));
    root.setString(REQUEST_CONFIG_XXHASH64, Memory(configXxhash64));
    root.setLong(REQUEST_CURRENT_GENERATION, currentGeneration);
    root.setLong(REQUEST_TIMEOUT, vespalib::count_ms(serverTimeout));
    trace.serialize(root.setObject(REQUEST_TRACE));
    root.setString(REQUEST_COMPRESSION_TYPE, Memory(compressionTypeToString(compressionType)));
    root.setString(REQUEST_VESPA_VERSION, Memory(vespaVersion.toString()));
}

vespalib::string
SlimeConfigRequest::createJsonFromSlime(const Slime & data)
{
    SimpleBuffer buf;
    JsonFormat::encode(data, buf, true);
    return buf.get().make_string();
}

}
