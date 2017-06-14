// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

enum JobCode
{
   PRINT_MESSAGE_AND_WAIT3SEC,
   INCREASE_NUMBER,
   PRIORITY_TEST,
   WAIT_FOR_BREAK_FLAG,
   WAIT_FOR_THREAD_TO_FINISH,
   WAIT_FOR_CONDITION,
   BOUNCE_CONDITIONS,
   TEST_ID,
   WAIT2SEC_AND_SIGNALCOND,
   HOLD_MUTEX_FOR2SEC,
   WAIT_2_SEC,
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
   FastOS_Mutex *mutex;
   FastOS_Cond *condition;
   FastOS_BoolCond *boolcondition;
   FastOS_ThreadInterface *otherThread, *ownThread;
   double *timebuf;
   double average;
   int result;
   FastOS_ThreadId _threadId;
   Job *otherjob;
   int bouncewakeupcnt;
   bool bouncewakeup;

   Job()
     : code(NOP),
       message(NULL),
       mutex(NULL),
       condition(NULL),
       boolcondition(NULL),
       otherThread(NULL),
       ownThread(NULL),
       timebuf(NULL),
       average(0.0),
       result(-1),
       _threadId(),
       otherjob(NULL),
       bouncewakeupcnt(0),
       bouncewakeup(false)
   {
   }

   ~Job()
   {
      if(message != NULL)
         free(message);
   }
};
