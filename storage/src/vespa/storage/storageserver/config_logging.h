// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace config { class ConfigInstance; }

namespace storage {

void log_config_received(const config::ConfigInstance& cfg);

}
