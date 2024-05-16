// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/helper/configgetter.h>
#include <vespa/config/subscription/configuri.h>

namespace storage {

template <typename ConfigT>
std::unique_ptr<ConfigT> config_from(const ::config::ConfigUri& cfg_uri) {
    return ::config::ConfigGetter<ConfigT>::getConfig(cfg_uri.getConfigId(), cfg_uri.getContext());
}

} // storage

