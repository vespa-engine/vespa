// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tests.h"
#include <vespa/fastos/file.h>
#include <memory>
#include <cassert>
#include <sys/mman.h>

namespace {

// Create the named file, and write it's filename into it.
// Return true on success
bool createFile(const char* fileName) {
    FastOS_StatInfo statInfo;
    FastOS_File cf(fileName);
    return ( cf.OpenWriteOnly() &&
            cf.CheckedWrite(fileName, strlen(fileName)) &&
            cf.Close() &&
            FastOS_File::Stat(fileName, &statInfo) &&
            statInfo._isRegular );
}

bool createFile(const char* fileName,
                const unsigned int size) {
    FastOS_File cf(fileName);
    bool success = false;
    if (cf.OpenWriteOnlyTruncate()) {
        auto buf = std::make_unique<char[]>(size); // Could be dangerous..
        if (buf) {
            memset(buf.get(), 0, size); // dont write uninitialized bytes since valgrind will complain
            if (cf.CheckedWrite(buf.get(), size)) {
                success = true;
            }
        }
        cf.Close();
    }
    return success;
}
} // namespace <unnamed>


class FileTest : public BaseTest
{
private:
    virtual bool useProcessStarter() const override { return true; }
public:
    const std::string srcDir = getenv("SOURCE_DIRECTORY") ? getenv("SOURCE_DIRECTORY") : ".";
    const std::string roFilename = srcDir + "/hello.txt";
    const std::string woFilename = "generated/writeonlytest.txt";
    const std::string rwFilename = "generated/readwritetest.txt";

