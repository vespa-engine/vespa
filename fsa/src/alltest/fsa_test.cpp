// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdio.h>
#include <string>

#include <vespa/fsa/fsa.h>

using namespace fsa;

int main(int, char**)
{
  FSA *f = new FSA("__testfsa__.__fsa__", FILE_ACCESS_MMAP);
  FSA::State *fs = new FSA::State(*f);

  std::string s("cucu");
  fs->start(s);
  fs->delta('m');
  fs->delta("ber");
  if(fs->isFinal()){
    printf("start/delta test: string(\"cucu\")+'m'+\"ber\" is accepted\n");
    printf("                  data size: %d\n",fs->dataSize());
    printf("                  data string: \"%-*.*s\"\n",fs->dataSize(),fs->dataSize(),fs->data());
  }
  else {
    printf("start/delta test failed.\n");
  }

  const unsigned char *pb = fs->lookup("cucumber");
  if(pb!=NULL){
    printf("lookup test: \"cucumber\" -> \"%s\"\n",pb);
  }
  else{
    printf("lookup test: \"cucumber\" not found.\n");
  }


  FSA::HashedState *fs1 = new FSA::HashedState(*f);


  fs1->delta("pe");

  FSA::HashedState *fs2 = new FSA::HashedState(*fs1);
  FSA::HashedState *fs3 = new FSA::HashedState(*fs1);



  fs1->delta("a");
  fs2->delta("ach");
  fs3->delta("ar");

  if(fs1->isFinal() && fs2->isFinal()){
    printf("copy hashed state test:\n");
    printf("    \"pe\"+\"a\":    hash=%d, data_size=%d, data string=\"%-*.*s\"\n",
           fs1->hash(),fs1->dataSize(),fs1->dataSize(),fs1->dataSize(),fs1->data());
    printf("    \"pe\"+\"ach\":  hash=%d, data_size=%d, data string=\"%-*.*s\"\n",
           fs2->hash(),fs2->dataSize(),fs2->dataSize(),fs2->dataSize(),fs2->data());
    printf("    \"pe\"+\"ar\":   hash=%d, data_size=%d, data string=\"%-*.*s\"\n",
           fs3->hash(),fs3->dataSize(),fs3->dataSize(),fs3->dataSize(),fs3->data());

  }
  else {
    printf("copy hashed state test failed.\n");
  }

  printf("revLookup test:\n");
  unsigned int i=0;
  std::string res;
  while(i<100){
    res=fs2->revLookup(i);
    if(res.size()==0)
      break;
    fs2->lookup(res);
    printf("    %d -> %s -> %d\n",i,res.c_str(),fs2->hash());
    i++;
  }

  printf("iterator test:\n");
  fs1->start('p');
  printf("  possible continuations from \"p\":\n");
  for(FSA::iterator it(*fs1); it!=fs1->end(); ++it){
    printf("    \"p\" + \"%s\"\n",it->str().c_str());
  }

  delete fs;
  delete fs1;
  delete fs2;
  delete fs3;


  printf("counter/memory state test\n");
  FSA::CounterState *cs = new FSA::CounterState(*f);
  FSA::MemoryState *ms = new FSA::MemoryState(*f);

  cs->start("cucu");
  ms->start("cucu");
  printf("    \"cucu\" -> %s:%d\n",ms->memory().c_str(),cs->counter());

  cs->start("cucumber");
  ms->start("cucumber");
  printf("    \"cucumber\" -> %s:%d\n",ms->memory().c_str(),cs->counter());

  cs->start("cucumber slumber");
  ms->start("cucumber slumber");
  printf("    \"cucumber slumber\" -> %s:%d\n",ms->memory().c_str(),cs->counter());

  delete cs;
  delete ms;
  delete f;

  return 0;
}
