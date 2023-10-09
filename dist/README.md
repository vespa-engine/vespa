<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Building Vespa RPM

To manually build a Vespa RPM on CentOS 7:

1. Add repo: ```yum-config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-7/group_vespa-vespa-epel-7.repo```
2. Inspect vespa.spec and install all RPM's marked with BuildRequires.
1. In the root of the Vespa source tree, execute : ```./dist.sh <Vespa version>```
1. Build the RPM with : ```rpmbuild -bb ~/rpmbuild/SPECS/vespa-<Vespa version>.spec```

The RPM should now be available in ~/rpmbuild/RPMS.