    void DirectoryTest()
    {
        TestHeader ("Directory management (remove & empty) test");

        const char *dirName = "tmpTestDir";
        char file1[1024];
        char file2[1024];
        char file3[1024];
        char file4[1024];
        char file5[1024];
        char subdir1[512];
        char subdir2[512];
        sprintf(file1, "%s%sfile1", dirName, FastOS_File::GetPathSeparator());
        sprintf(file2, "%s%sfile2", dirName, FastOS_File::GetPathSeparator());
        sprintf(file3, "%s%sfile2", dirName, FastOS_File::GetPathSeparator());
        sprintf(subdir1, "%s%sdir1", dirName, FastOS_File::GetPathSeparator());
        sprintf(subdir2, "%s%sdir2", dirName, FastOS_File::GetPathSeparator());
        sprintf(file4, "%s%sfile4", subdir2, FastOS_File::GetPathSeparator());
        sprintf(file5, "%s%sfile5", subdir2, FastOS_File::GetPathSeparator());

        FastOS_StatInfo statInfo;

        bool success = false;

        // Don't run at all if the directory already exists
        assert(!FastOS_File::Stat(dirName, &statInfo));

        FastOS_File::MakeDirectory(dirName);

        // Verify that we succeed with an empty directory
        FastOS_File::EmptyDirectory(dirName);
        success = FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Removing empty directory.");

        // Verify that we can empty a directory with files in it
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::EmptyDirectory(dirName);
        success =
            !FastOS_File::Stat(file1, &statInfo) &&
            !FastOS_File::Stat(file2, &statInfo) &&
            !FastOS_File::Stat(file3, &statInfo) &&
            FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Deleting dir with files in it.");

        // Verify that we can empty a directory with files and directories in it
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::MakeDirectory(subdir1);
        FastOS_File::MakeDirectory(subdir2);
        createFile(file4);
        createFile(file5);
        FastOS_File::EmptyDirectory(dirName);
        success =  FastOS_File::Stat(dirName, &statInfo) &&
                   !FastOS_File::Stat(file1, &statInfo) &&
                   !FastOS_File::Stat(file2, &statInfo) &&
                   !FastOS_File::Stat(file3, &statInfo) &&
                   !FastOS_File::Stat(file4, &statInfo) &&
                   !FastOS_File::Stat(file5, &statInfo) &&
                   !FastOS_File::Stat(subdir1, &statInfo) &&
                   !FastOS_File::Stat(subdir2, &statInfo);
        Progress(success, "Emptying directory with files and folders in it.");

        // Verify that we don't empty the directory if we find a file to keep
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::MakeDirectory(subdir1);
        FastOS_File::MakeDirectory(subdir2);
        createFile(file4);
        createFile(file5);
        FastOS_File::EmptyDirectory(dirName, "file1");
        success = FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Emptying dir with keepfile in it.");
        // Verify that all but the file to keep are removed
        success = FastOS_File::Stat(file1, &statInfo) &&
                  !FastOS_File::Stat(file2, &statInfo) &&
                  !FastOS_File::Stat(file3, &statInfo) &&
                  !FastOS_File::Stat(file4, &statInfo) &&
                  !FastOS_File::Stat(file5, &statInfo) &&
                  !FastOS_File::Stat(subdir1, &statInfo) &&
                  !FastOS_File::Stat(subdir2, &statInfo);
        Progress(success, "Looking for keepfile.");

        // Verify that we don't empty the sub-directory if we find a file to keep
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::MakeDirectory(subdir1);
        FastOS_File::MakeDirectory(subdir2);
        createFile(file4);
        createFile(file5);
        FastOS_File::EmptyDirectory(dirName, "file4");
        success = FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Emptying file with nested keepfile.");
        // Verify that all but the file to keep are removed
        success = !FastOS_File::Stat(file1, &statInfo) &&
                  !FastOS_File::Stat(file2, &statInfo) &&
                  !FastOS_File::Stat(file3, &statInfo) &&
                  FastOS_File::Stat(file4, &statInfo) &&
                  !FastOS_File::Stat(file5, &statInfo) &&
                  !FastOS_File::Stat(subdir1, &statInfo) &&
                  FastOS_File::Stat(subdir2, &statInfo);
        // Progress(success, "Looking for nested keepfile."); // Unsupported for now.


        FastOS_File::EmptyAndRemoveDirectory(dirName);

        FastOS_File::MakeDirectory(dirName);

        // Verify that we can remove an empty directory
        FastOS_File::EmptyAndRemoveDirectory(dirName);
        success = !FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Deleting empty directory.");

        // Verify that we can remove a directory with files in it
        FastOS_File::MakeDirectory(dirName);
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::EmptyAndRemoveDirectory(dirName);
        success = !FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Deleting a directory with files in it.");

        // Verify that we can remove a directory with files and directories in it
        FastOS_File::MakeDirectory(dirName);
        createFile(file1);
        createFile(file2);
        createFile(file3);
        FastOS_File::MakeDirectory(subdir1);
        FastOS_File::MakeDirectory(subdir2);
        createFile(file4);
        createFile(file5);
        FastOS_File::EmptyAndRemoveDirectory(dirName);
        success = !FastOS_File::Stat(dirName, &statInfo);
        Progress(success, "Deleting directory with files and directories in it.");

    }

    void MoveFileTest() {
        TestHeader ("Moving files (across volumes too) test");

        const char *dirName = "tmpTestDir";
        char file1[1024];
        char file2[1024];
        char file3[1024];
        sprintf(file1, "%s%sfile1", dirName, FastOS_File::GetPathSeparator());
        sprintf(file2, "%s%sfile2", dirName, FastOS_File::GetPathSeparator());
        sprintf(file3, "%stmp%sfile3", FastOS_File::GetPathSeparator(),
                FastOS_File::GetPathSeparator());

        FastOS_File::MakeDirectory(dirName);
        createFile(file1);

        FastOS_StatInfo statInfo;
        // Move file to new name in same dir.
        FastOS_File::MoveFile(file1, file2);
        Progress(FastOS_File::Stat(file2, &statInfo), "Moving one within a directory.");

        // Move file to /tmp.
        FastOS_File::MoveFile(file2, file3);
        Progress(FastOS_File::Stat(file3, &statInfo), "Moving to /tmp/");

        // Clean up
        FastOS_File::Delete(file3);
        FastOS_File::EmptyAndRemoveDirectory(dirName);
    }

