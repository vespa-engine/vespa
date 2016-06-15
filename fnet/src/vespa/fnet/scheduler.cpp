// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fnet.scheduler");
#include <vespa/fnet/fnet.h>
#include <string>
#include <sstream>

FNET_Scheduler::FNET_Scheduler(FastOS_Time *sampler,
                               FastOS_Time *now)
    : _cond(),
      _next(),
      _now(),
      _sampler(sampler),
      _currIter(0),
      _currSlot(0),
      _currPt(NULL),
      _tailPt(NULL),
      _performing(NULL),
      _waitTask(false)
{
    for (int i = 0; i < NUM_SLOTS; i++)
        _slots[i] = NULL;
    _slots[NUM_SLOTS] = NULL;

    if (now != NULL) {
        _next = *now;
    } else {
        _next.SetNow();
    }
    _next.AddMilliSecs(SLOT_TICK);
}


FNET_Scheduler::~FNET_Scheduler()
{
    if (LOG_WOULD_LOG(debug)) {
        bool empty = true;
        std::stringstream dump;
        Lock();
        dump << "FNET_Scheduler {" << std::endl;
        dump << "  [slot=" << _currSlot << "][iter=" << _currIter << "]" << std::endl;
        for (int i = 0; i <= NUM_SLOTS; i++) {
            FNET_Task *pt = _slots[i];
            if (pt != NULL) {
                empty = false;
                FNET_Task *end = pt;
                do {
                    dump << "  FNET_Task { slot=" << pt->_task_slot;
                    dump << ", iter=" << pt->_task_iter << " }" << std::endl;
                    pt = pt->_task_next;
                } while (pt != end);
            }
        }
        dump << "}" << std::endl;
        Unlock();
        if (!empty) {
            LOG(debug, "~FNET_Scheduler(): tasks still pending when deleted"
                "\n%s", dump.str().c_str());
        }
    }
}


void
FNET_Scheduler::Schedule(FNET_Task *task, double seconds)
{
    uint32_t ticks = 1 + (uint32_t) (seconds * (1000 / SLOT_TICK) + 0.5);

    Lock();
    if (!task->_killed) {
        if (IsActive(task))
            LinkOut(task);
        task->_task_slot = (ticks + _currSlot) & SLOTS_MASK;
        task->_task_iter = _currIter + ((ticks + _currSlot) >> SLOTS_SHIFT);
        LinkIn(task);
    }
    Unlock();
}


void
FNET_Scheduler::ScheduleNow(FNET_Task *task)
{
    Lock();
    if (!task->_killed) {
        if (IsActive(task))
            LinkOut(task);
        task->_task_slot = NUM_SLOTS;
        task->_task_iter = 0;
        LinkIn(task);
    }
    Unlock();
}


void
FNET_Scheduler::Unschedule(FNET_Task *task)
{
    Lock();
    WaitTask(task);
    if (IsActive(task))
        LinkOut(task);
    Unlock();
}


void
FNET_Scheduler::Kill(FNET_Task *task)
{
    Lock();
    WaitTask(task);
    if (IsActive(task))
        LinkOut(task);
    task->_killed = true;
    Unlock();
}


void
FNET_Scheduler::Print(FILE *dst)
{
    Lock();
    fprintf(dst, "FNET_Scheduler {\n");
    fprintf(dst, "  [slot=%d][iter=%d]\n", _currSlot, _currIter);
    for (int i = 0; i <= NUM_SLOTS; i++) {
        FNET_Task *pt = _slots[i];
        if (pt != NULL) {
            FNET_Task *end = pt;
            do {
                fprintf(dst, "  FNET_Task { slot=%d, iter=%d }\n",
                        pt->_task_slot, pt->_task_iter);
                pt = pt->_task_next;
            } while (pt != end);
        }
    }
    fprintf(dst, "}\n");
    Unlock();
}


void
FNET_Scheduler::CheckTasks()
{
    if (_sampler != NULL) {
        _now = *_sampler;
    } else {
        _now.SetNow();
    }

    // assume timely value propagation

    if (_slots[NUM_SLOTS] == NULL && _now < _next)
        return;

    Lock();

    // perform urgent tasks

    PerformTasks(NUM_SLOTS, 0);

    // handle bucket timeout(s)

    for (int i = 0; _now >= _next; ++i, _next.AddMilliSecs(SLOT_TICK)) {
        if (i < 25) {
            if (++_currSlot >= NUM_SLOTS) {
                _currSlot = 0;
                _currIter++;
            }
            PerformTasks(_currSlot, _currIter);
        }
    }
    Unlock();
}
