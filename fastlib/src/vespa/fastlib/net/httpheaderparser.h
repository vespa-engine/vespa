// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Markus Bjartveit Kr�ger
* @date            Creation date: 2000-11-22
* @version         $Id$
*
* @file
*
* HTTP header parser.
*
* Copyright (c)  : 2001 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/


#pragma once



class Fast_BufferedInputStream;


#define HTTPHEADERPARSER_LINE_BUFFER_SIZE  4096


class Fast_HTTPHeaderParser
{
  private:
    // Prevent use of:
    Fast_HTTPHeaderParser(const Fast_HTTPHeaderParser &);
    Fast_HTTPHeaderParser & operator=(const Fast_HTTPHeaderParser &);
  protected:
    char _pushBack;
    bool _isPushBacked;
    char _lineBuffer[HTTPHEADERPARSER_LINE_BUFFER_SIZE];
    Fast_BufferedInputStream *_input;

  public:
    Fast_HTTPHeaderParser(Fast_BufferedInputStream &in);
    virtual ~Fast_HTTPHeaderParser(void);


    // Methods
    bool ReadRequestLine(const char *&method, const char *&url,
                         int &versionMajor, int &versionMinor);
    bool ReadHeader(const char *&name, const char *&value);
};



