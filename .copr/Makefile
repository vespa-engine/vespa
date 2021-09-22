# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
TOP = $(realpath $(dir $(lastword $(MAKEFILE_LIST))))

# Version
VESPA_VERSION = $(shell git tag --points-at HEAD | grep -oP "\d+\.\d+\.\d+" | sort -V | tail -1)

RPMTOPDIR := $(HOME)/rpmbuild
SOURCEDIR := $(RPMTOPDIR)/SOURCES
SPECDIR := $(RPMTOPDIR)/SPECS
SPECFILE := $(SPECDIR)/vespa-$(VESPA_VERSION).spec

srpm:
	dnf install -y git rpmdevtools
	$(TOP)/../dist.sh $(VESPA_VERSION)
	spectool -g -C $(SOURCEDIR) $(SPECFILE)
	rpmbuild -bs --define "_topdir $(RPMTOPDIR)" $(SPECFILE)
	cp -a $(RPMTOPDIR)/SRPMS/* $(outdir)
clean:
	-rm -rf $(RPMTOPDIR)

.PHONY: srpm clean
