// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"

using namespace std::chrono;

class Thread_Bounce_Test : public ThreadTestBase
{
   int Main () override;

   void BounceTest(void)
   {
      TestHeader("Bounce Test");

     FastOS_ThreadPool pool(128 * 1024);
     std::mutex mutex1;
     std::condition_variable cond1;
     std::mutex mutex2;
     std::condition_variable cond2;
     Job job1;
     Job job2;
     int cnt1;
     int cnt2;
     int cntsum;
     int lastcntsum;

     job1.code = BOUNCE_CONDITIONS;
     job2.code = BOUNCE_CONDITIONS;
     job1.otherjob = &job2;
     job2.otherjob = &job1;
     job1.mutex = &mutex1;
     job1.condition = &cond1;
     job2.mutex = &mutex2;
     job2.condition = &cond2;

     job1.ownThread = pool.NewThread(this, static_cast<void *>(&job1));
     job2.ownThread = pool.NewThread(this, static_cast<void *>(&job2));

     lastcntsum = -1;
     for (int iter = 0; iter < 8; iter++) {
       steady_clock::time_point start = steady_clock::now();

       nanoseconds left = steady_clock::now() - start;
       while (left < 1000ms) {
         std::this_thread::sleep_for(1000ms - left);
         left = steady_clock::now() - start;
       }

       mutex1.lock();
       cnt1 = job1.bouncewakeupcnt;
       mutex1.unlock();
       mutex2.lock();
       cnt2 = job2.bouncewakeupcnt;
       mutex2.unlock();
       cntsum = cnt1 + cnt2;
       Progress(lastcntsum != cntsum, "%d bounces", cntsum);
       lastcntsum = cntsum;
     }

     job1.ownThread->SetBreakFlag();
     mutex1.lock();
     job1.bouncewakeup = true;
     cond1.notify_one();
     mutex1.unlock();

     job2.ownThread->SetBreakFlag();
     mutex2.lock();
     job2.bouncewakeup = true;
     cond2.notify_one();
     mutex2.unlock();

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
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
