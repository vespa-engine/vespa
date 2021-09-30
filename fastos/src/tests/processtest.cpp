// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "tests.h"
#include <vespa/fastos/process.h>
#include <thread>

using namespace std::chrono_literals;
using namespace std::chrono;

class MyListener : public FastOS_ProcessRedirectListener
{
private:
   MyListener(const MyListener&);
   MyListener& operator=(const MyListener&);

   const char *_title;
   int _receivedBytes;

public:
   static int _allocCount;
   static int _successCount;
   static int _failCount;
    static std::mutex *_counterLock;

   MyListener (const char *title)
     : _title(title),
       _receivedBytes(0)
   {
       std::lock_guard<std::mutex> guard(*_counterLock);
       _allocCount++;
   }

   virtual ~MyListener ()
   {
      bool isStdout = (strcmp(_title, "STDOUT") == 0);

      const int correctByteCount = 16;

      std::lock_guard<std::mutex> guard(*_counterLock);
      if(_receivedBytes == (isStdout ? correctByteCount : 0))
         _successCount++;
      else
         _failCount++;

      _allocCount--;
   }

   void OnReceiveData (const void *data, size_t length) override
   {
      _receivedBytes += length;
      if(data != nullptr)
      {
      }
      else
         delete(this);
   }
};

int MyListener::_allocCount = 0;
int MyListener::_successCount = 0;
int MyListener::_failCount = 0;
std::mutex *MyListener::_counterLock = nullptr;

class ProcessTest : public BaseTest
{
private:
    bool useProcessStarter() const override { return true; }
    bool useIPCHelper() const override { return true; }
    ProcessTest(const ProcessTest&);
    ProcessTest& operator=(const ProcessTest&);
    int GetLastError () const { return errno; }

   // Flag which indicates whether an IPC message is received
   // or not.
   bool _gotMessage;
   int _receivedMessages;
   std::mutex *_counterLock;
   bool _isChild;
public:
   ProcessTest ()
     : _gotMessage(false),
       _receivedMessages(0),
       _counterLock(nullptr),
       _isChild(true)
   {
   }

   void PollWaitTest ()
   {
      TestHeader("PollWait Test");

      FastOS_Process *xproc = new FastOS_Process("sort", true);

      if(xproc->Create())
      {
         int i;
         for(i=0; i<10; i++)
         {
            bool stillRunning;
            int returnCode;

            if(!xproc->PollWait(&returnCode, &stillRunning))
            {
               Progress(false, "PollWait failure: %d",
                        GetLastError());
               break;
            }

            if(i <= 5)
               Progress(stillRunning, "StillRunning = %s",
                        stillRunning ? "true" : "false");

            if(!stillRunning)
            {
               Progress(returnCode == 0, "Process exit code: %d",
                        returnCode);
               break;
            }

            if(i == 5)
            {
               // Make sort quit
               xproc->WriteStdin(nullptr, 0);
            }

            std::this_thread::sleep_for(1s);
         }

         if(i == 10)
         {
            Progress(false, "Timeout");
            xproc->Kill();
         }
      }
      delete xproc;

      PrintSeparator();
   }

   void ProcessTests (bool doKill, bool stdinPre, bool waitKill)
   {
      const int numLoops = 100;
      const int numEachTime = 40;

      MyListener::_counterLock = new std::mutex;

      char testHeader[200];
      strcpy(testHeader, "Process Test");
      if(doKill)
         strcat(testHeader, " w/Kill");
      if(!stdinPre)
         strcat(testHeader, " w/open stdin");
      if(waitKill)
         strcat(testHeader, " w/Wait timeout");

      TestHeader(testHeader);

      MyListener::_allocCount = 0;
      MyListener::_successCount = 0;
      MyListener::_failCount = 0;

      Progress(true, "Starting processes...");

      for(int i=0; i<numLoops; i++)
      {
         FastOS_ProcessInterface *procs[numEachTime];

         int j;
         for(j=0; j<numEachTime; j++)
         {
            FastOS_ProcessInterface *xproc =
               new FastOS_Process("sort", true,
                                  new MyListener("STDOUT"),
                                  new MyListener("STDERR"));

            if(xproc->Create())
            {
               const char *str = "Peter\nPaul\nMary\n";

               if(!waitKill && stdinPre)
               {
                  xproc->WriteStdin(str, strlen(str));
                  xproc->WriteStdin(nullptr, 0);
               }

               if(doKill)
               {
                  if(!xproc->Kill())
                     Progress(false, "Kill failure %d", GetLastError());
               }

               if(!waitKill && !stdinPre)
               {
                  xproc->WriteStdin(str, strlen(str));
                  xproc->WriteStdin(nullptr, 0);
               }
            }
            else
            {
               Progress(false, "Process.CreateWithShell failure %d",
                        GetLastError());
               delete xproc;
               xproc = nullptr;
            }
            procs[j] = xproc;
         }

         for(j=0; j<numEachTime; j++)
         {
            FastOS_ProcessInterface *xproc = procs[j];
            if(xproc == nullptr)
               continue;

            int timeOut = -1;
            if(waitKill)
               timeOut = 1;

            steady_clock::time_point start = steady_clock::now();

            int returnCode;
            if(!xproc->Wait(&returnCode, timeOut))
               Progress(false, "Process.Wait failure %d", GetLastError());
            else
            {
               int checkReturnCode = 0;
               if(doKill || waitKill)
                  checkReturnCode = FastOS_Process::KILL_EXITCODE;
               if(returnCode != checkReturnCode)
                  Progress(false, "returnCode = %d", returnCode);
            }

            if (waitKill) {
               nanoseconds elapsed = steady_clock::now() - start;
               if((elapsed < 900ms) ||
                  (elapsed > 3500ms))
               {
                  Progress(false, "WaitKill time = %d", duration_cast<milliseconds>(elapsed).count());
               }
            }

            delete xproc;

            if(waitKill)
               Progress(true, "Started %d processes", i * numEachTime + j + 1);
         }

         if(!waitKill && ((i % 10) == 9))
            Progress(true, "Started %d processes", (i+1) * numEachTime);

         if(waitKill && (((i+1) * numEachTime) > 50))
            break;
      }

      Progress(MyListener::_allocCount == 0, "MyListener alloc count = %d", MyListener::_allocCount);

      if (!doKill && !waitKill) {
         Progress(MyListener::_successCount == (2 * numLoops * numEachTime),
                  "MyListener _successCount = %d", MyListener::_successCount);

         Progress(MyListener::_failCount == 0,
                  "MyListener _failCount = %d", MyListener::_failCount);
      }

      delete MyListener::_counterLock;
      MyListener::_counterLock = nullptr;

      PrintSeparator();
   }

   int Main () override
   {
      _isChild = false;

      printf("grep for the string '%s' to detect failures.\n\n", failString);

      PollWaitTest();
      ProcessTests(false, true, false);
      ProcessTests(true, true, false);
      ProcessTests(true, false, false);
      ProcessTests(false, true, true);

      printf("END OF TEST (%s)\n", _argv[0]);

      return allWasOk() ? 0 : 1;
   }
};

int main (int argc, char **argv)
{
   ProcessTest app;
   setvbuf(stdout, nullptr, _IOLBF, 8192);
   return app.Entry(argc, argv);
}