    void GetCurrentDirTest ()
    {
        TestHeader ("Get Current Directory Test");

        std::string currentDir = FastOS_File::getCurrentDirectory();

        Progress(!currentDir.empty(),
                 "Current dir: %s", !currentDir.empty() ?
                 currentDir.c_str() : "<failed>");

        bool dirrc = FastOS_File::SetCurrentDirectory("..");

        std::string parentDir;

        if (dirrc) {
            parentDir = FastOS_File::getCurrentDirectory();
        }

        Progress(dirrc && strcmp(currentDir.c_str(), parentDir.c_str()) != 0,
                 "Parent dir: %s", !parentDir.empty() ?
                 parentDir.c_str() : "<failed>");

        dirrc = FastOS_File::SetCurrentDirectory(currentDir.c_str());

        Progress(dirrc, "Changed back to working directory.");

        PrintSeparator();
    }

    void MemoryMapTest (int mmap_flags)
    {
        TestHeader ("Memory Map Test");

        int i;
        const int bufSize = 1000;

        FastOS_File::MakeDirectory("generated");
        FastOS_File file("generated/memorymaptest");

        bool rc = file.OpenReadWrite();
        Progress(rc, "Opening file 'generated/memorymaptest'");

        if (rc) {
            char *buffer = new char [bufSize];
            for (i = 0; i < bufSize; i++) {
                buffer[i] = i % 256;
            }
            ssize_t wroteB = file.Write2(buffer, bufSize);
            Progress(wroteB == bufSize, "Writing %d bytes to file", bufSize);

            file.Close();

            file.enableMemoryMap(mmap_flags);

            rc = file.OpenReadOnly();

            Progress(rc, "Opening file 'generated/memorymaptest' read-only");
            if (rc) {
                bool mmapEnabled;
                char *mmapBuffer = nullptr;

                mmapEnabled = file.IsMemoryMapped();
                mmapBuffer = static_cast<char *>(file.MemoryMapPtr(0));

                Progress(rc, "Memory mapping %s",
                         mmapEnabled ? "enabled" : "disabled");
                Progress(rc, "Map address: 0x%p", mmapBuffer);

                if (mmapEnabled) {
                    rc = 0;
                    for (i = 0; i < bufSize; i++) {
                        rc |= (mmapBuffer[i] == i % 256);
                    }
                    Progress(rc, "Reading %d bytes from memory map", bufSize);
                }
            }
            delete [] buffer;
        }
        FastOS_File::EmptyAndRemoveDirectory("generated");
        PrintSeparator();
    }

