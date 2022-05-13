// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hw_info_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>

namespace proton {

HwInfoExplorer::HwInfoExplorer(const HwInfo& info)
    : _info(info)
{
}

void
HwInfoExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    if (full) {
        auto& disk = object.setObject("disk");
        disk.setLong("size_bytes", _info.disk().sizeBytes());
        disk.setBool("slow", _info.disk().slow());
        disk.setBool("shared", _info.disk().shared());

        auto& memory = object.setObject("memory");
        memory.setLong("size_bytes", _info.memory().sizeBytes());

        auto& cpu = object.setObject("cpu");
        cpu.setLong("cores", _info.cpu().cores());
    }
}

}
