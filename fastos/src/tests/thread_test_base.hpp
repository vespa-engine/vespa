// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <thread>

static volatile int64_t number;
#define INCREASE_NUMBER_AMOUNT 10000

using namespace std::chrono_literals;

class ThreadTestBase : public BaseTest, public FastOS_Runnable
{
private:
   std::mutex printMutex;

public:
   ThreadTestBase(void)
     : printMutex()
   {
   }
   virtual ~ThreadTestBase() {}

   void PrintProgress (char *string) override {
      std::lock_guard<std::mutex> guard(printMutex);
      BaseTest::PrintProgress(string);
   }

   void Run (FastOS_ThreadInterface *thread, void *arg) override;

   void WaitForThreadsToFinish (Job *jobs, int count) {
      int i;

      Progress(true, "Waiting for threads to finish...");
      for(;;) {
         bool threadsFinished=true;

         for (i=0; i<count; i++) {
            if (jobs[i].result == -1) {
               threadsFinished = false;
               break;
            }
         }

         std::this_thread::sleep_for(1us);

         if(threadsFinished)
            break;
      }

      Progress(true, "Threads finished");
   }
};


void ThreadTestBase::Run (FastOS_ThreadInterface *thread, void *arg)
{
   if(arg == nullptr)
      return;

   Job *job = static_cast<Job *>(arg);
   char someStack[15*1024];

   memset(someStack, 0, 15*1024);

   switch(job->code)
   {
      case SILENTNOP:
      {
         job->result = 1;
         break;
      }

      case NOP:
      {
         Progress(true, "Doing NOP");
         job->result = 1;
         break;
      }

      case PRINT_MESSAGE_AND_WAIT3MSEC:
      {
         Progress(true, "Thread printing message: [%s]", job->message);
         job->result = strlen(job->message);

          std::this_thread::sleep_for(3ms);
         break;
      }

      case INCREASE_NUMBER:
      {
         int result;

         std::unique_lock<std::mutex> guard;
         if(job->mutex != nullptr) {
             guard = std::unique_lock<std::mutex>(*job->mutex);
         }

         result = static_cast<int>(number);

         int sleepOn = (INCREASE_NUMBER_AMOUNT/2) * 321/10000;
         for (int i=0; i<(INCREASE_NUMBER_AMOUNT/2); i++) {
            number = number + 2;

            if (i == sleepOn)
                std::this_thread::sleep_for(1ms);
         }

         guard = std::unique_lock<std::mutex>();

         job->result = result;  // This marks the end of the thread

         break;
      }

      case WAIT_FOR_BREAK_FLAG:
      {
         for(;;) {
             std::this_thread::sleep_for(1us);

            if (thread->GetBreakFlag()) {
               Progress(true, "Thread %p got breakflag", thread);
               break;
            }
         }
         break;
      }

      case WAIT_FOR_THREAD_TO_FINISH:
      {
          std::unique_lock<std::mutex> guard;
          if (job->mutex != nullptr) {
              guard = std::unique_lock<std::mutex>(*job->mutex);
          }

         if (job->otherThread != nullptr)
             job->otherThread->Join();

         break;
      }

      case TEST_ID:
      {
         job->mutex->lock();          // Initially the parent threads owns the lock
         job->mutex->unlock();        // It is unlocked when we should start

         FastOS_ThreadId currentId = FastOS_Thread::GetCurrentThreadId();

         if(currentId == job->_threadId)
            job->result = 1;
         else
            job->result = -1;
         break;
      }

      default:
         Progress(false, "Unknown jobcode");
         break;
   }
}
