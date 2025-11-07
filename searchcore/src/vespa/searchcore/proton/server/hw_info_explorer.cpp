// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hw_info_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/hwaccelerated/fn_table.h>

namespace proton {

HwInfoExplorer::HwInfoExplorer(const vespalib::HwInfo& info)
    : _info(info)
{
}

namespace {

void dump_vectorization_fn_table(vespalib::slime::Cursor& out) {
    using namespace vespalib::hwaccelerated::dispatch;
    const auto& tbl = active_fn_table();
    tbl.for_each_present_fn([&](FnTable::FnId fn_id) {
        auto& info_cursor = out.setObject(FnTable::id_to_fn_name(fn_id));
        const auto& target_info = tbl.fn_target_info(fn_id);
        info_cursor.setString("impl",    target_info.implementation_name());
        info_cursor.setString("target",  target_info.target_name());
        info_cursor.setLong("bit_width", target_info.vector_width_bits());
    });
}

} // anon ns

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

        // Since we dynamically compose a vectorization function table at process startup,
        // it's useful to be able to see what's actually being used to power these calls.
        auto& vec_fn_table = object.setObject("vectorization_fn_table");
        dump_vectorization_fn_table(vec_fn_table);
    }
}

}
