// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "tests.h"
#include <vespa/fastos/process.h>
#include <vespa/fastos/timestamp.h>
#include <vespa/fastos/file.h>

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
#if 0
         printf("[%s] received %u bytes of data:\n%s\n",
                _title, length, static_cast<const char *>(data));
#endif
      }
      else
         delete(this);
   }
};

int MyListener::_allocCount = 0;
int MyListener::_successCount = 0;
int MyListener::_failCount = 0;
std::mutex *MyListener::_counterLock = nullptr;


class ThreadRunJob : public FastOS_Runnable
{
private:
   ThreadRunJob(const ThreadRunJob&);
   ThreadRunJob& operator=(const ThreadRunJob&);

   const char *_processCmdLine;
   int _timeSpent;
public:
   ThreadRunJob (const char *commandLine) :
      _processCmdLine(commandLine),
      _timeSpent(0)
   {
   }

   void Run (FastOS_ThreadInterface *, void *) override
   {

      fastos::StopWatch timer;

      FastOS_Process xproc(_processCmdLine);
      int returnCode = -1;

      if (xproc.Create()) {
         xproc.Wait(&returnCode);
      }

      _timeSpent = int(timer.elapsed().ms());
   }

   int GetTimeSpent ()
   {
      return _timeSpent;
   }
};

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

   void OnReceivedIPCMessage (const void *data, size_t length) override
   {
      //      printf("Data: [%s]\n", static_cast<const char *>(data));

      if(length == 5) {
         const char *dataMatch = "IPCM";
         if(!_isChild)
            dataMatch = "IPCR";
         if(strcmp(static_cast<const char *>(data), dataMatch) != 0)
            Progress(false,
                     "Received message did not match \"%s\" (%s)",
                     dataMatch, static_cast<const char *>(data));
      }
      else
         Progress(false,
                  "Received message was not 5 bytes long (%d)", length);

      _gotMessage = true;

      // We only have the counter lock if we are the parent process.
      if(_counterLock != nullptr)
      {
          std::lock_guard<std::mutex> guard(*_counterLock);
          _receivedMessages++;
      }
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

            FastOS_Thread::Sleep(1000);
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

            fastos::StopWatch timer;

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
               double milliSecs = timer.elapsed().ms();
               if((milliSecs < 900) ||
                  (milliSecs > 3500))
               {
                  Progress(false, "WaitKill time = %d", int(milliSecs));
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

      Progress(MyListener::_allocCount == 0, "MyListener alloc count = %d",
               MyListener::_allocCount);

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

   int DoChildRole () {
      int rc = 124;
      int i;
      for(i=0; i<(20*10); i++) {
         if(_gotMessage)
            break;

         FastOS_Thread::Sleep(100);
      }
      if(i < (20*10))
      {
         // Send a message to the parent process.
         const char *messageString = "IPCR";
         SendParentIPCMessage(messageString, strlen(messageString) + 1);
         rc = 123;
      }
      else
         Progress(false, "Child timed out waiting for IPC message");

      return rc;
   }

   void IPCTest () {
      TestHeader ("IPC Test");
      const char *childProgram = _argv[1];

      _counterLock = new std::mutex;

      int i;
      for(i=0; i<30; i++)
      {
         FastOS_Process process(childProgram);
         if(process.Create())
         {
            // Send a message to the child process.
            const char *messageString = "IPCM";
            process.SendIPCMessage(messageString,
                                   strlen(messageString) + 1);

            // Wait for the process to end.
            int returnCode;
            if(process.Wait(&returnCode))
            {
               Progress(returnCode == 123,
                        "Child exited with code: %d", returnCode);
            }
            else
               Progress(false, "process.Wait() failed");
         }
         else
            Progress(false, "process.Create() (%s) failed", childProgram);
      }

      Progress(_receivedMessages == i,
               "Received %d messages", _receivedMessages);

      delete _counterLock;
      _counterLock = nullptr;

      PrintSeparator();
   }

   void NoInheritTest ()
   {
      TestHeader("No Inherit Test");

      const char *filename = "process__test.tmp___";

      Progress(true, "Opening '%s' for writing...", filename);
      void *inheritData = FastOS_Process::PrefopenNoInherit();
      FILE *fp = fopen(filename, "w");
      int numProc = FastOS_Process::PostfopenNoInherit(inheritData);
      Progress(fp != nullptr, "Open file");
      if(fp != nullptr)
      {
         Progress(numProc > 0, "Number of files processed = %d\n",
                  numProc);
         fclose(fp);
      }
      FastOS_File::Delete(filename);

      PrintSeparator();
   }

   int Main () override
   {
      // This process is started as either a parent or a child.
      // When the parent role is desired, a child program is supplied
      // as argv[1]

      if(_argc != 3)
         return DoChildRole();

      _isChild = false;

      printf("grep for the string '%s' to detect failures.\n\n", failString);

      NoInheritTest();
      PollWaitTest();
      IPCTest();
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
