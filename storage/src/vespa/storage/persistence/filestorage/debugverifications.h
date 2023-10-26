// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::DebugVerifications
 * @ingroup filestorage
 *
 * @brief Class containing some parameters for debug verifications.
 *
 * The persistence layer has config for what to verify as a bitmask.
 * This class is a simple helper class defining an enum such that we
 * can refer to the various parts with names instead of numbers.
 */

#pragma once

namespace storage {

struct DebugVerifications
{
    enum Types {
        SLOTFILE_INTEGRITY_AFTER_PUT            = 0x001,
        SLOTFILE_INTEGRITY_AFTER_UPDATE         = 0x002,
        SLOTFILE_INTEGRITY_AFTER_COMPACT        = 0x004,
        SLOTFILE_INTEGRITY_AFTER_MERGE          = 0x008,
        SLOTFILE_INTEGRITY_AFTER_REMOVE         = 0x010,
        SLOTFILE_INTEGRITY_AFTER_REVERT         = 0x020,
        SLOTFILE_INTEGRITY_AFTER_MULTIOP        = 0x040,
        SLOTFILE_INTEGRITY_AFTER_REMOVEALL      = 0x080,
        SLOTFILE_INTEGRITY_AFTER_JOIN           = 0x100,
        SLOTFILE_INTEGRITY_AFTER_SPLIT          = 0x200,
        SLOTFILE_INTEGRITY_AFTER_REMOVELOCATION = 0x400,
        FILESTORTHREAD_DISK_MATCHES_BUCKETDB    = 0x800
    };
};

} // storage

