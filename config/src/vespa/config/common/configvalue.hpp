// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configvalue.h"
#include <vespa/config/configgen/configpayload.h>
#include <vespa/config/frt/protocol.h>

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigValue::newInstance() const
{
    if (_payload) {
        const vespalib::slime::Inspector & payload(_payload->getSlimePayload());
        return std::make_unique<ConfigType>(::config::ConfigPayload(payload));
    } else {
        return std::make_unique<ConfigType>(*this);
    }
}

}
