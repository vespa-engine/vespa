// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definitions for FastOS_UNIX_Application.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once


#include "types.h"
#include "app.h"

class FastOS_UNIX_ProcessStarter;
class FastOS_UNIX_IPCHelper;
class FastOS_UNIX_Process;

/**
 * This is the generic UNIX implementation of @ref FastOS_ApplicationInterface
 */
class FastOS_UNIX_Application : public FastOS_ApplicationInterface
{
private:
    FastOS_UNIX_Application(const FastOS_UNIX_Application&);
    FastOS_UNIX_Application& operator=(const FastOS_UNIX_Application&);

    FastOS_UNIX_ProcessStarter *_processStarter;
    FastOS_UNIX_IPCHelper     *_ipcHelper;

protected:
    bool PreThreadInit () override;
public:
    FastOS_UNIX_Application ();
    virtual ~FastOS_UNIX_Application();

    /**
     * Parse program arguments.  @ref GetOpt() incrementally parses the
     * command line argument list and returns the next known option
     * character.  An option character is known if it has been
     * specified in the string of accepted option characters,
     * [optionsString].
     *
     * The option string [optionsString] may contain the following
     * elements: individual characters, and characters followed by a
     * colon to indicate an option argument is to follow.  For example,
     * an option string "x" recognizes an option ``-x'', and an option
     * string "x:" recognizes an option and argument ``-x argument''.
     * It does not matter to @ref GetOpt() if a following argument has
     * leading white space.
     *
     * @ref GetOpt() returns -1 when the argument list is exhausted, or
     * `?' if a non-recognized option is encountered.  The
     * interpretation of options in the argument list may be canceled
     * by the option `--' (double dash) which causes getopt() to signal
     * the end of argument processing and return -1.  When all options
     * have been processed (i.e., up to the first non-option argument),
     * getopt() returns -1.
     *
     * @ref GetOpt() should only be run by a single thread at the same
     * time. In order to evaluate the argument list multiple times, the
     * previous GetOpt loop must be finished (-1 returned).
     */
    int GetOpt (const char *optionsString, const char* &optionArgument, int &optionIndex);

    int GetOptLong(const char *optionsString, const char* &optionArgument, int &optionIndex,
                   const struct option *longopts, int *longindex);
    /**
     * Called before calling GetOpt() or GetOptLong() by sub-applications.
     */
    static void resetOptIndex(int OptionIndex);

    FastOS_UNIX_ProcessStarter *GetProcessStarter ();
    bool Init () override;
    void Cleanup () override;
    void AddToIPCComm (FastOS_UNIX_Process *process);
    void RemoveFromIPCComm (FastOS_UNIX_Process *process);
};


