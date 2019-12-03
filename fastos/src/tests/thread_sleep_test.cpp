// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include "job.h"
#include "thread_test_base.hpp"

class Thread_Sleep_Test : public ThreadTestBase
{
   int Main () override;

   void CreateSingleThread ()
   {
      TestHeader("Create Single Thread Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024);

      if(Progress(pool != nullptr, "Allocating ThreadPool"))
      {
         bool rc = (nullptr != pool->NewThread(this, nullptr));
         Progress(rc, "Creating Thread");

         Progress(true, "Sleeping 3 seconds");
         std::this_thread::sleep_for(3s);
      }

      Progress(true, "Closing threadpool...");
      pool->Close();

      Progress(true, "Deleting threadpool...");
      delete(pool);
      PrintSeparator();
   }
};

int Thread_Sleep_Test::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   CreateSingleThread();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);
   return allWasOk() ? 0 : 1;
}

int main (int argc, char **argv)
{
   Thread_Sleep_Test app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
