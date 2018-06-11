// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_thread_service.h"
#include <vespa/vespalib/util/syncable.h>

namespace search { class ISequencedTaskExecutor; }
namespace searchcorespi::index {

/**
 * Interface for the thread model used for write tasks.
 *
 * We have multiple write threads:
 *
 *  1. The master write thread used for the majority of write tasks.
 *
 *  2. The index write thread used for doing changes to the memory
 *     index, either directly (for data not bound to a field) or via
 *     index field inverter executor or index field writer executor.
 *
 *  3. The index field inverter executor is used to populate field
 *     inverters with data from document fields.  Scheduled tasks for
 *     the same field are executed in sequence.
 *
 *  4. The index field writer executor is used to sort data in field
 *     inverters before pushing the data to the memory field indexes.
 *     Scheduled tasks for the same field are executed in sequence.
 *
 * The master write thread is always the one giving tasks to the index
 * write thread.
 *
 * The index write thread extracts fields from documents and gives
 * task to the index field inverter executor and the index field
 * writer executor.
 *
 * The index field inverter executor and index field writer executor
 * are separate to allow for double buffering, i.e. populate one set
 * of field inverters using the index field inverter executor while
 * another set of field inverters are handled by the index field
 * writer executor.
 *
 * We might decide to allow index field inverter tasks to schedule
 * tasks to the index field writer executor, so draining logic needs
 * to sync index field inverter executor before syncing index field
 * writer executor.
 */
struct IThreadingService : public vespalib::Syncable
{
    IThreadingService(const IThreadingService &) = delete;
    IThreadingService & operator = (const IThreadingService &) = delete;
    IThreadingService() = default;
    virtual ~IThreadingService() {}

    virtual IThreadService &master() = 0;
    virtual IThreadService &index() = 0;
    virtual IThreadService &summary() = 0;
    virtual search::ISequencedTaskExecutor &indexFieldInverter() = 0;
    virtual search::ISequencedTaskExecutor &indexFieldWriter() = 0;
    virtual search::ISequencedTaskExecutor &attributeFieldWriter() = 0;
};

}
