# A map to the Vespa code base

You want to get familiar with the Vespa code base but don't know where to start?

Vespa consists of about 1.7 million lines of code, about equal parts Java and C++.
Since it it's mostly written by a team of developers selected for their ability 
to do this kind of thing unusually well, who have been given time to dedicate 
themselves to it for a long time, it is mostly easily to work with. However, one 
thing we haven't done is to create a module structure friendly to newcomers.  

This document aims to alleviate that somewhat by providing a map from the [[https://docs.vespa.ai/documentation/overview.html][functional elements] 
of Vespa to the top-level module structure 
[[https://github.com/vespa-engine/vespa][on Github]].

We'll start with the query and write paths, outside in.

## The stateless container

When a request is made to Vespa it first enters some stateless container cluster.
These containers consists of a core layer which provides general request-response
handling (using Jetty for HTTP), component management, configuration and similar 
basic functionality. 




