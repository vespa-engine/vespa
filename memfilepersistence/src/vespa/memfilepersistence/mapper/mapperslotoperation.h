// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::MapperSlotOperation
 * \ingroup memfile
 *
 * \brief Utility class to wrap const casting.
 *
 * The MemFile objects wants to track all changes done to them by clients, such
 * that they can track internally whether they are altered in memory from the
 * physical file. Thus, only const MemSlot objects are exposed. If one wants to
 * alter the MemFile slots one has to call functions in MemFile to do it.
 *
 * But the mapper code need to alter some information in the MemFile and MemSlot
 * objects. For instance, it has to clear altered tag after flushing content to
 * disk. The mappers thus need to alter the objects in a way regular clients
 * should not be allowed to.
 *
 * To implement this, we use this class, which contains only the functionality
 * needed by the mappers, and which const cast to let the mapper change the
 * state it needs to.
 */

#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <vespa/memfilepersistence/memfile/memfile.h>

namespace storage {
namespace memfile {

struct MapperSlotOperation : protected Types {
    static void setFlag(const MemFile& file, uint32_t flags)
    {
        const_cast<MemFile&>(file).setFlag(flags);
    }
    static void clearFlag(const MemFile& file, uint32_t flags)
    {
        const_cast<MemFile&>(file).clearFlag(flags);
    }
    static void setFlag(const MemSlot& slot, uint32_t flags)
    {
        const_cast<MemSlot&>(slot).setFlag(flags);
    }
    static void clearFlag(const MemSlot& slot, uint32_t flags)
    {
        const_cast<MemSlot&>(slot).clearFlag(flags);
    }
    static void setLocation(const MemSlot& slot, DocumentPart part,
                            const DataLocation& dl)
    {
        const_cast<MemSlot&>(slot).setLocation(part, dl);
    }
    static void setChecksum(const MemSlot& slot, uint16_t checksum)
    {
        const_cast<MemSlot&>(slot).setChecksum(checksum);
    }
};

} // memfile
} // storage