    void DirectIOTest ()
    {
        TestHeader ("Direct Disk IO Test");

        int i;
        const int bufSize = 40000;

        FastOS_File::MakeDirectory("generated");
        FastOS_File file("generated/diotest");

        bool rc = file.OpenWriteOnly();
        Progress(rc, "Opening file 'generated/diotest' write-only");

        if (rc) {
            char *buffer = new char [bufSize];

            for (i=0; i<bufSize; i++) {
                buffer[i] = 'A' + (i % 17);
            }
            ssize_t wroteB = file.Write2(buffer, bufSize);
            Progress(wroteB == bufSize, "Writing %d bytes to file", bufSize);

            file.Close();

            if (rc) {
                file.EnableDirectIO();

                rc = file.OpenReadOnly();
                Progress(rc, "Opening file 'generated/diotest' read-only");
                if (rc) {
                    bool dioEnabled;
                    size_t memoryAlignment=0;
                    size_t transferGranularity=0;
                    size_t transferMaximum=0;

                    dioEnabled = file.GetDirectIORestrictions(memoryAlignment,
                                                              transferGranularity,
                                                              transferMaximum);

                    Progress(rc, "DirectIO %s", dioEnabled ? "enabled" : "disabled");
                    Progress(rc, "Memory alignment: %u bytes", memoryAlignment);
                    Progress(rc, "Transfer granularity: %u bytes", transferGranularity);
                    Progress(rc, "Transfer maximum: %u bytes", transferMaximum);

                    if (dioEnabled) {
                        int eachRead = (8192 + transferGranularity - 1) / transferGranularity;

                        char *buffer2 = new char [(eachRead * transferGranularity +
                                                   memoryAlignment - 1)];
                        char *alignPtr = buffer2;
                        unsigned int align =
                            static_cast<unsigned int>
                            (reinterpret_cast<unsigned long>(alignPtr) &
                             (memoryAlignment - 1));
                        if (align != 0) {
                            alignPtr = &alignPtr[memoryAlignment - align];
                        }
                        int residue = bufSize;
                        int pos=0;
                        while (residue > 0) {
                            int readThisTime = eachRead * transferGranularity;
                            if (readThisTime > residue) {
                                readThisTime = residue;
                            }
                            file.ReadBuf(alignPtr, readThisTime, pos);

                            for (i=0; i<readThisTime; i++) {
                                rc = (alignPtr[i] == 'A' + ((i+pos) % 17));
                                if (!rc) {
                                    Progress(false, "Read error at offset %d", i);
                                    break;
                                }
                            }
                            residue -= readThisTime;
                            pos += readThisTime;

                            if (!rc) break;
                        }
                        if (rc) {
                            Progress(true, "Read success");

                            rc = file.SetPosition(1);
                            Progress(rc, "SetPosition(1)");
                            if (rc) {
                                try {
                                    const int attemptReadBytes = 173;
                                    ssize_t readB = file.Read(buffer, attemptReadBytes);
                                    Progress(false, "Expected to get an exception for unaligned read");
                                    ProgressI64(readB == attemptReadBytes, "Got %ld bytes from attempted 173", readB);
                                } catch(const DirectIOException &e) {
                                    Progress(true, "Got exception as expected");
                                }
                            }
                            if (rc) {
                                rc = file.SetPosition(1);
                                Progress(rc, "SetPosition(1)");
                                if (rc) {
                                    try {
                                        const int attemptReadBytes = 4096;
                                        ssize_t readB = file.Read(buffer, attemptReadBytes);
                                        Progress(false, "Expected to get an exception for unaligned read");
                                        ProgressI64(readB == attemptReadBytes, "Got %ld bytes from attempted 4096", readB);
                                    } catch(const DirectIOException &e) {
                                        Progress(true, "Got exception as expected");
                                    }
                                }
                            }
                        }
                        delete [] buffer2;
                    } else {
                        memset(buffer, 0, bufSize);

                        ssize_t readBytes = file.Read(buffer, bufSize);
                        Progress(readBytes == bufSize,
                                 "Reading %d bytes from file", bufSize);

                        for (i=0; i<bufSize; i++) {
                            rc = (buffer[i] == 'A' + (i % 17));
                            if (!rc) {
                                Progress(false, "Read error at offset %d", i);
                                break;
                            }
                        }
                        if (rc) Progress(true, "Read success");
                    }
                }
            }
            delete [] buffer;
        }

        FastOS_File::EmptyAndRemoveDirectory("generated");
        PrintSeparator();
    }

    void ReadOnlyTest ()
    {
        TestHeader("Read-Only Test");

        FastOS_File *myFile = new FastOS_File(roFilename.c_str());

        if (myFile->OpenReadOnly()) {
            int64_t filesize;
            filesize = myFile->GetSize();
            ProgressI64((filesize == 27), "File size: %ld", filesize);

            char dummyData[6] = "Dummy";
            bool writeResult = myFile->CheckedWrite(dummyData, 6);

            if (writeResult) {
                Progress(false, "Should not be able to write a file opened for read-only access.");
            } else {
                char dummyData2[28];
                Progress(true, "Write failed with read-only access.");

                bool rc = myFile->SetPosition(1);
                Progress(rc, "Setting position to 1");

                if (rc) {
                    ssize_t readBytes;
                    int64_t filePosition;
                    readBytes = myFile->Read(dummyData2, 28);

                    Progress(readBytes == 26, "Attempting to read 28 bytes, should get 26. Got: %d", readBytes);

                    filePosition = myFile->GetPosition();
                    Progress(filePosition == 27, "File position should now be 27. Was: %d", int(filePosition));

                    readBytes = myFile->Read(dummyData2, 6);
                    Progress(readBytes == 0, "We should now get 0 bytes. Read: %d bytes", readBytes);

                    filePosition = myFile->GetPosition();
                    Progress(filePosition == 27, "File position should now be 27. Was: %d", int(filePosition));
                }
            }
        } else {
            Progress(false, "Unable to open file '%s'.", roFilename.c_str());
        }
        delete(myFile);
        PrintSeparator();
    }

