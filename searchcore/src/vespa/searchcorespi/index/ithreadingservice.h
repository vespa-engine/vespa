// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_thread_service.h"

class FNET_Transport;

namespace vespalib {
    class ISequencedTaskExecutor;
    class Clock;
}
namespace searchcorespi::index {

/**
 * Interface for the thread model used for write tasks for a single document database.
 *
 * We have multiple write threads:
 *
 *  1. The "master" write thread used for the majority of write tasks.
 *
 *  2. The "index" write thread used for doing changes to the memory
 *     index, either directly (for data not bound to a field) or via
 *     index field inverter executor or index field writer executor.
 *
 *  3. The "summary" thread is used for doing changes to the document store.
 *
 *  4. The "index field inverter" executor is used to populate field
 *     inverters with data from document fields.  Scheduled tasks for
 *     the same field are executed in sequence.
 *
 *  5. The "index field writer" executor is used to sort data in field
 *     inverters before pushing the data to the memory field indexes.
 *     Scheduled tasks for the same field are executed in sequence.
 *
 *  6. The "attribute field writer" executor is used to write data to attribute vectors.
 *     Each attribute is always handled by the same thread,
 *     and scheduled tasks for the same attribute are executed in sequence.
 *
 * The master write thread is always the one giving tasks to the other write threads above.
 *
 * In addition this interface exposes the "shared" executor that is used by all document databases.
 * This is among others used for compressing / de-compressing documents in the document store,
 * merging files as part of disk index fusion, and running the prepare step when doing two-phase
 * puts against a tensor attribute with a HNSW index.
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
 *
 * TODO: * indexFieldInverter and indexFieldWriter can be collapsed to one. Both need sequencing,
 *         but they sequence on different things so efficiency will be the same and just depends on #threads
 */
struct IThreadingService
{
    IThreadingService(const IThreadingService &) = delete;
    IThreadingService & operator = (const IThreadingService &) = delete;
    IThreadingService() = default;
    virtual ~IThreadingService() = default;

    /**
     * Block the calling thread until the master thread has capacity to handle more tasks,
     * and then execute the given task in the master thread.
     */
    virtual void blocking_master_execute(vespalib::Executor::Task::UP task) = 0;

    virtual ISyncableThreadService &master() = 0;
    virtual IThreadService &index() = 0;
    virtual vespalib::ThreadExecutor &summary() = 0;
    virtual vespalib::Executor &shared() = 0;
    virtual FNET_Transport &transport() = 0;
    virtual const vespalib::Clock &clock() const = 0;
    virtual vespalib::ISequencedTaskExecutor &indexFieldInverter() = 0;
    virtual vespalib::ISequencedTaskExecutor &indexFieldWriter() = 0;
    virtual vespalib::ISequencedTaskExecutor &attributeFieldWriter() = 0;
};

}
