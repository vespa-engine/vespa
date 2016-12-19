// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "slimeconfigrequest.h"
#include "connection.h"
#include <vespa/fnet/frt/frt.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configstate.h>
#include <vespa/config/common/configdefinition.h>
#include <vespa/config/common/trace.h>
#include <vespa/config/common/vespa_version.h>

using namespace vespalib;
using namespace vespalib::slime;
using namespace vespalib::slime::convenience;
using namespace config::protocol;
using namespace config::protocol::v2;
using namespace config::protocol::v3;

namespace config {

const vespalib::string SlimeConfigRequest::REQUEST_TYPES = "s";

SlimeConfigRequest::SlimeConfigRequest(Connection * connection,
                                       const ConfigKey & key,
                                       const vespalib::string & configMd5,
                                       int64_t currentGeneration,
                                       int64_t wantedGeneration,
                                       const vespalib::string & hostName,
                                       int64_t serverTimeout,
                                       const Trace & trace,
                                       const VespaVersion & vespaVersion,
                                       int64_t protocolVersion,
                                       const CompressionType & compressionType,
                                       const vespalib::string & methodName)
    : FRTConfigRequest(connection, key),
      _data()
{
    populateSlimeRequest(key, configMd5, currentGeneration, wantedGeneration, hostName, serverTimeout, trace, vespaVersion, protocolVersion, compressionType);
    _request->SetMethodName(methodName.c_str());
    _parameters.AddString(createJsonFromSlime(_data).c_str());
}

bool
SlimeConfigRequest::verifyKey(const ConfigKey & key) const
{
    return (key.getDefName().compare(_parameters[0]._string._str) == 0 &&
            key.getDefNamespace().compare(_parameters[7]._string._str) == 0 &&
            key.getConfigId().compare(_parameters[3]._string._str) == 0 &&
            key.getDefMd5().compare(_parameters[2]._string._str) == 0);
}

bool
SlimeConfigRequest::verifyState(const ConfigState & state) const
{
    return (state.md5.compare(_parameters[4]._string._str) == 0 &&
            state.generation == static_cast<int64_t>(_parameters[5]._intval64));
}

void
SlimeConfigRequest::populateSlimeRequest(const ConfigKey & key,
                                         const vespalib::string & configMd5,
                                         int64_t currentGeneration,
                                         int64_t wantedGeneration,
                                         const vespalib::string & hostName,
                                         int64_t serverTimeout,
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
    root.setString(REQUEST_CONFIG_MD5, Memory(configMd5));
    root.setLong(REQUEST_CURRENT_GENERATION, currentGeneration);
    root.setLong(REQUEST_WANTED_GENERATION, wantedGeneration);
    root.setLong(REQUEST_TIMEOUT, serverTimeout);
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
