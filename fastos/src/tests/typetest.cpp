// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/file.h>
#include <vespa/fastos/serversocket.h>

#include <cstdlib>

class TypeTest : public BaseTest
{
private:
   void PrintfSpecifiersTest ()
   {
      char testBuf[200];
      const char *correct;

      TestHeader("64-bit printf-specifiers test");

      sprintf(testBuf, "%" PRId64, int64_t(-1000)*1000*1000*1000*1000);
      correct = "-1000000000000000";
      Progress(strcmp(testBuf, correct) == 0,
               "Generated=[%s], Correct=[%s]", testBuf, correct);

      sprintf(testBuf, "%" PRIu64, uint64_t(1000)*1000*1000*1000*1000);
      correct = "1000000000000000";
      Progress(strcmp(testBuf, correct) == 0,
               "Generated=[%s], Correct=[%s]", testBuf, correct);

      sprintf(testBuf, "%" PRIo64, uint64_t(1000)*1000*1000*1000*1000);
      correct = "34327724461500000";
      Progress(strcmp(testBuf, correct) == 0,
               "Generated=[%s], Correct=[%s]", testBuf, correct);

      sprintf(testBuf, "%" PRIx64, uint64_t(1000)*1000*1000*1000*1000);
      correct = "38d7ea4c68000";
      Progress(strcmp(testBuf, correct) == 0,
               "Generated=[%s], Correct=[%s]", testBuf, correct);

      sprintf(testBuf, "%" PRIX64, uint64_t(1000)*1000*1000*1000*1000);
      correct = "38D7EA4C68000";
      Progress(strcmp(testBuf, correct) == 0,
               "Generated=[%s], Correct=[%s]", testBuf, correct);

      PrintSeparator();
   }

   void ObjectSizeTest ()
   {
      TestHeader("Object Sizes (bytes)");

      Progress(true, "FastOS_Application:  %d", sizeof(FastOS_Application));
      Progress(true, "FastOS_BoolCond      %d", sizeof(FastOS_BoolCond));
      Progress(true, "FastOS_Cond          %d", sizeof(FastOS_Cond));
      Progress(true, "FastOS_DirectoryScan %d", sizeof(FastOS_DirectoryScan));
      Progress(true, "FastOS_File:         %d", sizeof(FastOS_File));
      Progress(true, "FastOS_Mutex:        %d", sizeof(FastOS_Mutex));
      Progress(true, "FastOS_Runnable      %d", sizeof(FastOS_Runnable));
      Progress(true, "FastOS_ServerSocket  %d", sizeof(FastOS_ServerSocket));
      Progress(true, "FastOS_Socket:       %d", sizeof(FastOS_Socket));
      Progress(true, "FastOS_SocketFactory %d", sizeof(FastOS_SocketFactory));
      Progress(true, "FastOS_StatInfo      %d", sizeof(FastOS_StatInfo));
      Progress(true, "FastOS_Thread:       %d", sizeof(FastOS_Thread));
      Progress(true, "FastOS_ThreadPool:   %d", sizeof(FastOS_ThreadPool));
      Progress(true, "FastOS_Time          %d", sizeof(FastOS_Time));

      PrintSeparator();
   }

public:
   virtual ~TypeTest() {};

   int Main () override
   {
      printf("grep for the string '%s' to detect failures.\n\n", failString);

      PrintfSpecifiersTest();
      ObjectSizeTest();

      PrintSeparator();
      printf("END OF TEST (%s)\n", _argv[0]);

      return allWasOk() ? 0 : 1;
   }
};


int main (int argc, char **argv)
{
   setvbuf(stdout, NULL, _IOLBF, 8192);
   TypeTest app;
   return app.Entry(argc, argv);
}

