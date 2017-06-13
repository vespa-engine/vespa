// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"
#include <vespa/fastos/time.h>

class Thread_Bounce_Test : public ThreadTestBase
{
   int Main () override;

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

int Thread_Bounce_Test::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   BounceTest();

   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }
   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   Thread_Bounce_Test app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
