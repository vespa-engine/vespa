// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduler.h"

#include "task.h"

#include <cmath>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.scheduler");

const vespalib::duration FNET_Scheduler::tick_ms = vespalib::adjustTimeoutByDetectedHz(10ms);

FNET_Scheduler::FNET_Scheduler(vespalib::steady_time* sampler)
    : _cond(),
      _next(),
      _now(),
      _sampler(sampler),
      _currIter(0),
      _currSlot(0),
      _currPt(nullptr),
      _tailPt(nullptr),
      _performing(nullptr),
      _waitTask(false) {
    for (int i = 0; i < NUM_SLOTS; i++)
        _slots[i] = nullptr;
    _slots[NUM_SLOTS] = nullptr;
    _now = _sampler ? *_sampler : vespalib::steady_clock::now();
    _next = _now + tick_ms;
}

FNET_Scheduler::~FNET_Scheduler() {
    if (LOG_WOULD_LOG(debug)) {
        bool              empty = true;
        std::stringstream dump;
        {
            std::lock_guard<std::mutex> guard(_lock);
            dump << "FNET_Scheduler {" << std::endl;
            dump << "  [slot=" << _currSlot << "][iter=" << _currIter << "]" << std::endl;
            for (int i = 0; i <= NUM_SLOTS; i++) {
                FNET_Task* pt = _slots[i];
                if (pt != nullptr) {
                    empty = false;
                    FNET_Task* end = pt;
                    do {
                        dump << "  FNET_Task { slot=" << pt->_task_slot;
                        dump << ", iter=" << pt->_task_iter << " }" << std::endl;
                        pt = pt->_task_next;
                    } while (pt != end);
                }
            }
            dump << "}" << std::endl;
        }
        if (!empty) {
            LOG(debug,
                "~FNET_Scheduler(): tasks still pending when deleted"
                "\n%s",
                dump.str().c_str());
        }
    }
}

void FNET_Scheduler::Schedule(FNET_Task* task, double seconds) {
    constexpr double ONE_MONTH_S = 3600 * 24 * 30;
    seconds = std::min(seconds, ONE_MONTH_S);
    uint32_t ticks = 2 + (uint32_t)std::ceil(seconds * (1000.0 / vespalib::count_ms(tick_ms)));

    std::lock_guard<std::mutex> guard(_lock);
    if (!task->_killed) {
        if (IsActive(task))
            LinkOut(task);
        task->_task_slot = (ticks + _currSlot) & SLOTS_MASK;
        task->_task_iter = _currIter + ((ticks + _currSlot) >> SLOTS_SHIFT);
        LinkIn(task);
    }
}

void FNET_Scheduler::ScheduleNow(FNET_Task* task) {
    std::lock_guard<std::mutex> guard(_lock);
    if (!task->_killed) {
        if (IsActive(task))
            LinkOut(task);
        task->_task_slot = NUM_SLOTS;
        task->_task_iter = 0;
        LinkIn(task);
    }
}

void FNET_Scheduler::Unschedule(FNET_Task* task) {
    std::unique_lock<std::mutex> guard(_lock);
    WaitTask(guard, task);
    if (IsActive(task))
        LinkOut(task);
}

void FNET_Scheduler::Kill(FNET_Task* task) {
    std::unique_lock<std::mutex> guard(_lock);
    WaitTask(guard, task);
    if (IsActive(task))
        LinkOut(task);
    task->_killed = true;
}

void FNET_Scheduler::Print(FILE* dst) {
    std::lock_guard<std::mutex> guard(_lock);
    fprintf(dst, "FNET_Scheduler {\n");
    fprintf(dst, "  [slot=%d][iter=%d]\n", _currSlot, _currIter);
    for (int i = 0; i <= NUM_SLOTS; i++) {
        FNET_Task* pt = _slots[i];
        if (pt != nullptr) {
            FNET_Task* end = pt;
            do {
                fprintf(dst, "  FNET_Task { slot=%d, iter=%d }\n", pt->_task_slot, pt->_task_iter);
                pt = pt->_task_next;
            } while (pt != end);
        }
    }
    fprintf(dst, "}\n");
}

void FNET_Scheduler::CheckTasks() {
    std::unique_lock guard(_lock);
    _now = _sampler ? *_sampler : vespalib::steady_clock::now();
    // perform urgent tasks

    PerformTasks(guard, NUM_SLOTS, 0);

    // handle bucket timeout(s)

    for (int i = 0; _now >= _next; ++i, _next += tick_ms) {
        if (i < 25) {
            if (++_currSlot >= NUM_SLOTS) {
                _currSlot = 0;
                _currIter++;
            }
            PerformTasks(guard, _currSlot, _currIter);
        }
    }
}

void FNET_Scheduler::FirstTask(uint32_t slot) {
    _currPt = _slots[slot];
    _tailPt = (_currPt != nullptr) ? _currPt->_task_prev : nullptr;
}

void FNET_Scheduler::NextTask() { _currPt = (_currPt != _tailPt) ? _currPt->_task_next : nullptr; }

void FNET_Scheduler::AdjustCurrPt() { _currPt = (_currPt != _tailPt) ? _currPt->_task_next : nullptr; }

void FNET_Scheduler::AdjustTailPt() { _tailPt = _tailPt->_task_prev; }

void FNET_Scheduler::LinkIn(FNET_Task* task) {
    FNET_Task** head = &(_slots[task->_task_slot]);

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

void FNET_Scheduler::LinkOut(FNET_Task* task) {
    FNET_Task** head = &(_slots[task->_task_slot]);

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

void FNET_Scheduler::BeforeTask(std::unique_lock<std::mutex>& guard, FNET_Task* task) {
    _performing = task;
    guard.unlock();
}

void FNET_Scheduler::AfterTask(std::unique_lock<std::mutex>& guard) {
    guard.lock();
    _performing = nullptr;
    if (_waitTask) {
        _waitTask = false;
        _cond.notify_all();
    }
}

void FNET_Scheduler::WaitTask(std::unique_lock<std::mutex>& guard, FNET_Task* task) {
    while (IsPerforming(task)) {
        _waitTask = true;
        _cond.wait(guard);
    }
}

void FNET_Scheduler::PerformTasks(std::unique_lock<std::mutex>& guard, uint32_t slot, uint32_t iter) {
    FirstTask(slot);
    for (FNET_Task* task; (task = GetTask()) != nullptr;) {
        NextTask();

        if (task->_task_iter == iter) {
            LinkOut(task);
            BeforeTask(guard, task);
            task->PerformTask(); // PERFORM TASK
            AfterTask(guard);
        }
    }
}

bool FNET_Scheduler::IsActive(FNET_Task* task) { return task->_task_next != nullptr; }
