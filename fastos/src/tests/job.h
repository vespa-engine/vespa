// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <condition_variable>

enum JobCode
{
   PRINT_MESSAGE_AND_WAIT3MSEC,
   INCREASE_NUMBER,
   WAIT_FOR_BREAK_FLAG,
   WAIT_FOR_THREAD_TO_FINISH,
   WAIT_FOR_CONDITION,
   TEST_ID,
   WAIT2SEC_AND_SIGNALCOND,
   SILENTNOP,
   NOP
};

class Job
{
private:
   Job(const Job &);
   Job &operator=(const Job&);

public:
   JobCode code;
   char *message;
   std::mutex *mutex;
   std::condition_variable *condition;
   FastOS_ThreadInterface *otherThread, *ownThread;
   double average;
   int result;
   FastOS_ThreadId _threadId;
   Job *otherjob;
   int bouncewakeupcnt;
   bool bouncewakeup;

   Job()
     : code(NOP),
       message(nullptr),
       mutex(nullptr),
       condition(nullptr),
       otherThread(nullptr),
       ownThread(nullptr),
       average(0.0),
       result(-1),
       _threadId(),
       otherjob(nullptr),
       bouncewakeupcnt(0),
       bouncewakeup(false)
   {
   }

   ~Job()
   {
      if(message != nullptr)
         free(message);
   }
};
