// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/vsm/config/config-vsm.h>
#include <vespa/vsm/config/config-vsmsummary.h>
#include <vespa/vespalib/util/ptrholder.h>

using vespa::config::search::vsm::VsmConfig;
using vespa::config::search::vsm::VsmsummaryConfig;
using vespa::config::search::vsm::VsmfieldsConfig;

namespace vsm {

typedef vespalib::PtrHolder<VsmfieldsConfig> VsmfieldsHolder;
typedef std::shared_ptr<VsmfieldsConfig> VsmfieldsHandle;

typedef vespalib::PtrHolder<VsmConfig> VsmHolder;
typedef std::shared_ptr<VsmConfig> VsmHandle;

typedef vespalib::PtrHolder<VsmsummaryConfig> FastS_VsmsummaryHolder;
typedef std::shared_ptr<VsmsummaryConfig> FastS_VsmsummaryHandle;

}

