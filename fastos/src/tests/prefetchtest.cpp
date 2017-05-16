// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * FastOS_Prefetch test program.
 *
 * @author  Olaf Birkeland
 * @version $Id$
 */
 /*
 * Creation date    : 2000-12-11
 * Copyright (c)    : 1997-2001 Fast Search & Transfer ASA
 *                    ALL RIGHTS RESERVED
 *************************************************************************/

#include "tests.h"
#include <vespa/fastos/time.h>
#include <vespa/fastos/prefetch.h>

class PrefetchTestApp : public BaseTest
{
public:
   virtual ~PrefetchTestApp() {}

   bool PrefetchTest ()
   {
      bool rc = false;
      int j, size, *a;
      int or1, or2;
      FastOS_Time start, stop;
      double timeVal;

      TestHeader("Prefetch Test");

      // 32MB
      size = 32;
      size *= 1024*1024/sizeof(*a);

      if ((a = static_cast<int *>(calloc(size, sizeof(*a)))) != NULL)
      {
         // Standard loop
         start.SetNow();
         or1 = 1;
         for(j=0; j<size; j++)
            or1 |= a[j];
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1==1, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (standard loop)",
                       float(size*sizeof(*a)/(1E3*timeVal)));


         // Unrolled loop
         start.SetNow();
         or1 = or2 = 2;
         for(j=0; j<size; j+=8)
         {
            or1 |= a[j+0]|a[j+1]|a[j+2]|a[j+3];
            or2 |= a[j+4]|a[j+5]|a[j+6]|a[j+7];
         }
         or1 |= or2;
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1 == 2, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (unrolled loop)",
                       float(size*sizeof(*a)/(1E3*timeVal)));


         // Unrolled loop with prefetch
         start.SetNow();
         or1 = or2 = 3;
         for(j=0; j<size; j+=8)
         {
            FastOS_Prefetch::NT(&a[j+32]);
            or1 |= a[j+0]|a[j+1]|a[j+2]|a[j+3];
            or2 |= a[j+4]|a[j+5]|a[j+6]|a[j+7];
         }
         or1 |= or2;
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1 == 3, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (unrolled loop with prefetch)",
                       float(size*sizeof(*a)/(1E3*timeVal)));

         // Unrolled loop
         start.SetNow();
         or1 = or2 = 4;
         for(j=0; j<size; j+=8)
         {
            or1 |= a[j+0]|a[j+1]|a[j+2]|a[j+3];
            or2 |= a[j+4]|a[j+5]|a[j+6]|a[j+7];
         }
         or1 |= or2;
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1 == 4, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (unrolled loop)",
                       float(size*sizeof(*a)/(1E3*timeVal)));


         // Standard loop
         start.SetNow();
         or1 = 5;
         for(j=0; j<size; j++)
            or1 |= a[j];
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1 == 5, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (standard loop)",
                       float(size*sizeof(*a)/(1E3*timeVal)));


         // Unrolled loop with prefetch
         start.SetNow();
         or1 = or2 = 6;
         for(j=0; j<size; j+=8)
         {
            FastOS_Prefetch::NT(&a[j+32]);
            or1 |= a[j+0]|a[j+1]|a[j+2]|a[j+3];
            or2 |= a[j+4]|a[j+5]|a[j+6]|a[j+7];
         }
         or1 |= or2;
         stop.SetNow();
         timeVal = stop.MilliSecs() - start.MilliSecs();
         Progress(or1 == 6, "Result = %d", or1);
         ProgressFloat(true, "%4.3f MB/s (unrolled loop with prefetch)",
                       float(size*sizeof(*a)/(1E3*timeVal)));


         free(a);
         rc = true;
      }
      else
         Progress(false, "Out of memory!!");

      PrintSeparator();

      return rc;
   }

   int Main () override
   {
      int rc = 1;
      printf("grep for the string '%s' to detect failures.\n\n", failString);

      if(PrefetchTest())
         rc = 0;

      printf("END OF TEST (%s)\n", _argv[0]);

      return rc;
   }
};


int main (int argc, char **argv)
{
   PrefetchTestApp app;
   setvbuf(stdout, NULL, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
