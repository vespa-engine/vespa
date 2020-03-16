# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa-base
Version:        %version
Release:        1%{?dist}
BuildArch:      noarch
Summary:        Vespa common files
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash

Conflicts: vespa

%description
Common files for Vespa RPMs

%install
lev_dir=%?buildroot%_prefix/libexec/vespa
mkdir -p "$lev_dir"
cp vespabase/src/common-env.sh "${lev_dir}"
chmod 444 "${lev_dir}/common-env.sh"

%clean
rm -rf %buildroot

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d %{_prefix} -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
echo "pathmunge %{_prefix}/bin" > /etc/profile.d/vespa.sh
echo "export VESPA_HOME=%{_prefix}" >> /etc/profile.d/vespa.sh
chmod +x /etc/profile.d/vespa.sh
exit 0

%postun
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
    ! getent passwd vespa >/dev/null || userdel vespa
    ! getent group vespa >/dev/null || groupdel vespa
fi

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
