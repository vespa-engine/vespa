// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/app.h>
#include <vespa/fastos/file.h>
#include <iomanip>
#include <iostream>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/log/log.h>
LOG_SETUP("vespa-fileheader-inspect");

using namespace vespalib;

class Application : public FastOS_Application {
private:
    vespalib::string _fileName;
    char        _delimiter;
    bool        _quiet;

    int parseOpts();
    void usage();
    void printQuiet(FileHeader &header);
    void printVerbose(FileHeader &header);
    vespalib::string escape(const vespalib::string &str, char quote = '\0');
    vespalib::string getTypeString(const FileHeader::Tag &tag);
    vespalib::string getValueString(const FileHeader::Tag &tag);

public:
    Application();
    ~Application();
    int Main() override;
};

Application::Application() :
    _fileName(""),
    _delimiter(';'),
    _quiet(false)
{ }

Application::~Application() {}


void
Application::usage()
{
    printf("Tool for inspecting the headers of files used by Vespa.\n");
    printf("Usage: %s [options] filename\n", _argv[0]);
    printf("\n");
    printf("The options are:\n");
    printf("-d delimiter   The delimiter to use to separate values in quiet output.\n");
    printf("-f file        The name of the file to inspect.\n");
    printf("-q             Enables machine readable output.\n");
    printf("-h             Shows this help page.\n");
}


int
Application::parseOpts()
{
    char c = '?';
    const char *optArg = NULL;
    int optInd = 0;
    while ((c = GetOpt("d:f:qh", optArg, optInd)) != -1) {
        switch (c) {
        case 'd':
            _delimiter = optArg[0];
            break;
        case 'f':
            _fileName = optArg;
            break;
        case 'q':
            _quiet = true;
            break;
        case 'h':
            usage();
            return EXIT_SUCCESS;
        default:
            usage();
            return EXIT_FAILURE;
        }
    }
    if (_argc == optInd + 1) {
        _fileName = _argv[optInd];
    }
    if (_fileName.empty()) {
        std::cerr << "No filename given." << std::endl;
        return EXIT_FAILURE;
    }
    return ~(EXIT_SUCCESS | EXIT_FAILURE);
}

int
Application::Main()
{
    int ret = parseOpts();
    if (ret == EXIT_FAILURE || ret == EXIT_SUCCESS) {
        return ret;
    }

    FastOS_File file;
    if (!file.OpenReadOnly(_fileName.c_str())) {
        std::cerr << "Failed to open file '" << _fileName << "'." << std::endl;
        return EXIT_FAILURE;
    }

    FileHeader header;
    try {
        header.readFile(file);
    } catch (IllegalHeaderException &e) {
        std::cerr << e.getMessage() << std::endl;
        return EXIT_FAILURE;
    }
    file.Close();

    if (_quiet) {
        printQuiet(header);
    } else {
        printVerbose(header);
    }
    return EXIT_SUCCESS;
}

void
Application::printQuiet(FileHeader &header)
{
    for (uint32_t i = 0, len = header.getNumTags(); i < len; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        std::cout << escape(tag.getName(), _delimiter) << _delimiter
                  << escape(getTypeString(tag), _delimiter) << _delimiter
                  << escape(getValueString(tag), _delimiter) << std::endl;
    }
}

void
Application::printVerbose(FileHeader &header)
{
    uint32_t nameWidth = 3, typeWidth = 4, valueWidth = 5;
    for (uint32_t i = 0, len = header.getNumTags(); i < len; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        nameWidth = std::max(nameWidth, (uint32_t)tag.getName().size());
        typeWidth = std::max(typeWidth, (uint32_t)getTypeString(tag).size());
        valueWidth = std::max(valueWidth, (uint32_t)getValueString(tag).size());
    }

    vespalib::asciistream line;
    line << "+" << std::string(nameWidth + 2, '-')
         << "+" << std::string(typeWidth + 2, '-')
         << "+" << std::string(valueWidth + 2, '-')
         << "+";

    std::cout << std::left << line.str() << std::endl;
    std::cout << "| " << std::setw(nameWidth) << "Tag" << " "
              << "| " << std::setw(typeWidth) << "Type" << " "
              << "| " << std::setw(valueWidth)<< "Value" << " "
              << "| " << std::endl;
    std::cout << line.str() << std::endl;
    for (uint32_t i = 0, len = header.getNumTags(); i < len; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        std::cout << "| " << std::setw(nameWidth) << escape(tag.getName()) << " "
                  << "| " << std::setw(typeWidth) << getTypeString(tag) << " "
                  << "| " << std::setw(valueWidth) << escape(getValueString(tag)) << " "
                  << "| " << std::endl;
    }
    std::cout << line.str() << std::endl;
}

vespalib::string
Application::escape(const vespalib::string &str, char quote)
{
    vespalib::string ret = "";
    for (uint32_t i = 0, len = str.size(); i < len; ++i) {
        char c = str[i];
        switch (c) {
        case '\f':
            ret.append("\\f");
            break;
        case '\n':
            ret.append("\\n");
            break;
        case '\r':
            ret.append("\\r");
            break;
        case '\t':
            ret.append("\\t");
            break;
        default:
            if (c != '\0' && c == quote) {
                ret.append("\\");
            }
            ret.push_back(c);
        }
    }
    return ret;
}

vespalib::string
Application::getTypeString(const FileHeader::Tag &tag)
{
    switch (tag.getType()) {
    case FileHeader::Tag::TYPE_FLOAT:
        return "float";
    case FileHeader::Tag::TYPE_INTEGER:
        return "integer";
    case FileHeader::Tag::TYPE_STRING:
        return "string";
    default:
        LOG_ASSERT(tag.getType() == FileHeader::Tag::TYPE_INTEGER);
        LOG_ABORT("should not be reached");
    }
}

vespalib::string
Application::getValueString(const FileHeader::Tag &tag)
{
    vespalib::asciistream out;
    out << tag;
    return out.str();
}

int
main(int argc, char** argv)
{
    Application app;
    return app.Entry(argc, argv);
}
