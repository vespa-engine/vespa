// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

#include <vespa/fastos/fastos.h>
#include "tests.h"
#include "jobs.h"
#include "base_thread.hpp"

class ThreadTest : public BaseForThreadTest
{
   int Main ();

   void CreateSingleThread ()
   {
      TestHeader("Create Single Thread Test");

      FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024);

      if(Progress(pool != NULL, "Allocating ThreadPool"))
      {
         bool rc = (NULL != pool->NewThread(this, NULL));
         Progress(rc, "Creating Thread");

         Progress(true, "Sleeping 3 seconds");
         FastOS_Thread::Sleep(3000);
      }

      Progress(true, "Closing threadpool...");
      pool->Close();

      Progress(true, "Deleting threadpool...");
      delete(pool);
      PrintSeparator();
   }
};

int ThreadTest::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);
   time_t before = time(0);

   CreateSingleThread();
   { time_t now = time(0); printf("[%ld seconds]\n", now-before); before = now; }

   printf("END OF TEST (%s)\n", _argv[0]);

   return 0;
}

int main (int argc, char **argv)
{
   ThreadTest app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
