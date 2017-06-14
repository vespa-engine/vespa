// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/time.h>

class ThreadRunJob;
void UseSomeCpu(int i, ThreadRunJob *threadRunJob);

class ThreadRunJob : public FastOS_Runnable
{
public:
   int64_t UseSomeCpu2(int64_t someNumber)
   {
      return someNumber + (someNumber/2 + someNumber*4) +
         someNumber * someNumber * someNumber;
   }

   void Run (FastOS_ThreadInterface *thisThread, void *arg) override
   {
      (void)thisThread;
      (void)arg;

      FastOS_Time before, current;
      before.SetNow();

      for(int i=0; i<200000; i++)
      {
         if((i % 200) == 0)
         {
            current.SetNow();
            current -= before;
            if(current.MilliSecs() > 3000)
              break;
         }
         UseSomeCpu(i, this);
      }
      delete (this);
   }
};

class UseCpuTest : public BaseTest
{
public:
   int Main () override
   {
      FastOS_ThreadPool pool(128*1024);
      pool.NewThread(new ThreadRunJob());
      pool.NewThread(new ThreadRunJob());
      pool.NewThread(new ThreadRunJob());
      pool.NewThread(new ThreadRunJob());
      pool.Close();
      return 0;
   }
};

void UseSomeCpu (int i, ThreadRunJob *threadRunJob)
{
   int64_t lastVal = i;
   for(int e=0; e<100; e++)
      lastVal = threadRunJob->UseSomeCpu2(lastVal);
}

int main (int argc, char **argv)
{
   UseCpuTest app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}

