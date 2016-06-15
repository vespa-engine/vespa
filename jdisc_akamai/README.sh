#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

CURRENT=$(date -u '+%F %T %Z')

if [ -z ${VERSION} ]; then
    echo "ERROR: No version number defined";
    exit 1;
fi

cat <<EOF

Akamai rotation package for jdisc

This package installs a small http://host:port/akamai page (by default) to be served by the application. By default the file resides in $VESPA_HOME/libexec/jdisc/akamai .

Note: The directory and file name can be changed through this package. However, if you do decide to change the directory, it is important to update the corresponding servingDirectory URI binding as well in your application.

To add the jdisc application server to the rotation, install this package, then:

 $ $VESPA_HOME/bin/ystatus start jdisc_akamai

To remove the tomcat server from the vip rotation:

 $ $VESPA_HOME/bin/ystatus stop jdisc_akamai

Installing, activating, or deactivating this package will never cause your jdisc application to restart.

Environemtn settings:

Defaults are in [], options are in {}.

jdisc_akamai.boot:		[autostart]	{autostart|autostop} always start/stop on boot

jdisc_akamai.data_extended:	[0] 		{0|1} print out extra data to akamai

jdisc_akamai.email:		[unset]		{email address} all start action will be emailed to the specified email address.

jdisc_akamai.manage_ymon_notifications: [0] 	{0,1} does a best-effort attempt at changing ymon notifications; there may be problems changing notifications that this script does not catch.

jdisc_akamai.msg		[unset]		{message} Display a set message whenever the package is started. After the message is displayed, the user must confirm that they really mean to put jdisc back to rotation.

jdisc_akamai.status_file	[$VESPA_HOME/libexec/jdisc/akamai]	{path} Path to status file.


ChangeLog:

Version ${VERSION}
  * Prepended \$(ROOT) to status file

Version 1.0.2
  * Moved to jdisc_bundles

Version 1.0.1
  * Bumped version to upload to quarantine

Version 1.0.0
  * Initial version

EOF
