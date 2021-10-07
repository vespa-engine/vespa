// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

#include "tests.h"

void PerformanceTest (char *buffer);

int main (int argc, char **argv)
{
   (void)argc;
   (void)argv;

   void (*test)(char *buffer) = PerformanceTest;

   test(nullptr);
   return 0;
}

void PerformanceTest (char *buffer)
{
   // Cause exception
   *static_cast<char *>(nullptr) = 'e';

#if 1
   FastOS_File file("test.txt");

   if(file.OpenReadOnly())
   {
      file.Read(buffer, 20);
      file.Write2(buffer, 20);
      file.Read(buffer, 20);
      file.Write2(buffer, 20);
      file.Read(buffer, 20);
      file.Write2(buffer, 20);
   }
#else

   int filedes = open("test.txt", O_RDONLY, 0664);

   if(filedes != -1)
   {
      write(filedes, buffer, 20);
      read(filedes, buffer, 20);
      write(filedes, buffer, 20);
      read(filedes, buffer, 20);
      write(filedes, buffer, 20);
      read(filedes, buffer, 20);
      write(filedes, buffer, 20);

      close(filedes);
   }
#endif
}

