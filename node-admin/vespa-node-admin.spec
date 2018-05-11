# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Force special prefix for Vespa
%define _prefix /opt/vespa

# Hack to speed up jar packing for now. This does not affect the rpm size.
%define __jar_repack %{nil}

Name:           vespa-node-admin
Version:        %version
Release:        1%{?dist}
BuildArch:      noarch
Summary:        Vespa Node Admin
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai

Requires: bash
Requires: java-1.8.0-openjdk-headless
Requires: vespa-base = %{version}
Requires: vespa-standalone-container = %{version}
Requires: vespa-node-maintainer = %{version}
Requires: vespa-log-utils = %{version}

Conflicts: vespa

%description
The Node Admin manages the machine so it is a suitable host for one or more
Vespa nodes.

%install
app_dir=%?buildroot%_prefix/conf/node-admin-app
mkdir -p "$app_dir"/components
cp node-admin/src/main/application/services.xml "$app_dir"

declare -a jar_components=(
  node-admin/target/node-admin-jar-with-dependencies.jar
  docker-api/target/docker-api-jar-with-dependencies.jar
)
for path in "${jar_components[@]}"; do
  cp "$path" "$app_dir"/components
done

mkdir -p %buildroot%_prefix/libexec/vespa
cp node-admin/src/main/sh/node-admin.sh %buildroot%_prefix/libexec/vespa

mkdir -p %buildroot%_prefix/libexec/vespa/node-admin
cp node-admin/scripts/maintenance.sh %buildroot%_prefix/libexec/vespa/node-admin

%clean
rm -rf %buildroot

%files
%defattr(-,vespa,vespa,-)
%_prefix/*
