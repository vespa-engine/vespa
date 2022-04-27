// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/file.h>

class TypeTest : public BaseTest
{
private:

   void ObjectSizeTest ()
   {
      TestHeader("Object Sizes (bytes)");

      Progress(true, "FastOS_DirectoryScan %d", sizeof(FastOS_DirectoryScan));
      Progress(true, "FastOS_File:         %d", sizeof(FastOS_File));
      Progress(true, "FastOS_Runnable      %d", sizeof(FastOS_Runnable));
      Progress(true, "FastOS_StatInfo      %d", sizeof(FastOS_StatInfo));
      Progress(true, "FastOS_Thread:       %d", sizeof(FastOS_Thread));
      Progress(true, "FastOS_ThreadPool:   %d", sizeof(FastOS_ThreadPool));

      PrintSeparator();
   }

public:
   virtual ~TypeTest() {};

   int Main () override
   {
      printf("grep for the string '%s' to detect failures.\n\n", failString);

      ObjectSizeTest();

      PrintSeparator();
      printf("END OF TEST (%s)\n", _argv[0]);

      return allWasOk() ? 0 : 1;
   }
};


int main (int argc, char **argv)
{
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   TypeTest app;
   return app.Entry(argc, argv);
}

