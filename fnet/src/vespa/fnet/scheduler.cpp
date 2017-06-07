// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduler.h"
#include "task.h"
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.scheduler");


FNET_Scheduler::FNET_Scheduler(FastOS_Time *sampler,
                               FastOS_Time *now)
    : _cond(),
      _next(),
      _now(),
      _sampler(sampler),
      _currIter(0),
      _currSlot(0),
      _currPt(nullptr),
      _tailPt(nullptr),
      _performing(nullptr),
      _waitTask(false)
{
    for (int i = 0; i < NUM_SLOTS; i++)
        _slots[i] = nullptr;
    _slots[NUM_SLOTS] = nullptr;

    if (now != nullptr) {
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
            if (pt != nullptr) {
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
        if (pt != nullptr) {
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
    if (_sampler != nullptr) {
        _now = *_sampler;
    } else {
        _now.SetNow();
    }

    // assume timely value propagation

    if (_slots[NUM_SLOTS] == nullptr && _now < _next)
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

void
FNET_Scheduler::FirstTask(uint32_t slot) {
    _currPt = _slots[slot];
    _tailPt = (_currPt != nullptr) ?
              _currPt->_task_prev : nullptr;
}

void
FNET_Scheduler::NextTask() {
    _currPt = (_currPt != _tailPt) ?
              _currPt->_task_next : nullptr;
}

void
FNET_Scheduler::AdjustCurrPt() {
    _currPt = (_currPt != _tailPt) ?
              _currPt->_task_next : nullptr;
}

void
FNET_Scheduler::AdjustTailPt() {
    _tailPt = _tailPt->_task_prev;
}

void
FNET_Scheduler::LinkIn(FNET_Task *task) {
    FNET_Task **head = &(_slots[task->_task_slot]);

    if ((*head) == nullptr) {
        (*head) = task;
        task->_task_next = task;
        task->_task_prev = task;
    } else {
        task->_task_next = (*head);
        task->_task_prev = (*head)->_task_prev;
        (*head)->_task_prev->_task_next = task;
        (*head)->_task_prev = task;
    }
}

void
FNET_Scheduler::LinkOut(FNET_Task *task) {
    FNET_Task **head = &(_slots[task->_task_slot]);

    if (task == _currPt)
        AdjustCurrPt();
    else if (task == _tailPt)
        AdjustTailPt();

    if (task->_task_next == task) {
        (*head) = nullptr;
    } else {
        task->_task_prev->_task_next = task->_task_next;
        task->_task_next->_task_prev = task->_task_prev;
        if ((*head) == task)
            (*head) = task->_task_next;
    }
    task->_task_next = nullptr;
    task->_task_prev = nullptr;
}

void
FNET_Scheduler::BeforeTask(FNET_Task *task) {
    _performing = task;
    Unlock();
}

void
FNET_Scheduler::AfterTask() {
    Lock();
    _performing = nullptr;
    if (_waitTask) {
        _waitTask = false;
        Broadcast();
    }
}

void
FNET_Scheduler::WaitTask(FNET_Task *task) {
    while (IsPerforming(task)) {
        _waitTask = true;
        Wait();
    }
}

void
FNET_Scheduler::PerformTasks(uint32_t slot, uint32_t iter) {
    FirstTask(slot);
    for (FNET_Task *task; (task = GetTask()) != nullptr; ) {
        NextTask();

        if (task->_task_iter == iter) {
            LinkOut(task);
            BeforeTask(task);
            task->PerformTask(); // PERFORM TASK
            AfterTask();
        }
    }
}

bool FNET_Scheduler::IsActive(FNET_Task *task) {
    return task->_task_next != nullptr;
}