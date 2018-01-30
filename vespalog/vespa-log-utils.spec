# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa-log-utils
Version:        %version
Release:        1%{?dist}
BuildArch:      noarch
Summary:        Vespa Node Admin
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash

Conflicts: vespa

%description
Utilities for reading Vespa log files.

%install
bin_dir=%?buildroot%_prefix/bin
lev_dir=%?buildroot%_prefix/libexec/vespa
mkdir -p "$bin_dir"
mkdir -p "$lev_dir"
cp vespabase/src/common-env.sh "${lev_dir}"
cp vespalog/src/vespa-logfmt/vespa-logfmt.pl "${bin_dir}/vespa-logfmt"
chmod 444 "${lev_dir}/common-env.sh"
chmod 555 "${bin_dir}/vespa-logfmt"
ln -s "vespa-logfmt" "${bin_dir}/logfmt"

%clean
rm -rf %buildroot

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
