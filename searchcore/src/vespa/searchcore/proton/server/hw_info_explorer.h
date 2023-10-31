// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>
#include <vespa/vespalib/util/hw_info.h>

namespace proton {

/**
 * Class used to explore the hardware information on the machine on which proton runs.
 */
class HwInfoExplorer : public vespalib::StateExplorer
{
private:
    vespalib::HwInfo _info;

public:
    HwInfoExplorer(const vespalib::HwInfo& info);

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
