# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Hack to speed up jar packing for now
%define __jar_repack %{nil}

# Force special prefix for Vespa
%define _prefix /opt/vespa

Name:           vespa
Version:        7.16.38
Release:        1%{?dist}
Summary:        Vespa - The open big data serving engine
Group:          Applications/Databases
License:        Commercial
URL:            http://vespa.ai
Source0:        vespa-%{version}.tar.gz

%if 0%{?centos}
BuildRequires: epel-release
BuildRequires: centos-release-scl
BuildRequires: devtoolset-7-gcc-c++
BuildRequires: devtoolset-7-libatomic-devel
BuildRequires: devtoolset-7-binutils
BuildRequires: rh-maven35
%define _devtoolset_enable /opt/rh/devtoolset-7/enable
%define _rhmaven35_enable /opt/rh/rh-maven35/enable
%endif
%if 0%{?fedora}
BuildRequires: gcc-c++
BuildRequires: libatomic
%endif
BuildRequires: Judy-devel
%if 0%{?centos}
BuildRequires: cmake3
BuildRequires: llvm5.0-devel
BuildRequires: vespa-boost-devel >= 1.59.0-6
BuildRequires: vespa-gtest >= 1.8.1-1
%endif
%if 0%{?fedora}
BuildRequires: cmake >= 3.9.1
BuildRequires: maven
%if 0%{?fc27}
BuildRequires: llvm-devel >= 5.0.2
BuildRequires: boost-devel >= 1.64
BuildRequires: vespa-gtest >= 1.8.1-1
%endif
%if 0%{?fc28}
BuildRequires: llvm-devel >= 6.0.1
BuildRequires: boost-devel >= 1.66
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc29}
BuildRequires: llvm-devel >= 7.0.0
BuildRequires: boost-devel >= 1.66
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%if 0%{?fc30}
BuildRequires: llvm-devel >= 7.0.0
BuildRequires: boost-devel >= 1.66
BuildRequires: gtest-devel
BuildRequires: gmock-devel
%endif
%endif
BuildRequires: xxhash-devel >= 0.6.5
BuildRequires: lz4-devel
BuildRequires: libzstd-devel
BuildRequires: zlib-devel
BuildRequires: libicu-devel
BuildRequires: java-11-openjdk-devel
BuildRequires: openssl-devel
BuildRequires: rpm-build
BuildRequires: make
BuildRequires: vespa-cppunit-devel >= 1.12.1-6
BuildRequires: systemd
BuildRequires: flex >= 2.5.0
BuildRequires: bison >= 3.0.0
%if 0%{?centos}
Requires: epel-release
%endif
Requires: which
Requires: initscripts
Requires: perl
Requires: perl-Carp
Requires: perl-Data-Dumper
Requires: perl-Digest-MD5
Requires: perl-Env
Requires: perl-Exporter
Requires: perl-File-Path
Requires: perl-File-Temp
Requires: perl-Getopt-Long
Requires: perl-IO-Socket-IP
Requires: perl-JSON
Requires: perl-libwww-perl
Requires: perl-Net-INET6Glue
Requires: perl-Pod-Usage
Requires: perl-URI
Requires: valgrind
Requires: Judy
Requires: xxhash
Requires: xxhash-libs >= 0.6.5
Requires: lz4
Requires: libzstd
Requires: zlib
Requires: libicu
Requires: perf
Requires: gdb
Requires: net-tools
%if 0%{?centos}
Requires: llvm5.0
%define _vespa_llvm_version 5.0
%define _extra_link_directory /usr/lib64/llvm5.0/lib;/opt/vespa-gtest/lib64;/opt/vespa-cppunit/lib
%define _extra_include_directory /usr/include/llvm5.0;/opt/vespa-boost/include;/opt/vespa-gtest/include;/opt/vespa-cppunit/include
%endif
%if 0%{?fedora}
%if 0%{?fc27}
Requires: llvm-libs >= 5.0.2
%define _vespa_llvm_version 5.0
%define _vespa_gtest_link_directory /opt/vespa-gtest/lib64
%define _vespa_gtest_include_directory /opt/vespa-gtest/include
%endif
%if 0%{?fc28}
Requires: llvm-libs >= 6.0.1
%define _vespa_llvm_version 6.0
%endif
%if 0%{?fc29}
Requires: llvm-libs >= 7.0.0
%define _vespa_llvm_version 7
%endif
%if 0%{?fc30}
Requires: llvm-libs >= 7.0.0
%define _vespa_llvm_version 7
%endif
%define _extra_link_directory /opt/vespa-cppunit/lib%{?_vespa_llvm_link_directory:;%{_vespa_llvm_link_directory}}%{?_vespa_gtest_link_directory:;%{_vespa_gtest_link_directory}}
%define _extra_include_directory /opt/vespa-cppunit/include%{?_vespa_llvm_include_directory:;%{_vespa_llvm_include_directory}}%{?_vespa_gtest_include_directory:;%{_vespa_gtest_include_directory}}
%endif
Requires: java-11-openjdk
Requires: openssl
Requires: vespa-cppunit >= 1.12.1-6
Requires(pre): shadow-utils

