<!-- Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Contributing to Vespa
Contributions to [Vespa](http://github.com/vespa-engine/vespa),
[Vespa system tests](http://github.com/vespa-engine/system-test),
[Vespa samples](https://github.com/vespa-engine/sample-apps)
and the [Vespa documentation](http://github.com/vespa-engine/documentation) are welcome.
This documents tells you what you need to know to contribute.

## Open development
All work on Vespa happens directly on Github,
using the [Github flow model](https://guides.github.com/introduction/flow/).
We release the master branch a few times a week and you should expect it to almost always work.
In addition to the [public Travis build](https://travis-ci.org/vespa-engine/vespa) 
we have a large acceptance and performance test suite which
is also run continuously. We plan to add this to the open source code base later.

All pull requests are reviewed by a member of the Vespa Committers team.
You can find a suitable reviewer in the OWNERS file upward in the source tree from
where you are making the change (the OWNERS have a special responsibility for
ensuring the long-term integrity of a portion of the code).
If you want to become a committer/OWNER making some quality contributions is the way to start.


***Creating a Pull Request***

Please follow [best practices](https://github.com/trein/dev-best-practices/wiki/Git-Commit-Best-Practices) for creating git commits.

When your code is ready to be submitted, [submit a pull request](https://help.github.com/articles/creating-a-pull-request/) to begin the code review process.

We only seek to accept code that you are authorized to contribute to the project. We have added a pull request template on our projects so that your contributions are made with the following confirmation: 

> I confirm that this contribution is made under the terms of the license found in the root directory of this repository's source tree and that I have the authority necessary to make this contribution on behalf of its copyright owner.

## Versioning
Vespa uses semantic versioning - see
[vespa versions](http://docs.vespa.ai/en/vespa-versions.html).
Notice in particular that any Java API in a package having a @PublicAPI
annotation in the package-info file cannot be changed in an incompatible way
between major versions: Existing types and method signatures must be preserved
(but can be marked deprecated).

## Issues
We track issues in [GitHub issues](https://github.com/vespa-engine/vespa/issues).
It is fine to submit issues also for feature requests and ideas, whether or not you intend to work on them.

There is also a [ToDo list](TODO.md) for larger things which nobody are working on yet.

## Community
If you have questions, want to share your experience or help others, please join our community on [Stack Overflow](http://stackoverflow.com/questions/tagged/vespa).

### Getting started
See [README](README.md) for how to build and test Vespa. 

For an overview of the modules, see [Code-map.md](Code-map.md).
More details are in the READMEs of each module.

## License and copyright
If you add new files you are welcome to use your own copyright.
In any case the code (or documentation) you submit will be licensed
under the Apache 2.0 license.

## Code of Conduct

We encourage inclusive and professional interactions on our project. We welcome everyone to open an issue, improve the documentation, report bug or submit a pull request. By participating in this project, you agree to abide by the [Verizon Media Code of Conduct](Code-of-Conduct.md). If you feel there is a conduct issue related to this project, please raise it per the Code of Conduct process and we will address it.
