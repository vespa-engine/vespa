// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/time.h>
#include <cmath>

using namespace fastos;

class TimeTest : public BaseTest
{
private:

public:
   virtual ~TimeTest() {};

   void Wait3SecondsTest ()
   {
      TestHeader("Wait 3 seconds test");
      bool rc=false;
      FastOS_Time before, timing;

      Progress(true, "Waiting 3 seconds...");
      before.SetNow();

      FastOS_Thread::Sleep(3000);

      timing.SetNow();

      rc = (timing > before);
      Progress(rc, "AfterTime > BeforeTime");

      rc = (before < timing);
      Progress(rc, "BeforeTime < AfterTime");

      rc = (before <= timing);
      Progress(rc, "BeforeTime <= AfterTime");

      rc = (timing >= before);
      Progress(rc, "AfterTime >= BeforeTime");

      rc = (timing >= timing);
      Progress(rc, "AfterTime >= AfterTime");

      rc = (timing <= timing);
      Progress(rc, "AfterTime <= AfterTime");

      FastOS_Time copyOfAfterTime = timing;
      rc = (copyOfAfterTime == timing);
      Progress(rc, "CopyOfAfterTime == AfterTime");

      timing -= before;

      double milliseconds = timing.MilliSecs();
      rc = ((milliseconds >= 1900) &&
            (milliseconds <= 10000));   // More than 10 seconds??

      Progress(rc, "Waittime = %d milliseconds",
               static_cast<int>(milliseconds));

      double microseconds = timing.MicroSecs();
      rc = ((microseconds >= 1900000) &&
            (microseconds <= 10000000));   // More than 10 seconds??

      Progress(rc, "Waittime = %d microseconds",
               static_cast<int>(microseconds));

      timing += before;
      rc = (copyOfAfterTime == timing);
      Progress(rc, "CopyOfAfterTime == AfterTime (after minus-plus)");

      PrintSeparator();
   }

   void TimeArithmeticTest ()
   {
      bool rc=false;

      TestHeader("Other Time Aritmetic Test");

      FastOS_Time time1, time2;
      time1.SetZero();
      time2.SetZero();

      rc = (time1 == time2);
      Progress(rc, "Two zero times are equal");

      time1.SetMilliSecs(124);
      Progress(long(time1.MilliSecs()) == 124,
               "SetMilliSecs(124) -> MilliSecs(%ld)",
               long(time1.MilliSecs()));

      time1.SetMicroSecs(123000);
      Progress(long(time1.MicroSecs()) == 123000,
               "SetMicroSecs(123000) -> MicroSecs(%ld)",
               long(time1.MicroSecs()));

      time1.SetMilliSecs(999124);
      Progress(long(time1.MilliSecs()) == 999124,
               "SetMilliSecs(999124) -> MilliSecs(%ld)",
               long(time1.MilliSecs()));

      time1.SetMicroSecs(9123000);
      Progress(long(time1.MicroSecs()) == 9123000,
               "SetMicroSecs(9123000) -> MicroSecs(%ld)",
               long(time1.MicroSecs()));

      time2 = time1;
      Progress(long(time2.MicroSecs()) == 9123000,
               "[time2 = time1] -> time2.MicroSecs(%ld)",
               long(time2.MicroSecs()));

      time2 += time1;
      Progress(long(time2.MicroSecs()) == 9123000*2,
               "[time2 += time1] -> time2.MicroSecs(%ld)",
               long(time2.MicroSecs()));

      time2 += time2;
      Progress(long(time2.MicroSecs()) == 9123000*4,
               "[time2 += time2] -> time2.MicroSecs(%ld)",
               long(time2.MicroSecs()));

      time2 -= time1;
      Progress(long(time2.MicroSecs()) == 9123000*3,
               "[time2 -= time2] -> time2.MicroSecs(%ld)",
               long(time2.MicroSecs()));

      Progress(time2 > time1,
               "[time2 > time2] -> %s", time2 > time1 ? "true" : "false");
      Progress(time2 >= time1,
               "[time2 >= time2] -> %s", time2 >= time1 ? "true" : "false");
      Progress(time1 < time2,
               "[time1 < time2] -> %s", time1 < time2 ? "true" : "false");
      Progress(time1 <= time2,
               "[time1 <= time2] -> %s", time1 <= time2 ? "true" : "false");

      Progress(!(time2 < time1),
               "[time2 < time2] -> %s", time2 < time1 ? "true" : "false");
      Progress(!(time2 <= time1),
               "[time2 <= time1] -> %s", time2 <= time1 ? "true" : "false");
      Progress(!(time1 > time2),
               "[time1 > time2] -> %s", time1 > time2 ? "true" : "false");
      Progress(!(time1 >= time2),
               "[time1 >= time2] -> %s", time1 >= time2 ? "true" : "false");
      Progress(!(time1 == time2),
               "[time1 == time2] -> %s", time1 == time2 ? "true" : "false");

      time1 = time2;
      Progress(long(time1.MicroSecs()) == 9123000*3,
               "[time1 = time2] -> time1.MicroSecs(%ld)",
               long(time1.MicroSecs()));
      Progress(time2 >= time1,
               "[time2 >= time2] -> %s", time2 >= time1 ? "true" : "false");
      Progress(time1 <= time2,
               "[time1 <= time2] -> %s", time1 <= time2 ? "true" : "false");
      Progress((time1 == time2),
               "[time1 == time2] -> %s", time1 == time2 ? "true" : "false");

      time1.SetZero();
      Progress(long(time1.MilliSecs()) == 0,
               "SetZero() -> MilliSecs(%ld)",
               long(time1.MilliSecs()));

      Progress(long(time1.MicroSecs()) == 0,
               "SetZero() -> MicroSecs(%ld)",
               long(time1.MicroSecs()));

      time1 = 2.5;
      Progress(long(time1.MilliSecs()) == 2500,
               "time2 = 2.5 -> MilliSecs(%ld)",
               long(time1.MilliSecs()));

      time2 = 3.9;
      Progress(long(time2.MicroSecs()) == 3900000,
               "time2 = 3.9 -> MicroSecs(%ld)",
               long(time2.MicroSecs()));

      time1.SetNow();
      FastOS_Thread::Sleep(1000);
      double waited = time1.MicroSecsToNow();
      Progress((waited >= 950000) && (waited <= 1200000),
               "Slept 1000 ms, MicroSecsToNow(%ld)", long(waited));

      time2.SetNow();
      FastOS_Thread::Sleep(2000);
      waited = time2.MilliSecsToNow();
      Progress((waited >= (2*950)) && (waited <= (2*1200)),
               "Slept 2000 ms, MilliSecsToNow(%ld)", long(waited));

      time2.SetMicroSecs(40000);
      time2.AddMicroSecs(1000000);
      Progress(long(time2.MicroSecs()) == (40000 + 1000000),
               "[SetMicroSecs(40000); AddMicroSecs(1000000)] -> MicroSecs(%ld)",
               long(time2.MicroSecs()));

      time1.SetMicroSecs(9123000);
      time1.SubtractMicroSecs(512000);
      Progress(long(time1.MicroSecs()) == (9123000 - 512000),
               "[SetMicroSecs(9123000); SubMicroSecs(512000)] -> MicroSecs(%ld)",
               long(time1.MicroSecs()));

      time1.SetMilliSecs(400);
      time1.AddMilliSecs(1000001);
      Progress(long(time1.MilliSecs()) == (400 + 1000001),
               "[SetMilliSecs(400); AddMilliSecs(1000001)] -> MilliSecs(%ld)",
               long(time1.MilliSecs()));

      time2.SetMilliSecs(9123213);
      time2.SubtractMilliSecs(512343);
      Progress(long(time2.MilliSecs()) == (9123213 - 512343),
               "[SetMilliSecs(9123213); SubMilliSecs(512343)] -> MilliSecs(%ld)",
               long(time2.MilliSecs()));

      Progress(time2.GetSeconds() == ((9123213 - 512343) / (1000)),
               "[time2.GetSeconds()] -> %ld", time2.GetSeconds());
      Progress(int64_t(time2.GetMicroSeconds()) == ((int64_t(9123213 - 512343)*1000) % (1000000)),
               "[time2.GetMicroSeconds()] -> %ld", time2.GetMicroSeconds());

      PrintSeparator();
   }

