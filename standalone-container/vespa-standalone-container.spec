# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

# Hack to speed up jar packing for now. This does not affect the rpm size.
%define __jar_repack %{nil}

Name:           vespa-standalone-container
Version:        %version
BuildArch:      noarch
Release:        1%{?dist}
Summary:        Vespa standalone JDisc container
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash
Requires: java-1.8.0-openjdk-headless
Requires: vespa-base

Conflicts: vespa

%description
The Vespa standalone JDisc container is a runtime environment for Java
applications.

%install
declare jars_dir=%buildroot%_prefix/lib/jars
mkdir -p "$jars_dir"

declare -a dirs=(
  jdisc_http_service/target/dependency
  jdisc_jetty/target/dependency
  vespa_jersey2/target/dependency
)
for dir in "${dirs[@]}"; do
  cp "$dir"/* "$jars_dir"
done

declare -a modules=(
  component
  config-bundle
  config-model-api
  config-model
  config-provisioning
  configdefinitions
  container-disc
  container-jersey2
  container-search-and-docproc
  defaults
  docprocs
  jdisc-security-filters
  jdisc_core
  jdisc_http_service
  simplemetrics
  standalone-container
  vespa-athenz
  vespaclient-container-plugin
  zkfacade
)
for module in "${modules[@]}"; do
    cp "$module"/target/"$module"-jar-with-dependencies.jar "$jars_dir"
done

# vespajlib must be installed _without_ dependencies.
cp vespajlib/target/vespajlib.jar "$jars_dir"

declare -a libexec_files=(
  standalone-container/src/main/sh/standalone-container.sh
)
declare libexec_dir=%buildroot%_prefix/libexec/vespa
mkdir -p "$libexec_dir"
for file in "${libexec_files[@]}"; do
  cp "$file" "$libexec_dir"
done

%clean
rm -rf %buildroot

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d %_prefix -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
echo "pathmunge %_prefix/bin" > /etc/profile.d/vespa.sh
echo "export VESPA_HOME=%_prefix" >> /etc/profile.d/vespa.sh
chmod +x /etc/profile.d/vespa.sh

%postun
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
    ! getent passwd vespa >/dev/null || userdel vespa
    ! getent group vespa >/dev/null || groupdel vespa
fi

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
