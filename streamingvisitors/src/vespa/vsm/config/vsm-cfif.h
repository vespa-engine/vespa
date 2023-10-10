// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/vsm/config/config-vsm.h>
#include <vespa/vsm/config/config-vsmsummary.h>
#include <vespa/vespalib/util/ptrholder.h>

using vespa::config::search::vsm::VsmConfig;
using vespa::config::search::vsm::VsmsummaryConfig;
using vespa::config::search::vsm::VsmfieldsConfig;

namespace vsm {

using VsmfieldsHolder = vespalib::PtrHolder<VsmfieldsConfig>;
using VsmfieldsHandle = std::shared_ptr<VsmfieldsConfig>;

using VsmHolder = vespalib::PtrHolder<VsmConfig>;
using VsmHandle = std::shared_ptr<VsmConfig>;

using FastS_VsmsummaryHolder = vespalib::PtrHolder<VsmsummaryConfig>;
using FastS_VsmsummaryHandle = std::shared_ptr<VsmsummaryConfig>;

}

