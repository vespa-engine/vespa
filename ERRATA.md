<!-- Copyright verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

## Errata

### 2020-11-30: Document inconsistency
This bug existed between 2020-08-28 and 2020-09-23.
The following needs to happen to trigger the bug:

* Visibility delay is non-zero.
* A new config change is deployed that contains changes to proton.
  This config snapshot is stored in the transaction log on the content node.
* vespa-proton-bin is restarted, and as part of the prepare for restart step,
  at least one attribute vector is not flushed to the current serial number.
* Due to the bug, replay of the transaction log will fail to replay feed operations to attributes after replaying the config change.
  The effect is that all attributes that were not flushed as part of prepare for restart
  will not get any of the updates since the last time they were flushed.
* If a document was previously removed, lid space compaction will move another document to that local document id.
  Due to later missing updates as part of restarting we might see values from the removed document for some of the attributes.
* When the problem attributes are later flushed this inconsistency will be permanent.

Solution:
* Upgrade Vespa to at least vespa-7.306.19-1.el7.x86_64.rpm.
* Complete re-feed of the corpus.



### 2020-11-30: Regression introduced in Vespa 7.141 may cause data loss or inconsistencies when using 'create: true' updates
There exists a regression introduced in Vespa 7.141 where updates marked as `create: true` (i.e. create if missing)
may cause data loss or undetected inconsistencies in certain edge cases.
This regression was introduced as part of an optimization effort to greatly reduce the common-case overhead of updates
when replicas are out of sync.

Fixed in Vespa 7.157.9 and beyond.
If running a version affected (7.141 up to and including 7.147) you are strongly advised to upgrade.

See [#11686](https://github.com/vespa-engine/vespa/issues/11686) for details.
