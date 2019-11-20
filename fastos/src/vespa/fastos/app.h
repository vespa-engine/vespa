// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definition for FastOS_ApplicationInterface.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once


#include <vespa/fastos/types.h>

class FastOS_ProcessInterface;
class FastOS_ThreadPool;

#include <mutex>

/**
 * FastOS application wrapper class.
 * This class manages initialization and cleanup of the services
 * provided by FastOS.
 * Your application should inherit from this class, and implement
 * @ref Main().
 * It is possible, but not required to override the @ref Init()
 * and @ref Cleanup() methods. If you do, make sure you invoke
 * the superclass, in order to perform the neccessary initialization
 * and cleanup.
 *
 * To startup the FastOS_Application, invoke @ref Entry() as shown
 * in the example below. This will call @ref Init(), @ref Main()
 * (if @ref Init() succeeds) and @ref Cleanup(), in that order.
 *
 * Example:
 * @code
 *    #include <vespa/fastos/app.h>
 *
 *    class MyApplication : public FastOS_Application
 *    {
 *       int Main ()
 *       {
 *          printf("Hello world\n");
 *
 *          return 0;
 *       }
 *    };
 *
 *
 *    int main (int argc, char **argv)
 *    {
 *       MyApplication app;
 *
 *       app.Entry(argc, argv);
 *    }
 * @endcode
 *
 * Here is another example. This version overrides @ref Init() and
 * @ref Cleanup(). Most applications do not need to do this, but
 * an example of how to do it is included anyway:
 * @code
 *    #include <vespa/fastos/app.h>
 *
 *    class MyApplication : public FastOS_Application
 *    {
 *       bool MyOwnInitializationStuff ()
 *       {
 *          printf("My initialization stuff\n");
 *          return true;
 *       }
 *
 *       void MyOwnCleanupStuff()
 *       {
 *          printf("My cleanup stuff\n");
 *       }
 *
 *       // Most applications do not need to override this method.
 *       // If you do, make sure you invoke the superclass Init()
 *       // first.
 *       bool Init ()
 *       {
 *          bool rc=false;
 *
 *          if(FastOS_Application::Init())
 *          {
 *             if(MyOwnInitializationStuff())
 *             {
 *                rc = true;
 *             }
 *          }
 *
 *          // The application will not start if false is returned.
 *          return rc;
 *       }
 *
 *       // Most applications do not need to override this method.
 *       // If you do, make sure you invoke the superclass Cleanup()
 *       // at the end.
 *       // Note that Cleanup() is always invoked, even when Init()
 *       // fails.
 *       void Cleanup ()
 *       {
 *          MyOwnCleanupStuff();
 *
 *          FastOS_Application::Cleanup();
 *       }
 *
 *       int Main ()
 *       {
 *          printf("Hello world\n");
 *
 *          return 0;
 *       }
 *    };
 *
 *
 *    int main (int argc, char **argv)
 *    {
 *       MyApplication app;
 *
 *       app.Entry(argc, argv);
 *    }
 *
 * @endcode
 */
class FastOS_ApplicationInterface
{
    friend int main (int argc, char **argv);

private:
    FastOS_ApplicationInterface(const FastOS_ApplicationInterface&);
    FastOS_ApplicationInterface& operator=(const FastOS_ApplicationInterface&);

protected:
    /**
     *
     * Indicate if a process starter is going to be used.
     * Only override this one if you are going to start other processes.
     * @return true if you are going to use a process starter.
     */
    virtual bool useProcessStarter() const;
    virtual bool useIPCHelper() const;

    FastOS_ThreadPool       *_threadPool;
    FastOS_ProcessInterface *_processList;
    std::mutex              *_processListMutex;

    virtual bool PreThreadInit () { return true; }

public:
    int    _argc;
    char **_argv;

    FastOS_ApplicationInterface();

    virtual ~FastOS_ApplicationInterface();

    /**
     * FastOS initialization.
     * This method performs the neccessary initialization for FastOS.
     * It is possible to override this method, see the class documentation
     * for an example.
     * @ref Main() will be called, if and only if @ref Init() returns true.
     * @ref Cleanup will always be called, regardless of the @ref Init()
     * return value.
     */
    virtual bool Init();

    /**
     * FastOS Entry.
     * This method is used to enter the application and provide command
     * line arguments.  See the class documentation for an example on
     * how this is used.
     * @param argc Number of arguments
     * @param argv Array of arguments
     * @return Errorlevel returned to the shell
     */
    int Entry (int argc, char **argv);

    /**
     * FastOS Main.
     * This is where your application starts. See the class documentation
     * for an example on how this is used.
     * @return Errorlevel returned to the shell
     */
    virtual int Main ()=0;

    /**
     * FastOS Cleanup.
     * This method performs the neccessary cleanup for FastOS.
     * It is possible to override this method, see the class documentation
     * for an example.
     * @ref Cleanup() is always called, regardless of the return values
     * of @ref Init() and @ref Main().
     */
    virtual void Cleanup ();

    void AddChildProcess (FastOS_ProcessInterface *node);
    void RemoveChildProcess (FastOS_ProcessInterface *node);
    std::unique_lock<std::mutex> getProcessGuard() { return std::unique_lock<std::mutex>(*_processListMutex); }
    FastOS_ProcessInterface *GetProcessList () { return _processList; }

    FastOS_ThreadPool *GetThreadPool ();
};


#include <vespa/fastos/unix_app.h>
typedef FastOS_UNIX_Application FASTOS_PREFIX(Application);



// Macro included for convenience.
#define FASTOS_MAIN(application_class) \
int main (int argc, char **argv)       \
{                                      \
   application_class app;              \
                                       \
   return app.Entry(argc, argv);       \
}



