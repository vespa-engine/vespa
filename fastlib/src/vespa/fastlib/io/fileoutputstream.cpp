// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author Stein Hardy Danielsen
* @date            Creation date: 2000-10-07
*
* FileOutputStream class implementation
*
* Copyright (c)  : 1997-2000 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/

#include <vespa/fastos/fastos.h>
#include <vespa/fastlib/io/fileoutputstream.h>



Fast_FileOutputStream::Fast_FileOutputStream(const char *fileName)
    : _theFile(new FastOS_File(fileName))
{
    _theFile->OpenWriteOnly();
}


Fast_FileOutputStream::~Fast_FileOutputStream(void)
{
    _theFile->Close();
    delete _theFile;
}
