// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <condition_variable>

enum JobCode
{
   PRINT_MESSAGE_AND_WAIT3MSEC,
   INCREASE_NUMBER,
   WAIT_FOR_BREAK_FLAG,
   WAIT_FOR_THREAD_TO_FINISH,
   TEST_ID,
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
   std::atomic<int> result;
   FastOS_ThreadId _threadId;

   Job()
     : code(NOP),
       message(nullptr),
       mutex(nullptr),
       condition(nullptr),
       otherThread(nullptr),
       ownThread(nullptr),
       result(-1),
       _threadId()
   {
   }

   ~Job()
   {
      if(message != nullptr)
         free(message);
   }
};
