<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

## About the disk dictionary format

The designs of the disk index dictionary formats were incremental, due
to changing requirements over the years.

### 1st generation

Patricia tree in memory.

### 2nd generation, 1998-09-04

Problem: Dictionary did not fit in memory (machine had 512 MB ram)
when indexing 5 million web documents on a single machine.

Changed format to variable length records on disk, with a sparse
version of the dictionary in memory (each 256th word) to limit disk
access for binary search.

### 3rd generation, 2000-03-09

Problem: Too many disk read operations and too many bytes read from disk
(limited PCI bandwidth).

Changed format to a "paged" dictionary where a dictionary lookup would
use 1 disk read, reading 4 kiB of data. Data was not compressed. Could
not memory map whole dictionary. The sparse files were read into
memory and used to determine the page to use for further lookup.
Binary search within the pages read from disk.

### 4th generation, 2002-08-16

Problem: Dictionary used too much disk space.

Changed format to compressed format. Decompression could not contain
much state, thus delta values were compressed using exp golomb coding.

Two levels of skip lists within each page, where skip list on a level
contained enough information to skip on all levels below within the
same page.

Start of word was replaced by a byte telling how many bytes is
ommitted due to the prefix being common with previous words (word
before in dictionary and word before in the lookup order).

### 5th generation, 2010-08-21

Payload ("value") changed when skip information was added for large
posting lists. Added overflow handling for long words / huge payloads.
Added another level of pages ("sparse pages") to improve compression.

### 6th generation, 2015-05-12

Started using a separate dictionary for each index field instead of a
shared dictionary across all index fields. Minor changes.
