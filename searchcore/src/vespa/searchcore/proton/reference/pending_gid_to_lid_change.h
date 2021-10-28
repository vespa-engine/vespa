// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/*
 * Class for a gid to lid change awaiting a force commit.
 */
class PendingGidToLidChange
{
    using Context = std::shared_ptr<vespalib::IDestructorCallback>;
    using GlobalId = document::GlobalId;
    using SerialNum = search::SerialNum;

    Context   _context;
    SerialNum _serial_num;
    GlobalId  _gid;
    uint32_t  _lid;
    bool      _is_remove;
public:
    PendingGidToLidChange(Context context, const GlobalId& gid, uint32_t lid, SerialNum serial_num, bool is_remove_) noexcept
        : _context(std::move(context)),
          _serial_num(serial_num),
          _gid(gid),
          _lid(lid),
          _is_remove(is_remove_)
    { }
    PendingGidToLidChange(PendingGidToLidChange &&) noexcept = default;
    PendingGidToLidChange & operator=(PendingGidToLidChange &&) noexcept = default;
    PendingGidToLidChange(const PendingGidToLidChange &) = delete;
    PendingGidToLidChange & operator=(const PendingGidToLidChange &) = delete;
    ~PendingGidToLidChange() = default;

    Context steal_context() && { return std::move(_context); }
    const GlobalId &get_gid() const { return _gid; }
    uint32_t get_lid() const { return _lid; }
    SerialNum get_serial_num() const { return _serial_num; }
    bool is_remove() const { return _is_remove; }
};

}
