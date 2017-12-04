# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

# Hack to speed up jar packing for now. This does not affect the rpm size.
%define __jar_repack %{nil}

Name:           vespa-jdisc-container
Version:        %version
BuildArch:      noarch
Release:        1%{?dist}
Summary:        Vespa standalone JDisc container
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash
Requires: java-1.8.0-openjdk-headless

Conflicts: vespa

%description
The Vespa standalone JDisc container is a runtime environment for Java
applications.

%install
declare jars_dir=%buildroot%_prefix/lib/jars
mkdir -p "$jars_dir"

declare -a dirs=(
  jdisc_jetty/target/dependency
  vespa_jersey2/target/dependency
)
for dir in "${dirs[@]}"; do
  cp "$dir"/* "$jars_dir"
done

declare -a files=(
  component/target/component-jar-with-dependencies.jar
  config-bundle/target/config-bundle-jar-with-dependencies.jar
  config-model-api/target/config-model-api-jar-with-dependencies.jar
  config-model/target/config-model-jar-with-dependencies.jar
  config-provisioning/target/config-provisioning-jar-with-dependencies.jar
  configdefinitions/target/configdefinitions-jar-with-dependencies.jar
  container-disc/target/container-disc-jar-with-dependencies.jar
  container-jersey2/target/container-jersey2-jar-with-dependencies.jar
  container-search-and-docproc/target/container-search-and-docproc-jar-with-dependencies.jar
  defaults/target/defaults-jar-with-dependencies.jar
  docprocs/target/docprocs-jar-with-dependencies.jar
  jdisc_core/target/dependency/vespajlib.jar
  jdisc_core/target/jdisc_core-jar-with-dependencies.jar
  jdisc_http_service/target/jdisc_http_service-jar-with-dependencies.jar
  simplemetrics/target/simplemetrics-jar-with-dependencies.jar
  standalone-container/target/standalone-container-jar-with-dependencies.jar
  vespaclient-container-plugin/target/vespaclient-container-plugin-jar-with-dependencies.jar
  vespajlib/target/vespajlib.jar
  zkfacade/target/zkfacade-jar-with-dependencies.jar
)
for file in "${files[@]}"; do
  cp "$file" "$jars_dir"
done

declare -a libexec_files=(
  vespabase/src/common-env.sh
  standalone-container/src/main/sh/jdisc-container
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
rm -f /etc/profile.d/vespa.sh
userdel vespa

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
