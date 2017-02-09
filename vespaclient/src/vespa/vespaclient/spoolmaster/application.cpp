// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/defaults.h>
#include <vector>
#include <string>
#include <iostream>
#include <algorithm>
#include <cstdio>
#include <dirent.h>
#include <unistd.h>

#include "application.h"

namespace {

std::string masterInbox() {
    std::string dir = vespa::Defaults::vespaHome();
    dir.append("var/spool/master/inbox");
    return dir;
}

std::string outboxParent() {
    std::string dir = vespa::Defaults::vespaHome();
    dir.append("var/spool/vespa");
    return dir;
}

}

namespace spoolmaster {

Application::Application()
    : _masterInbox(masterInbox()),
      _inboxFiles(),
      _outboxParentDir(outboxParent()),
      _outboxes()
{
    // empty
}

Application::~Application()
{
    // empty
}

bool
Application::scanInbox()
{
    std::vector<std::string> rv;
    DIR *d = opendir(_masterInbox.c_str());
    if (d == NULL) {
        perror(_masterInbox.c_str());
        mkdir(_masterInbox.c_str(), 0775);
        return false;
    }

    struct dirent *entry;
    while ((entry = readdir(d)) != NULL) {
        if (strcmp(entry->d_name, ".")  == 0) continue;
        if (strcmp(entry->d_name, "..") == 0) continue;

        std::string fn = _masterInbox;
        fn.append("/");
        fn.append(entry->d_name);

        struct stat sb;
        if (stat(fn.c_str(), &sb) == 0) {
            if (S_ISREG(sb.st_mode)) {
                rv.push_back(fn);
            }
        } else {
            perror(fn.c_str());
        }
    }
    closedir(d);

    if (access(_masterInbox.c_str(), W_OK) < 0) {
        perror(_masterInbox.c_str());
        return false;
    }

    _inboxFiles = rv;
    return (rv.size() > 0);
}

bool
Application::findOutboxes()
{
    std::vector<std::string> rv;
    DIR *d = opendir(_outboxParentDir.c_str());
    if (d == NULL) {
        perror(_outboxParentDir.c_str());
        return false;
    }
    struct dirent *entry;
    while ((entry = readdir(d)) != NULL) {
        if (strcmp(entry->d_name, ".")  == 0) continue;
        if (strcmp(entry->d_name, "..") == 0) continue;

        /* XXX: should check if d_name starts with "colo." ? */

        std::string fn = _outboxParentDir;
        fn.append("/");
        fn.append(entry->d_name);
        fn.append("/inbox");

        if (fn == _masterInbox) continue;

        struct stat sb;
        if (stat(fn.c_str(), &sb) == 0) {
            if (S_ISDIR(sb.st_mode)) {
                if (access(fn.c_str(), W_OK) < 0) {
                    std::cerr << "Cannot write to directory ";
                    perror(fn.c_str());
                    continue;
                }
                rv.push_back(fn);
            }
        } else {
            perror(fn.c_str());
        }
    }
    closedir(d);
    if (rv.size() > 0) {
        std::sort(rv.begin(), rv.end());
        sviter_t ni = rv.begin();
        sviter_t oi = _outboxes.begin();

        while (ni != rv.end()) {
            const std::string &newval = *ni;
            if (oi == _outboxes.end()) {
                std::cerr << "Found new slave inbox: " << newval << std::endl;
                ++ni;
                continue;
            }
            const std::string &oldval = *oi;
            if (newval == oldval) {
                ++ni;
                ++oi;
            } else if (newval < oldval) {
                std::cerr << "Found new slave inbox: " << newval << std::endl;
                ++ni;
            } else /* oldval < newval */ {
                std::cerr << "Slave inbox removed: " << oldval << std::endl;
                ++oi;
            }
        }
        _outboxes = rv;
        return true;
    }
    std::cerr << "Did not find any slave inboxes in: " << _outboxParentDir << std::endl;
    return false;
}

void
Application::moveLinks()
{
    for (sviter_t fni = _inboxFiles.begin(); fni != _inboxFiles.end(); ++fni) {
        const std::string& filename = *fni;
        size_t ldp = filename.rfind("/");
        std::string basename = filename.substr(ldp+1);
        for (sviter_t obi = _outboxes.begin(); obi != _outboxes.end(); ++obi) {
            std::string newFn = *obi;
            newFn.append("/");
            newFn.append(basename);

            std::cout << "linking " << filename << " -> " << newFn << std::endl;
            if (link(filename.c_str(), newFn.c_str()) < 0) {
                std::cerr << "linking " << filename << " -> " << newFn;
                perror("failed");
                return;
            }
        }
        if (unlink(filename.c_str()) < 0) {
            std::cerr << "cannot remove " << filename;
            perror(", error");
        }
    }
}


int
Application::Main()
{
    bool aborted = false;
    findOutboxes();

    try {
        while (!aborted) {
            if (scanInbox() && findOutboxes()) {
                moveLinks();
            } else {
                FastOS_Thread::Sleep(200);
            }
        }
    }
    catch(std::exception &e) {
        fprintf(stderr, "ERROR: %s\n", e.what());
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

} // namespace spoolmaster
