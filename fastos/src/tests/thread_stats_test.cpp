// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"

class Thread_Stats_Test : public ThreadTestBase
{
   void ThreadStatsTest ()
   {
      int inactiveThreads;
      int activeThreads;
      int startedThreads;

      TestHeader("Thread Statistics Test");

      FastOS_ThreadPool pool(128*1024);
      Job job[2];

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Initial inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Initial active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 0, "Initial started threads = %d", startedThreads);

      job[0].code = WAIT_FOR_BREAK_FLAG;
      job[0].ownThread = pool.NewThread(this, static_cast<void *>(&job[0]));

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 1, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 1, "Started threads = %d", startedThreads);

      job[1].code = WAIT_FOR_BREAK_FLAG;
      job[1].ownThread = pool.NewThread(this, static_cast<void *>(&job[1]));

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 2, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 2, "Started threads = %d", startedThreads);

      Progress(true, "Setting breakflag on threads...");
      job[0].ownThread->SetBreakFlag();
      job[1].ownThread->SetBreakFlag();

      job[0].ownThread->Join();
      job[1].ownThread->Join();
      while (pool.GetNumInactiveThreads() != 2) {
          std::this_thread::sleep_for(1ms);
      }

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 2, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 2, "Started threads = %d", startedThreads);

      Progress(true, "Repeating process in the same pool...");

      job[0].code = WAIT_FOR_BREAK_FLAG;
      job[0].ownThread = pool.NewThread(this, static_cast<void *>(&job[0]));

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 1, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 1, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 3, "Started threads = %d", startedThreads);

      job[1].code = WAIT_FOR_BREAK_FLAG;
      job[1].ownThread = pool.NewThread(this, static_cast<void *>(&job[1]));

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 0, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 2, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 4, "Started threads = %d", startedThreads);

      Progress(true, "Setting breakflag on threads...");
      job[0].ownThread->SetBreakFlag();
      job[1].ownThread->SetBreakFlag();

      job[0].ownThread->Join();
      job[1].ownThread->Join();
      while (pool.GetNumInactiveThreads() != 2) {
          std::this_thread::sleep_for(1ms);
      }

      inactiveThreads = pool.GetNumInactiveThreads();
      Progress(inactiveThreads == 2, "Inactive threads = %d", inactiveThreads);
      activeThreads = pool.GetNumActiveThreads();
      Progress(activeThreads == 0, "Active threads = %d", activeThreads);
      startedThreads = pool.GetNumStartedThreads();
      Progress(startedThreads == 4, "Started threads = %d", startedThreads);

      pool.Close();
      Progress(true, "Pool closed.");

      PrintSeparator();
   }

   int Main () override;
};

int Thread_Stats_Test::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   ThreadStatsTest();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   Thread_Stats_Test app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
