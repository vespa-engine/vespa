// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequestfactory.h"
#include "frtconfigrequestv3.h"
#include <vespa/config/common/trace.h>
#include <vespa/config/common/configstate.h>
#include <vespa/vespalib/util/host_name.h>

using std::make_unique;

namespace config {

/**
 * Factory for creating config requests depending on protocol version;
 */
FRTConfigRequestFactory::FRTConfigRequestFactory(int traceLevel, const VespaVersion & vespaVersion, const CompressionType & compressionType)
    : _traceLevel(traceLevel),
      _vespaVersion(vespaVersion),
      _hostName(vespalib::HostName::get()),
      _compressionType(compressionType)
{
}

FRTConfigRequestFactory::~FRTConfigRequestFactory() = default;

std::unique_ptr<FRTConfigRequest>
FRTConfigRequestFactory::createConfigRequest(const ConfigKey & key, Connection * connection,
                                             const ConfigState & state, vespalib::duration serverTimeout) const
{
    return make_unique<FRTConfigRequestV3>(connection, key, state.xxhash64, state.generation, _hostName,
                                           serverTimeout, Trace(_traceLevel), _vespaVersion, _compressionType);
}

} // namespace config
