# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

# Hack to speed up jar packing for now. This does not affect the rpm size.
%define __jar_repack %{nil}

Name:           vespa-node-maintainer
Version:        %version
Release:        1%{?dist}
BuildArch:      noarch
Summary:        Vespa Node Maintainer
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash
Requires: java-1.8.0-openjdk-headless
Requires: vespa-base

Conflicts: vespa

%description
The Node Maintainer does various maintenance tasks on a node.


%install
mkdir -p %buildroot%_prefix/lib/jars
cp node-maintainer/target/node-maintainer-jar-with-dependencies.jar %buildroot%_prefix/lib/jars

%clean
rm -rf %buildroot

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
