// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "application.h"
#include <boost/program_options.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/document/base/idstringexception.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("vespadoclocator");


bool
Application::printDocumentLocation(Locator &locator, const std::string &docIdStr)
{
    try {
        document::DocumentId docId(docIdStr);
        std::cout << "DocumentId(" << docIdStr << ") "
                  << "BucketId(" << locator.getBucketId(docId).getId() << ") "
                  << "SearchColumn(" << locator.getSearchColumn(docId) << ")"
                  << std::endl;
    } catch (document::IdParseException &e) {
        std::cerr << e.getMessage() << std::endl;
        return false;
    } catch (vespalib::IllegalArgumentException &e) {
        std::cerr << e.getMessage() << std::endl;
        return false;
    }
    return true;
}

int
Application::Main()
{
    // Configure locator object.
    using namespace boost::program_options;

    uint32_t numColumns = 0;
    std::string configId;
    std::string clusterName;
    std::vector<std::string> docIds;

    options_description desc("This is a tool for resolving the target column number of a document."
                             "\n\n"
                             "The options are");
    desc.add_options()
        ( "config-id,i",
          value<std::string>(&configId)->default_value("client"),
          "The identifier to use when subscribing to configuration." )

        ( "cluster-name,c",
          value<std::string>(&clusterName),
          "The name of the search cluster in which to resolve document location." )

        ( "document-id,d",
          value< std::vector<std::string> >(&docIds),
          "The identifiers of the documents to locate. "
          "These can also be passed as arguments without the option prefix. "
          "If none is given, this tool parses identifiers from standard in." )

        ( "help,h",
          "Shows this help page." )

        ( "num-columns,n",
          value<uint32_t>(&numColumns),
          "The number of columns in the search cluster. By providing this, no configuration "
          "is required, meaning you can run this tool outside of a vespa cluster." );

    positional_options_description pos;
    pos.add("document-id", -1);

    variables_map vm;
    try {
        store(command_line_parser(_argc, _argv).options(desc).positional(pos).run(), vm);
        notify(vm);
    } catch (unknown_option &e) {
        std::cout << e.what() << std::endl;
        return EXIT_FAILURE;
    }

    if (vm.count("help") != 0) {
        std::cout << desc << std::endl;
        return EXIT_SUCCESS;
    }

    Locator locator(numColumns);
    if (vm.count("num-columns") == 0) {
        try {
            locator.configure(configId, clusterName);
        } catch (config::InvalidConfigException &e) {
            std::cerr << e.getMessage() << std::endl;
            return EXIT_FAILURE;
        }
    }

    // Locate the documents provided.
    if (docIds.empty()) {
        char buf[4096];
        while (!std::cin.getline(buf, 4096).eof()) {
            std::string in(buf);
            if (!printDocumentLocation(locator, in)) {
                return EXIT_FAILURE;
            }
        }
    } else {
        for (std::vector<std::string>::iterator it = docIds.begin();
             it != docIds.end(); ++it)
        {
            if (!printDocumentLocation(locator, *it)) {
                return EXIT_FAILURE;
            }
        }
    }
    return EXIT_SUCCESS;
}