    void WriteOnlyTest ()
    {
        TestHeader("Write-Only Test");
        FastOS_File::MakeDirectory("generated");

        FastOS_File *myFile = new FastOS_File(woFilename.c_str());

        if (myFile->OpenWriteOnly()) {
            int64_t filesize;
            filesize = myFile->GetSize();

            ProgressI64((filesize == 0), "File size: %ld", filesize);

            char dummyData[6] = "Dummy";
            bool writeResult = myFile->CheckedWrite(dummyData, 6);

            if (!writeResult) {
                Progress(false, "Should be able to write to file opened for write-only access.");
            } else {
                Progress(true, "Write 6 bytes ok.");

                int64_t filePosition = myFile->GetPosition();
                if (filePosition == 6) {
                    Progress(true, "Fileposition is now 6.");

                    if (myFile->SetPosition(0)) {
                        Progress(true, "SetPosition(0) success.");
                        filePosition = myFile->GetPosition();

                        if (filePosition == 0) {
                            Progress(true, "Fileposition is now 0.");

                            int readBytes = myFile->Read(dummyData, 6);

                            if (readBytes != 6) {
                                Progress(true, "Trying to read a write-only file should fail and it did.");
                                Progress(true, "Return code was: %d.", readBytes);
                            } else {
                                Progress(false, "Read on a file with write-only access should fail, but it didn't.");
                            }
                        } else {
                            ProgressI64(false, "Fileposition should be 6, but was %ld.", filePosition);
                        }
                    } else {
                        Progress(false, "SetPosition(0) failed");
                    }
                } else {
                    ProgressI64(false, "Fileposition should be 6, but was %ld.", filePosition);
                }
            }
            bool closeResult = myFile->Close();
            Progress(closeResult, "Close file.");
        } else {
            Progress(false, "Unable to open file '%s'.", woFilename.c_str());
        }


        bool deleteResult = myFile->Delete();
        Progress(deleteResult, "Delete file '%s'.", woFilename.c_str());

        delete(myFile);
        FastOS_File::EmptyAndRemoveDirectory("generated");
        PrintSeparator();
    }

    void ReadWriteTest ()
    {
        TestHeader("Read/Write Test");
        FastOS_File::MakeDirectory("generated");

        FastOS_File *myFile = new FastOS_File(rwFilename.c_str());

        if (myFile->OpenExisting()) {
            Progress(false, "OpenExisting() should not work when '%s' does not exist.", rwFilename.c_str());
            myFile->Close();
        } else {
            Progress(true, "OpenExisting() should fail when '%s' does not exist, and it did.", rwFilename.c_str());
        }

        if (myFile->OpenReadWrite()) {
            int64_t filesize;

            filesize = myFile->GetSize();

            ProgressI64((filesize == 0), "File size: %ld", filesize);

            char dummyData[6] = "Dummy";

            bool writeResult = myFile->CheckedWrite(dummyData, 6);

            if (!writeResult) {
                Progress(false, "Should be able to write to file opened for read/write access.");
            } else {
                Progress(true, "Write 6 bytes ok.");

                int64_t filePosition = myFile->GetPosition();

                if (filePosition == 6) {
                    Progress(true, "Fileposition is now 6.");

                    if (myFile->SetPosition(0)) {
                        Progress(true, "SetPosition(0) success.");
                        filePosition = myFile->GetPosition();

                        if (filePosition == 0) {
                            Progress(true, "Fileposition is now 0.");

                            char dummyData2[7];
                            int readBytes = myFile->Read(dummyData2, 6);

                            if (readBytes == 6) {
                                Progress(true, "Reading 6 bytes worked.");

                                int cmpResult = memcmp(dummyData, dummyData2, 6);
                                Progress((cmpResult == 0), "Comparing the written and read result.\n");

                                bool rc = myFile->SetPosition(1);
                                Progress(rc, "Setting position to 1");

                                if (rc) {
                                    readBytes = myFile->Read(dummyData2, 7);

                                    Progress(readBytes == 5, "Attempting to read 7 bytes, should get 5. Got: %d", readBytes);

                                    filePosition = myFile->GetPosition();
                                    Progress(filePosition == 6, "File position should now be 6. Was: %d", int(filePosition));

                                    readBytes = myFile->Read(dummyData2, 6);
                                    Progress(readBytes == 0, "We should not be able to read any more. Read: %d bytes", readBytes);

                                    filePosition = myFile->GetPosition();
                                    Progress(filePosition == 6, "File position should now be 6. Was: %d", int(filePosition));
                                }
                            } else {
                                Progress(false, "Reading 6 bytes failed.");
                            }
                        } else {
                            ProgressI64(false, "Fileposition should be 6, but was %ld.", filePosition);
                        }
                    } else {
                        Progress(false, "SetPosition(0) failed");
                    }
                } else {
                    ProgressI64(false, "Fileposition should be 6, but was %ld.", filePosition);
                }
            }

            bool closeResult = myFile->Close();
            Progress(closeResult, "Close file.");
        } else {
            Progress(false, "Unable to open file '%s'.", rwFilename.c_str());
        }
        bool deleteResult = myFile->Delete();
        Progress(deleteResult, "Delete file '%s'.", rwFilename.c_str());

        delete(myFile);
        FastOS_File::EmptyAndRemoveDirectory("generated");
        PrintSeparator();
    }