# Ugly workaround because vespamalloc/src/vespamalloc/malloc/mmap.cpp uses the private
# _dl_sym function.
Provides: libc.so.6(GLIBC_PRIVATE)(64bit)

%description

Vespa - The open big data serving engine

%prep
%setup -q

%build
%if 0%{?_devtoolset_enable:1}
source %{_devtoolset_enable} || true
%endif
%if 0%{?_rhmaven35_enable:1}
source %{_rhmaven35_enable} || true
%endif

export PATH="/usr/lib/jvm/java-11-openjdk/bin:$PATH"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
export FACTORY_VESPA_VERSION=%{version}

sh bootstrap.sh java
mvn --batch-mode -nsu -T 1C  install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
cmake3 -DCMAKE_INSTALL_PREFIX=%{_prefix} \
       -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk \
       -DEXTRA_LINK_DIRECTORY="%{_extra_link_directory}" \
       -DEXTRA_INCLUDE_DIRECTORY="%{_extra_include_directory}" \
       -DCMAKE_INSTALL_RPATH="%{_prefix}/lib64%{?_extra_link_directory:;%{_extra_link_directory}};/usr/lib/jvm/jre-11-openjdk/lib" \
       %{?_vespa_llvm_version:-DVESPA_LLVM_VERSION="%{_vespa_llvm_version}"} \
       .

make %{_smp_mflags}

%install
rm -rf $RPM_BUILD_ROOT
make install DESTDIR=%{buildroot}

mkdir -p %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa.service %{buildroot}/usr/lib/systemd/system
cp %{buildroot}/%{_prefix}/etc/systemd/system/vespa-configserver.service %{buildroot}/usr/lib/systemd/system

%clean
rm -rf $RPM_BUILD_ROOT

%pre
getent group vespa >/dev/null || groupadd -r vespa
getent passwd vespa >/dev/null || \
    useradd -r -g vespa -d %{_prefix} -s /sbin/nologin \
    -c "Create owner of all Vespa data files" vespa
echo "pathmunge %{_prefix}/bin" > /etc/profile.d/vespa.sh
echo "export VESPA_HOME=%{_prefix}" >> /etc/profile.d/vespa.sh
chmod +x /etc/profile.d/vespa.sh
exit 0

%post
%systemd_post vespa-configserver.service
%systemd_post vespa.service

%preun
%systemd_preun vespa.service
%systemd_preun vespa-configserver.service

%postun
%systemd_postun_with_restart vespa.service
%systemd_postun_with_restart vespa-configserver.service
if [ $1 -eq 0 ]; then # this is an uninstallation
    rm -f /etc/profile.d/vespa.sh
    ! getent passwd vespa >/dev/null || userdel vespa
    ! getent group vespa >/dev/null || groupdel vespa
fi

%files
%defattr(-,vespa,vespa,-)
%doc
%{_prefix}/*
%config(noreplace) %{_prefix}/conf/logd/logd.cfg
%config(noreplace) %{_prefix}/conf/vespa/default-env.txt
%config(noreplace) %{_prefix}/etc/vespamalloc.conf
%attr(644,root,root) /usr/lib/systemd/system/vespa.service
%attr(644,root,root) /usr/lib/systemd/system/vespa-configserver.service

%changelog
