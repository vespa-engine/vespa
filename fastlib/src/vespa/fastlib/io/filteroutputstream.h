// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-1-14
* @version         $Id$
*
* @file
*
* Generic filter output stream interfaces
*
* Copyright (c)  : 1997-1999 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
#pragma once

#include <vespa/fastlib/io/outputstream.h>





class Fast_FilterOutputStream : public Fast_OutputStream
{
  private:

    // Prevent use of:
    Fast_FilterOutputStream(void);
    Fast_FilterOutputStream(Fast_FilterOutputStream &);
    Fast_FilterOutputStream &operator=(const Fast_FilterOutputStream &);


  protected:

    /** The stream to forward data to */
    Fast_OutputStream *_out;


  public:

    // Constructors
    Fast_FilterOutputStream(Fast_OutputStream &out) : _out(&out) {};

    virtual ~Fast_FilterOutputStream(void) {};


    virtual bool Close(void)    { return _out->Close(); }
    virtual void Flush(void)    {        _out->Flush(); }

    virtual inline ssize_t Write(const void *sourceBuffer, size_t length)
    {
      return _out->Write(sourceBuffer, length);
    }

};