    void ScanDirectoryTest()
    {
        TestHeader("DirectoryScan Test");

        FastOS_DirectoryScan *scanDir = new FastOS_DirectoryScan(".");

        while (scanDir->ReadNext()) {
            const char *name = scanDir->GetName();
            bool isDirectory = scanDir->IsDirectory();
            bool isRegular   = scanDir->IsRegular();

            printf("%-30s %s\n", name,
                   isDirectory ? "DIR" : (isRegular ? "FILE" : "UNKN"));
        }

        delete(scanDir);
        PrintSeparator();
    }

    void ReadBufTest ()
    {
        TestHeader("ReadBuf Test");

        FastOS_File file(roFilename.c_str());

        char buffer[20];

        if (file.OpenReadOnly()) {
            int64_t position = file.GetPosition();
            Progress(position == 0, "File pointer should be 0 after opening file");

            file.Read(buffer, 4);
            buffer[4] = '\0';
            position = file.GetPosition();
            Progress(position == 4, "File pointer should be 4 after reading 4 bytes");
            Progress(strcmp(buffer, "This") == 0, "[This]=[%s]", buffer);

            file.ReadBuf(buffer, 6, 8);
            buffer[6] = '\0';
            position = file.GetPosition();
            Progress(position == 4, "File pointer should still be 4 after ReadBuf");
            Progress(strcmp(buffer, "a test") == 0, "[a test]=[%s]", buffer);

            file.Close();
        }

        PrintSeparator();
    }

    void DiskFreeSpaceTest ()
    {
        TestHeader("DiskFreeSpace Test");

        int64_t freeSpace = FastOS_File::GetFreeDiskSpace(roFilename.c_str());
        ProgressI64(freeSpace != -1, "DiskFreeSpace using file ('hello.txt'): %ld MB.", freeSpace == -1 ? -1 : freeSpace/(1024*1024));
        freeSpace = FastOS_File::GetFreeDiskSpace(".");
        ProgressI64(freeSpace != -1, "DiskFreeSpace using dir (.): %ld MB.", freeSpace == -1 ? -1 : freeSpace/(1024*1024));
        PrintSeparator();
    }

    void MaxLengthTest ()
    {
        TestHeader ("Max Lengths Test");

        int maxval = FastOS_File::GetMaximumFilenameLength(".");
        Progress(maxval > 5 && maxval < (512*1024),
                 "Maximum filename length = %d", maxval);

        maxval = FastOS_File::GetMaximumPathLength(".");
        Progress(maxval > 5 && maxval < (512*1024),
                 "Maximum path length = %d", maxval);

        PrintSeparator();
    }

