// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <cassert>
#include <ctime>
#include <iostream>

using namespace std;

#include <vespa/fastlib/io/fileinputstream.h>
#include <vespa/fastlib/io/fileoutputstream.h>
#include <vespa/fastlib/io/bufferedinputstream.h>
#include <vespa/fastlib/io/bufferedoutputstream.h>

static bool ReadFile(const char *tag, Fast_InputStream &input,
                     char *buffer, int64_t fileSize, size_t chunkSize)
{
    char *pos;
    clock_t start, ticks;

    cout << "Starting to read file (" << tag << ")..." << endl;
    start = clock();

    pos = buffer;
    // Slurp file
    ssize_t numRead;
    while (pos - buffer < fileSize &&
                         (numRead = input.Read(pos, chunkSize)) > 0)
        pos += numRead;
    if (pos - buffer != fileSize)
    {
        cerr << "Read "      << pos - buffer << " bytes, "
             << " expected " << static_cast<int>(fileSize) << " bytes.\n";
        return false;
    }

    ticks = clock() - start;
    cout << "Done, used " << ticks << " ticks "
         << "(" << static_cast<double>(ticks) / CLOCKS_PER_SEC << " seconds)\n\n"
         << ends;

    return true;
}

static bool WriteAndReadBackFile(const char *tag, Fast_OutputStream &output,
                                 char *buffer, int64_t fileSize,
                                 const char *fileName, size_t chunkSize)
{
    char *pos;
    clock_t start, ticks;

    cout << "Starting to write file (" << tag << ")..." << endl;
    start = clock();

    pos = buffer;
    // Dump file
    ssize_t numWritten;
    size_t numToWrite = (fileSize < static_cast<int64_t>(chunkSize)) ?
                        static_cast<size_t>(fileSize) : chunkSize;
    while (pos - buffer < fileSize &&
           (numWritten = output.Write(pos, numToWrite)) > 0)
    {
        pos += numWritten;
        if ((fileSize - (pos - buffer)) < static_cast<int64_t>(chunkSize)) {
            numToWrite = (fileSize - (pos - buffer));
        }
        else {
            numToWrite = chunkSize;
        }
    }
    output.Flush();

    if (pos - buffer != fileSize)
    {
        cerr << "Wrote "     << pos - buffer << " bytes, " \
             << " expected " << static_cast<int>(fileSize) << " bytes.\n";
        return false;
    }

    ticks = clock() - start;
    cout << "Done, used " << ticks << " ticks "
         << "(" << static_cast<double>(ticks) / CLOCKS_PER_SEC << " seconds)\n\n"
         << ends;

    output.Close();

    FastOS_File readBackFile;
    readBackFile.OpenReadOnly(fileName);
    if (readBackFile.Read(buffer, fileSize) != fileSize)
    {
        cerr << "Error reading data back from " << fileName << ".\n";
        return false;
    }

    return true;
}

int main(int argc, char **argv)
{
    if (argc < 2 || argc > 4)
    {
        cerr << "Usage: " << argv[0] << " <file> [<buffer size> [<chunk size>]]\n";
        return 1;
    }

    const char *fileName = argv[1];
    unsigned long bufferSize = (argc >= 3) ? strtoul(argv[2], NULL, 10) : 1024;
    unsigned long chunkSize  = (argc >= 4) ? strtoul(argv[3], NULL, 10) : 1;

    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(fileName, &statInfo))
    {
        cerr << "Failed to stat " << fileName << "\n";
        return 1;
    }

    int64_t fileSize = statInfo._size;

    char *unbufferedData = new char[fileSize];
    assert(unbufferedData != NULL);

    char *bufferedData = new char[fileSize];
    assert(bufferedData != NULL);

    /////////////////////////////////////////////////////////////////////
    // Start of input test

    Fast_FileInputStream unbufferedInputFile(fileName);
    // run unneeded functions for coverage
    unbufferedInputFile.Skip(unbufferedInputFile.Available());

    if (!ReadFile("unbuffered", unbufferedInputFile, unbufferedData, fileSize, chunkSize))
    {
        cerr << "Unbuffered read failed" << endl;
        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    Fast_FileInputStream slaveInputFile(fileName);
    Fast_BufferedInputStream bufferedInputFile(slaveInputFile, bufferSize);

    if (!ReadFile("buffered", bufferedInputFile, bufferedData, fileSize, chunkSize))
    {
        cerr << "Buffered read failed" << endl;
        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    if (memcmp(unbufferedData, bufferedData, fileSize) == 0)
    {
        cout << "Buffered and unbuffered data equal -- success!\n";
    }
    else
    {
        cout << "Buffered and unbuffered data differs -- error!\n";
        cout << "Contents of unbuffered data:\n";
        cout.write(unbufferedData, fileSize);
        cout << "Contents of buffered data:\n";
        cout.write(bufferedData, fileSize);

        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    /////////////////////////////////////////////////////////////////////
    // Start of output test

    const char *tempFile = "bufferedstreamtest.tmp";

    Fast_FileOutputStream unbufferedOutputFile(tempFile);

    if (!WriteAndReadBackFile("unbuffered", unbufferedOutputFile,
                              unbufferedData, fileSize, tempFile, chunkSize))
    {
        cerr << "Unbuffered write and read back failed" << endl;

        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    Fast_FileOutputStream slaveOutputFile(tempFile);
    Fast_BufferedOutputStream bufferedOutputFile(slaveOutputFile, bufferSize);

    if (!WriteAndReadBackFile("buffered", bufferedOutputFile,
                              bufferedData, fileSize, tempFile, chunkSize))
    {
        cerr << "Buffered write and read back failed" << endl;

        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    if (memcmp(unbufferedData, bufferedData, fileSize) == 0)
    {
        cout << "Buffered and unbuffered data equal -- success!\n";
    }
    else
    {
        cout << "Buffered and unbuffered data differs -- error!\n";
        cout << "Contents of unbuffered data:\n";
        cout.write(unbufferedData, fileSize);
        cout << "Contents of buffered data:\n";
        cout.write(bufferedData, fileSize);

        delete [] unbufferedData;
        delete [] bufferedData;
        return 1;
    }

    delete [] unbufferedData;
    delete [] bufferedData;
    return 0;
}
