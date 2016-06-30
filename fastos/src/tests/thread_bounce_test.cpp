// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

#include <vespa/fastos/fastos.h>
#include "tests.h"
#include "jobs.h"

static volatile int64_t number;
#define INCREASE_NUMBER_AMOUNT 10000

#define MUTEX_TEST_THREADS 6
#define MAX_THREADS 7


class ThreadTest : public BaseTest, public FastOS_Runnable
{
private:
   FastOS_Mutex printMutex;

public:
   ThreadTest(void)
     : printMutex()
   {
   }
   virtual ~ThreadTest() {};

   void PrintProgress (char *string)
   {
      printMutex.Lock();
      BaseTest::PrintProgress(string);
      printMutex.Unlock();
   }

   void Run (FastOS_ThreadInterface *thread, void *arg);

   void WaitForThreadsToFinish (Job *jobs, int count)
   {
      int i;

      Progress(true, "Waiting for threads to finish...");
      for(;;)
      {
         bool threadsFinished=true;

         for(i=0; i<count; i++)
         {
            if(jobs[i].result == -1)
            {
               threadsFinished = false;
               break;
            }
         }

         FastOS_Thread::Sleep(500);

         if(threadsFinished)
            break;
      }

      Progress(true, "Threads finished");
   }

   int Main ();

   void BounceTest(void)
   {
      TestHeader("Bounce Test");

     FastOS_ThreadPool pool(128 * 1024);
     FastOS_Cond cond1;
     FastOS_Cond cond2;
     Job job1;
     Job job2;
     FastOS_Time checkTime;
     int cnt1;
     int cnt2;
     int cntsum;
     int lastcntsum;

     job1.code = BOUNCE_CONDITIONS;
     job2.code = BOUNCE_CONDITIONS;
     job1.otherjob = &job2;
     job2.otherjob = &job1;
     job1.condition = &cond1;
     job2.condition = &cond2;

     job1.ownThread = pool.NewThread(this, static_cast<void *>(&job1));
     job2.ownThread = pool.NewThread(this, static_cast<void *>(&job2));

     lastcntsum = -1;
     for (int iter = 0; iter < 8; iter++) {
       checkTime.SetNow();

       int left = static_cast<int>(checkTime.MilliSecsToNow());
       while (left < 1000) {
         FastOS_Thread::Sleep(1000 - left);
         left = static_cast<int>(checkTime.MilliSecsToNow());
       }

       cond1.Lock();
       cnt1 = job1.bouncewakeupcnt;
       cond1.Unlock();
       cond2.Lock();
       cnt2 = job2.bouncewakeupcnt;
       cond2.Unlock();
       cntsum = cnt1 + cnt2;
       Progress(lastcntsum != cntsum, "%d bounces", cntsum);
       lastcntsum = cntsum;
     }

     job1.ownThread->SetBreakFlag();
     cond1.Lock();
     job1.bouncewakeup = true;
     cond1.Signal();
     cond1.Unlock();

     job2.ownThread->SetBreakFlag();
     cond2.Lock();
     job2.bouncewakeup = true;
     cond2.Signal();
     cond2.Unlock();

     pool.Close();
     Progress(true, "Pool closed.");
     PrintSeparator();

   }

};

int ThreadTest::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   BounceTest();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);

   return 0;
}

volatile int busyCnt;

void ThreadTest::Run (FastOS_ThreadInterface *thread, void *arg)
{
   if(arg == NULL)
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

      case PRINT_MESSAGE_AND_WAIT3SEC:
      {
         Progress(true, "Thread printing message: [%s]", job->message);
         job->result = strlen(job->message);

         FastOS_Thread::Sleep(3000);
         break;
      }

      case INCREASE_NUMBER:
      {
         int result;

         if(job->mutex != NULL)
            job->mutex->Lock();

         result = static_cast<int>(number);

         int sleepOn = (INCREASE_NUMBER_AMOUNT/2) * 321/10000;
         for(int i=0; i<(INCREASE_NUMBER_AMOUNT/2); i++)
         {
            number = number + 2;

            if(i == sleepOn)
               FastOS_Thread::Sleep(1000);
         }

         if(job->mutex != NULL)
            job->mutex->Unlock();

         job->result = result;  // This marks the end of the thread

         break;
      }

      case WAIT_FOR_BREAK_FLAG:
      {
         for(;;)
         {
            FastOS_Thread::Sleep(1000);

            if(thread->GetBreakFlag())
            {
               Progress(true, "Thread %p got breakflag", thread);
               break;
            }
         }
         break;
      }

      case WAIT_FOR_THREAD_TO_FINISH:
      {
         if(job->mutex)
            job->mutex->Lock();

         if(job->otherThread != NULL)
            job->otherThread->Join();

         if(job->mutex)
            job->mutex->Unlock();
         break;
      }

      case WAIT_FOR_CONDITION:
      {
         job->condition->Lock();

         job->result = 1;

         job->condition->Wait();
         job->condition->Unlock();

         job->result = 0;

         break;
      }

      case BOUNCE_CONDITIONS:
      {
        while (!thread->GetBreakFlag()) {
          job->otherjob->condition->Lock();
          job->otherjob->bouncewakeupcnt++;
          job->otherjob->bouncewakeup = true;
          job->otherjob->condition->Signal();
          job->otherjob->condition->Unlock();

          job->condition->Lock();
          while (!job->bouncewakeup)
            job->condition->TimedWait(1);
          job->bouncewakeup = false;
          job->condition->Unlock();
        }
        break;
      }

      case TEST_ID:
      {
         job->mutex->Lock();          // Initially the parent threads owns the lock
         job->mutex->Unlock();        // It is unlocked when we should start

         FastOS_ThreadId currentId = FastOS_Thread::GetCurrentThreadId();

         if(currentId == job->_threadId)
            job->result = 1;
         else
            job->result = -1;
         break;
      }

      case WAIT2SEC_AND_SIGNALCOND:
      {
         FastOS_Thread::Sleep(2000);
         job->condition->Signal();
         job->result = 1;
         break;
      }

      case HOLD_MUTEX_FOR2SEC:
      {
         job->mutex->Lock();
         FastOS_Thread::Sleep(2000);
         job->mutex->Unlock();
         job->result = 1;
         break;
      }

      case WAIT_2_SEC:
      {
         FastOS_Thread::Sleep(2000);
         job->result = 1;
         break;
      }

      default:
         Progress(false, "Unknown jobcode");
         break;
   }
}

int main (int argc, char **argv)
{
   ThreadTest app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
