// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config_logging.h"
#include <vespa/config/configgen/configinstance.h>
#include <vespa/config/print/configdatabuffer.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>

LOG_SETUP(".storageserver.config_logging");

namespace storage {

void log_config_received(const config::ConfigInstance& cfg) {
    if (LOG_WOULD_LOG(debug)) {
        config::ConfigDataBuffer buf;
        cfg.serialize(buf);
        LOG(debug, "Received new %s config: %s", cfg.defName().c_str(), buf.slimeObject().toString().c_str());
    }
}

}
