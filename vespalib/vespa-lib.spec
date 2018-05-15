# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa-lib
Version:        %version
Release:        1%{?dist}
BuildArch:      x86_64
Summary:        Vespa common libraries
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash

Conflicts: vespa

%description
Common libraries and binaries for Vespa RPMs

%install
bin_dir=%?buildroot%_prefix/bin
lib_dir=%?buildroot%_prefix/lib
mkdir -p "$bin_dir"
mkdir -p "$lib_dir"
cp vespalib/src/apps/vespa-detect-hostname/vespa-detect-hostname "${bin_dir}"
cp vespalib/src/apps/vespa-validate-hostname/vespa-validate-hostname "${bin_dir}"
cp fastos/src/vespa/fastos/libfastos.so "${lib_dir}"
cp vespalog/src/vespa/log/libvespalog.so "${lib_dir}"
cp vespalib/src/vespa/vespalib/libvespalib.so "${lib_dir}"

%clean
rm -rf %buildroot

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
