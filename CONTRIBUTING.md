<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Contributing to Vespa

Contributions to [Vespa](https://github.com/vespa-engine/vespa),
[Vespa system tests](https://github.com/vespa-engine/system-test),
[Vespa samples](https://github.com/vespa-engine/sample-apps)
and the [Vespa documentation](https://github.com/vespa-engine/documentation) are welcome.
This documents tells you what you need to know to contribute.

## Open development

All work on Vespa happens directly on GitHub,
using the [GitHub flow model](https://docs.github.com/en/get-started/quickstart/github-flow).
We release the master branch four times a week, and you should expect it to always work.
The continuous build of Vespa is at [https://factory.vespa.oath.cloud](https://factory.vespa.oath.cloud).
You can follow the fate of each commit there.

All pull requests must be approved by a
[Vespa Committer](https://github.com/orgs/vespa-engine/people).
You can find a suitable reviewer in the OWNERS file upward in the source tree from
where you are making the change (OWNERS have a special responsibility for
ensuring the long-term integrity of a portion of the code).

The way to become a committer (and OWNER) is to make some quality contributions
to an area of the code. See [GOVERNANCE](GOVERNANCE.md) for more details.

### Creating a Pull Request

Please follow
[best practices](https://github.com/trein/dev-best-practices/wiki/Git-Commit-Best-Practices)
for creating git commits.

When your code is ready to be submitted,
[submit a pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request)
to request a code review.

We only seek to accept code that you are authorized to contribute to the project.
We have added a pull request template on our projects so that your contributions are made
with the following confirmation:

> I confirm that this contribution is made under the terms of the license found in the root directory of this repository's source tree and that I have the authority necessary to make this contribution on behalf of its copyright owner.

## Versioning

Vespa uses semantic versioning - see
[vespa versions](https://vespa.ai/releases#versions).
Notice in particular that any Java API in a package having a @PublicAPI
annotation in the package-info, and no @Beta annotation on the class,
cannot be changed in an incompatible way between major versions:
Existing types and method signatures must be preserved
(but can be marked deprecated).

We verify ABI compatibility during the regular Java build you'll run with Maven (mvn install).
This build step will also fail if you _add_ to public APIs, which is fine if there's a good reason
to do it. In that case update the ABI spec as instructed in the error message.

## Issues

We track issues in [GitHub issues](https://github.com/vespa-engine/vespa/issues).
It is fine to submit issues also for feature requests and ideas, whether or not you intend to work on them.

There is also a [ToDo list](TODO.md) for larger things nobody is working on yet.

## Community

If you have questions, want to share your experience or help others,
join our [Slack channel](http://slack.vespa.ai).
See also [Stack Overflow questions tagged Vespa](https://stackoverflow.com/questions/tagged/vespa),
and feel free to add your own.

### Getting started

See [README](README.md) for how to build and test Vespa.
[Code-map.md](Code-map.md) provides an overview of the modules of Vespa.
More details are in the READMEs of each module.

## License and copyright

If you add new files you are welcome to use your own copyright.
In any case the code (or documentation) you submit will be licensed
under the Apache 2.0 license.
