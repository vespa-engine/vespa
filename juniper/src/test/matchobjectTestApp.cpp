// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Definition and implementation of the application for running unit tests
 * for the MatchObject class in isolation.
 *
 * @file matchobjectTestApp.cpp
 *
 * @author Knut Omang
 *
 * @date Created 21 Feb 2003
 *
 * $Id$
 *
 * <pre>
 *              Copyright (c) : 2003 Fast Search & Transfer ASA
 *                              ALL RIGHTS RESERVED
 * </pre>
 ****************************************************************************/
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("matchobjectTest");
#include "matchobjectTest.h"
#include "testenv.h"

/**
 * The MatchObjectTestApp class is the main routine for running the unit
 * tests for the MatchObject class in isolation.
 *
 * @sa MatchObject @author Knut Omang
 */
class MatchObjectTestApp : public FastOS_Application {
public:
    virtual int Main() {
        juniper::TestEnv te(this, "../rpclient/testclient.rc");
        MatchObjectTest test;
        test.SetStream(&std::cout);
        test.Run(_argc, _argv);
        return (int)test.Report();
    }
};

FASTOS_MAIN(MatchObjectTestApp);