   void TimeStepTest ()
   {
      TestHeader("Time Step Test");
      FastOS_Time before, timing;
      before.SetNow();

      int delay = 400;
      for(int i=delay; i<3000; i+=delay)
      {
         FastOS_Thread::Sleep(delay);

         timing.SetNow();
         timing -= before;

         double millis = timing.MilliSecs();
         double correct = i;

         Progress((std::fabs(millis - correct)/correct) < 0.15,
                  "Elapsed time measurement: %d",
                  static_cast<int>(millis));
      }

      PrintSeparator();
   }

   void requireThatTimeStampCanBeConvertedToString() {
      TestHeader("requireThatTimeStampCanBeConvertedToString");

      int64_t time = 1424867106 * fastos::TimeStamp::SEC + 123 * fastos::TimeStamp::MS;
      fastos::TimeStamp timeStamp(time);
      std::string actualString = timeStamp.toString();
      std::string expectString = "2015-02-25 12:25:06.123 UTC";
      Progress(expectString == actualString,
               "Actual string: '%s'", actualString.c_str());

      PrintSeparator();
   }

   void requireThatTimeStampIsConstructedCorrect() {
       using fastos::TimeStamp;
       Progress(TimeStamp(97).ns() == 97l, "TimeStamp(int)");
       Progress(TimeStamp(97u).ns() == 97l, "TimeStamp(unsigned int)");
       Progress(TimeStamp(INT64_C(97)).ns() == 97l, "TimeStamp(int64_t)");
       Progress(TimeStamp(UINT64_C(97)).ns() == 97l, "TimeStamp(uint64_t)");
       Progress(TimeStamp(TimeStamp::Seconds(97.3)).ns() == 97300000000l, "TimeStamp(double)");
       PrintSeparator();
   }


   int Main () override;
};

int TimeTest::Main ()
{
   printf("grep for the string '%s' to detect failures.\n\n", failString);

   Wait3SecondsTest();
   TimeArithmeticTest();
   TimeStepTest();
   requireThatTimeStampCanBeConvertedToString();
   requireThatTimeStampIsConstructedCorrect();

   printf("END OF TEST (%s)\n", _argv[0]);

   return allWasOk() ? 0 : 1;
}


int main (int argc, char **argv)
{
   TimeTest app;

   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}

