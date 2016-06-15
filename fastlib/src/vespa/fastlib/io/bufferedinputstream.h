// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definitions for Fast_BufferedInputStream
 *
 * @author  Markus Bjartveit Kr�ger
 * @version $Id$
 */
 /*
 * Creation date    : 2001-10-29
 * Copyright (c)    : 1997-2002 Fast Search & Transfer ASA
 *                    ALL RIGHTS RESERVED
 *************************************************************************/
#pragma once

#include <vespa/fastlib/io/filterinputstream.h>





class Fast_BufferedInputStream : public Fast_FilterInputStream
{
  private:

    // Prevent use of:
    Fast_BufferedInputStream(const Fast_BufferedInputStream &);
    Fast_BufferedInputStream & operator=(const Fast_BufferedInputStream &);


  protected:

    // Buffer attributes
    char    *_buffer;
    size_t   _bufferSize;
    size_t   _bufferUsed;       // Amount of buffer currently holding data
    size_t   _bufferRead;       // How far buffer has been read
    bool     _nextWillFail;


  public:

    // Constructor
    Fast_BufferedInputStream(Fast_InputStream &in, size_t bufferSize = 1024);

    // Destructor
    virtual ~Fast_BufferedInputStream(void);

    // Subclassed methods
    virtual ssize_t  Available(void);
    virtual bool     Close(void);
    virtual ssize_t  Skip(size_t skipNBytes);
    virtual ssize_t  Read(void *targetBuffer, size_t length);

    // Additional methods
    ssize_t  ReadBufferFullUntil(void *targetBuffer,
                                 size_t maxlength,
                                 char stopChar);
};




