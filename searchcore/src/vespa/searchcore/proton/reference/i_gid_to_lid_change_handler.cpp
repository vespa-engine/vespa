// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_gid_to_lid_change_handler.h"
#include <vespa/document/base/globalid.h>
namespace proton {

IGidToLidChangeHandler::~IGidToLidChangeHandler() = default;

void
IGidToLidChangeHandler::notifyRemove(IDestructorCallbackSP context, GlobalId gid, SerialNum serialNum) {
    std::vector<GlobalId> gids;
    gids.push_back(gid);
    notifyRemoves(std::move(context), gids, serialNum);
}

}
