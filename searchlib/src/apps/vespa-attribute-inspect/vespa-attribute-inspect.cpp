// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <iostream>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/fileheader.h>
#include <fstream>

#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <unistd.h>

namespace search {

typedef AttributeVector::SP AttributePtr;

class LoadAttribute
{
private:
    void load(const AttributePtr & ptr);
    void applyUpdate(const AttributePtr & ptr);
    void printContent(const AttributePtr & ptr, std::ostream & os);
    void usage();

public:
    int main(int argc, char **argv);
};

void
LoadAttribute::load(const AttributePtr & ptr)
{
    std::cout << "loading attribute: " << ptr->getBaseFileName() << std::endl;
    ptr->load();
    std::cout << "attribute successfully loaded"  << std::endl;
}

void
LoadAttribute::applyUpdate(const AttributePtr & ptr)
{
    std::cout << "applyUpdate" << std::endl;
    if (ptr->isIntegerType()) {
        IntegerAttribute * a = static_cast<IntegerAttribute *>(ptr.get());
        if (ptr->hasMultiValue()) {
            a->append(0, 123456789, 1);
        } else {
            a->update(0, 123456789);
        }
        a->commit();
    } else if (ptr->isFloatingPointType()) {
        FloatingPointAttribute * a = static_cast<FloatingPointAttribute *>(ptr.get());
        if (ptr->hasMultiValue()) {
            a->append(0, 123456789.5f, 1);
        } else {
            a->update(0, 123456789);
        }
        a->commit();
    } else if (ptr->isStringType()) {
        StringAttribute * a = static_cast<StringAttribute *>(ptr.get());
        if (ptr->hasMultiValue()) {
            a->append(0, "non-existing string value", 1);
        } else {
            a->update(0, "non-existing string value");
        }
        a->commit();
    }
}

void
LoadAttribute::printContent(const AttributePtr & ptr, std::ostream & os)
{
    uint32_t sz = ptr->getMaxValueCount();
    if (ptr->hasWeightedSetType()) {
        AttributeVector::WeightedString * buf = new AttributeVector::WeightedString[sz];
        for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
            uint32_t valueCount = ptr->get(doc, buf, sz);
            assert(valueCount <= sz);
            os << "doc " << doc << ": valueCount(" << valueCount << ")" << std::endl;
            for (uint32_t i = 0; i < valueCount; ++i) {
                os << "    " << i << ": " << "[" << buf[i].getValue() << ", " << buf[i].getWeight() << "]" << std::endl;
            }
        }
        delete [] buf;
    } else {
        vespalib::string *buf = new vespalib::string[ptr->getMaxValueCount()];
        for (uint32_t doc = 0; doc < ptr->getNumDocs(); ++doc) {
            uint32_t valueCount = ptr->get(doc, buf, sz);
            assert(valueCount <= sz);
            os << "doc " << doc << ": valueCount(" << valueCount << ")" << std::endl;
            for (uint32_t i = 0; i < valueCount; ++i) {
                os << "    " << i << ": " << "[" << buf[i] << "]" << std::endl;
            }
        }
        delete [] buf;
    }
}

void
LoadAttribute::usage()
{
    std::cout << "usage: vespa-attribute-inspect [-p (print content to <attribute>.out)]" << std::endl;
    std::cout << "                     [-a (apply a single update)]" << std::endl;
    std::cout << "                     [-s (save attribute to <attribute>.save.dat)]" << std::endl;
    std::cout << "                     <attribute>" << std::endl;
}

int
LoadAttribute::main(int argc, char **argv)
{
    bool doPrintContent = false;
    bool doApplyUpdate = false;
    bool doSave = false;
    bool doFastSearch = false;

    int opt;
    bool optError = false;
    while ((opt = getopt(argc, argv, "pasf:")) != -1) {
        switch (opt) {
        case 'p':
            doPrintContent = true;
            break;
        case 'a':
            doApplyUpdate = true;
            break;
        case 'f':
            if (strcmp(optarg, "search") == 0) {
                doFastSearch = true;
            } else {
                std::cerr << "Expected 'search' or 'aggregate', got '" <<
                    optarg << "'" << std::endl;
                optError = true;
            }
            break;
        case 's':
            doSave = true;
            break;
        default:
            optError = true;
            break;
        }
    }

    if (argc != (optind + 1) || optError) {
        usage();
        return -1;
    }

    vespalib::string fileName(argv[optind]);
    vespalib::FileHeader fh;
    {
        vespalib::string datFileName(fileName + ".dat");
        Fast_BufferedFile file;
        file.ReadOpenExisting(datFileName.c_str());
        (void) fh.readFile(file);
    }
    attribute::BasicType bt(fh.getTag("datatype").asString());
    attribute::CollectionType ct(fh.getTag("collectiontype").asString());
    attribute::Config c(bt, ct);
    c.setFastSearch(doFastSearch);
    AttributePtr ptr = AttributeFactory::createAttribute(fileName, c);
    vespalib::Timer timer;
    load(ptr);
    std::cout << "load time: " << vespalib::to_s(timer.elapsed()) << " seconds " << std::endl;

    std::cout << "numDocs: " << ptr->getNumDocs() << std::endl;

    if (doApplyUpdate) {
        timer = vespalib::Timer();
        applyUpdate(ptr);
        std::cout << "update time: " << vespalib::to_s(timer.elapsed()) << " seconds " << std::endl;
    }

    if (doPrintContent) {
        vespalib::string outFile(fileName + ".out");
        std::ofstream of(outFile.c_str());
        if (of.fail()) {
            std::cout << "failed opening: " << fileName << ".out" << std::endl;
        }
        std::cout << "printContent" << std::endl;
        printContent(ptr, of);
        of.close();
    }

    if (doSave) {
        vespalib::string saveFile = fileName + ".save";
        std::cout << "saving attribute: " << saveFile << std::endl;
        timer = vespalib::Timer();
        ptr->save(saveFile);
        std::cout << "save time: " << vespalib::to_s(timer.elapsed()) << " seconds " << std::endl;
    }

    return 0;
}

}

int main(int argc, char ** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    search::LoadAttribute myApp;
    return myApp.main(argc, argv);
}
