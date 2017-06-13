// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/app.h>
#include <vespa/fastlib/net/httpheaderparser.h>
#include <vespa/fastlib/io/fileinputstream.h>
#include <vespa/fastlib/io/bufferedinputstream.h>

class HeaderReaderApp : public FastOS_Application
{
public:
    int Main() override
    {
        if (_argc != 2)
        {
            fprintf(stderr, "Usage: %s <header file>\n", _argv[0]);
            return 1;
        }
        Fast_FileInputStream fileinput(_argv[1]);
        Fast_BufferedInputStream input(fileinput, 32768);
        Fast_HTTPHeaderParser headerParser(input);

        const char *headerName, *headerValue;
        while (headerParser.ReadHeader(headerName, headerValue))
        {
            printf("Header name:  \"%s\"\n", headerName);
            printf("Header value: \"%s\"\n", headerValue);
            printf("\n");
        }

        char buffer[1024];
        size_t bytesRead = 0;
        ssize_t lastRead;
        printf("------> Remaining data in file: <------\n");
        while ((lastRead = input.Read(buffer, sizeof(buffer))) > 0)
        {
            fwrite(buffer, 1, lastRead, stdout);
            bytesRead += lastRead;
        }
        printf("------>  End of remaining data  <--------\n");
        printf("Total remaining data: %u bytes\n",
               static_cast<unsigned int>(bytesRead));

        return 0;
    }
};




int main (int argc, char *argv[])
{
    HeaderReaderApp app;

    return app.Entry(argc, argv);
}
