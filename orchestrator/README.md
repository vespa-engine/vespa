<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Orchestrator, a.k.a. Maestro
A service to facilitate safe and staggered restart and upgrades of services in a Vespa instance.
It uses consolidated information from Slobrok and the application model to decide if a hosts
should be allowed to stop its services.

## TODO:
* Constraint on requests on start-up.
* Constraint on requests after permitting host to go down (it should last at least as long as Slobrok heartbeat cycle).
* Implement caching of host-down decisions.
* Instance resource, exposing the orchestrators current knowledge
