// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "application.h"

int
main(int argc, char** argv)
{
    spoolmaster::Application *app = new spoolmaster::Application();
    int retVal = app->Entry(argc, argv);
    delete app;
    return retVal;
}

