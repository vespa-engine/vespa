// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the hardware information on the machine on which proton runs.
 */
class HwInfoExplorer : public vespalib::StateExplorer
{
private:
    HwInfo _info;

public:
    HwInfoExplorer(const HwInfo& info);

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
