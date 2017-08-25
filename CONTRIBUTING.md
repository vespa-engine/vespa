# Contributing to Vespa
We appreciate contributions to Vespa!
Below is a quick how-to.


## Reporting issues
Reporting a problem is a valuable contribution.
Use [GitHub issues](https://github.com/vespa-engine/vespa/issues) to report bugs.
Issues are evaluated daily.
If you read this, you are probably a developer who knows how to write good bug reports - 
make it easy to for others to reproduce the problem (include a test case!),
include the Vespa version,
and make it easy for others to understand the importance of the problem.


## Check the ToDo list
Future features are kept on the [ToDo list](TODO.md) - 
minor fixes better reported and tracked in [issues](https://github.com/vespa-engine/vespa/issues).


## Versioning
Vespa uses semantic versioning,
read [this guide](http://docs.vespa.ai/documentation/vespa-versions.html) to understand
deprecations and changes to APIs and stored data across releases.
Vespa releases more often than weekly, and the team does not write release notes or a changelog - 
instead, track issues labeled _Feature_.


## Build custom plugins
Vespa has great support for custom plugins -
you will often find that the best way to implement your application is by writing a plugin -
refer to the [APIs](http://docs.vespa.ai/documentation/api.html).


## Where to start contributing
Most features plug into the [Vespa Container](docs.vespa.ai/documentation/jdisc/index.html) -
this is the most likely place to write enhancements.
Discuss with the community if others have similar feature requests - make the feature generic.

### Getting started
See [README](README.md) for how to build and test Vespa. 
<!-- Do we have a link to code conventions - or just use below? -->
Java coding guidelines:
* 4 spaces indent  <!-- Line width? -->
* Use Java coding standards
* No wildcard imports

### Pull requests
The Vespa Team evaluates pull requests as fast as we can.
File an issue that you can refer to in the pull request -
The issue can be valid even though a pull request will not be merged.
Also add `Closes #XXX` or `Fixes #XXX` in commits - this will auto-close the issue.
The Vespa Team work on the master branch, and does not have branches for other major versions - 
the current major version is the only active.
Submit unit tests with the changes and [update documentation](https://github.com/vespa-engine/documentation).
<!--  Do we need a Signed-off-by: Joe Smith <joe.smith@email.com> -->


## Community
List here - Slack channel?
