// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author Stein Hardy Danielsen
* @date            Creation date: 2000-1-14
*
* Generic output stream interface
*
* Copyright (c)  : 1997-1999 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#pragma once

#include <cstdlib>

class Fast_OutputStream
{
public:

    virtual ~Fast_OutputStream() { }

    virtual bool    Close() = 0;
    virtual void    Flush() = 0;
    virtual ssize_t Write(const void *sourceBuffer, size_t bufferSize) = 0;
};
