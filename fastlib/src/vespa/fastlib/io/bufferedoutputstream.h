// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Markus Bjartveit Kr�ger
* @date            Creation date: 2001-10-30
* @version         $Id$
*
* @file
*
* Generic buffered output stream
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#pragma once

#include <vespa/fastlib/io/filteroutputstream.h>





class Fast_BufferedOutputStream : public Fast_FilterOutputStream
{
  private:

    // Prevent use of:
    Fast_BufferedOutputStream(const Fast_BufferedOutputStream &);
    Fast_BufferedOutputStream & operator=(const Fast_BufferedOutputStream &);


  protected:

    // Buffer attributes
    char    *_buffer;
    size_t   _bufferSize;
    size_t   _bufferUsed;       // Amount of buffer currently holding data
    size_t   _bufferWritten;    // How far buffer has been written
    bool     _nextWillFail;


  public:

    // Constructor
    Fast_BufferedOutputStream(Fast_OutputStream &out, size_t bufferSize = 1024);

    // Destructor
    virtual ~Fast_BufferedOutputStream(void);

    // Methods
    virtual bool     Close(void);
    virtual ssize_t  Write(const void *sourceBuffer, size_t length);
    virtual void     Flush(void);

};