    void CopyFileTest ()
    {
        FastOS_StatInfo statInfo;
        TestHeader("CopyFile Test");
        const char *dirName = "tmpDir";
        char file1[1024];
        char file2[1024];
        char file3[1024];
        char file4[1024];
        char file5[1024];
        sprintf(file1, "%s%sfile1", dirName, FastOS_File::GetPathSeparator());
        sprintf(file2, "%s%sfile2", dirName, FastOS_File::GetPathSeparator());
        sprintf(file3, "%s%sfile3", dirName, FastOS_File::GetPathSeparator());
        sprintf(file4, "%s%sfile4", dirName, FastOS_File::GetPathSeparator());
        sprintf(file5, "%s%sfile5", dirName, FastOS_File::GetPathSeparator());

        FastOS_File::EmptyAndRemoveDirectory(dirName);
        FastOS_File::MakeDirectory(dirName);
        printf("Creating files to copy. Some of them are quite large...\n\n");
        createFile(file1);
        createFile(file3, 20*1024*1024); // 20MB file.
        createFile(file4, 1024*1024); // 1MB file, i.e. exact size of buffer.
        createFile(file5, 1024*1024 + 100); // 1.001MB file

        FastOS_File::Stat(file4, &statInfo);
        unsigned int sizeOfFile4 = statInfo._size;

        FastOS_File::Stat(file5, &statInfo);
        unsigned int sizeOfFile5 = statInfo._size;

        // Tests start here.
        bool copyOK = FastOS_File::CopyFile(file1, file2);
        Progress(copyOK,
                 "File copy from %s to %s.", file1, file2);

        FastOS_File::Delete(file2);
        copyOK = FastOS_File::CopyFile(file3, file2);
        Progress(copyOK,
                 "File copy from %s to %s.", file3, file2);
        FastOS_File::Stat(file2, &statInfo);
        Progress(statInfo._size == 20*1024*1024,
                 "Size of copied file is 20MB.");

        copyOK = FastOS_File::CopyFile(file3, file3);
        Progress(!copyOK,
                 "File copy onto itself should fail.");

        FastOS_File::Delete(file1);
        copyOK = FastOS_File::CopyFile(file1, file2);
        Progress(!copyOK,
                 "File copy of a missing file should fail.");

        copyOK = FastOS_File::CopyFile(file4, file2);
        Progress(copyOK,
                 "Copying a smaller file onto a larger one.");
        FastOS_File::Stat(file2, &statInfo);
        Progress(statInfo._size == sizeOfFile4,
                 "Size of copied file should be %u bytes.", sizeOfFile4);

        copyOK = FastOS_File::CopyFile(file4, file1);
        Progress(copyOK,
                 "Copying a file with exact size of buffer.");
        FastOS_File::Stat(file1, &statInfo);
        Progress(statInfo._size == sizeOfFile4,
                 "Size of copied file should be %u bytes.", sizeOfFile4);

        copyOK = FastOS_File::CopyFile(file5, file1);
        Progress(copyOK,
                 "Copying a file with size %u bytes.", sizeOfFile5);
        FastOS_File::Stat(file1, &statInfo);
        Progress(statInfo._size == sizeOfFile5,
                 "Size of copied file should be %u bytes.", sizeOfFile5);


        FastOS_File::EmptyAndRemoveDirectory("./tmpDir");
        PrintSeparator();
    }

    int Main () override
    {
        printf("This test should be run in the 'tests' directory.\n\n");
        printf("grep for the string '%s' to detect failures.\n\n", failString);

        DirectoryTest();
        MoveFileTest();
        CopyFileTest();
        GetCurrentDirTest();
        DirectIOTest();
        MaxLengthTest();
        DiskFreeSpaceTest();
        ReadOnlyTest();
        WriteOnlyTest();
        ReadWriteTest();
        ScanDirectoryTest();
        ReadBufTest();
        MemoryMapTest(0);
        MemoryMapTest(MAP_HUGETLB);

        PrintSeparator();
        printf("END OF TEST (%s)\n", _argv[0]);

        return allWasOk() ? 0 : 1;
    }
    FileTest();
    ~FileTest();
};

FileTest::FileTest() { }
FileTest::~FileTest() { }


int main (int argc, char **argv)
{
    FileTest app;

    setvbuf(stdout, nullptr, _IOLBF, 8192);
    return app.Entry(argc, argv);
}
