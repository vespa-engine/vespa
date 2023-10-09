// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>

#include <vespa/fsa/conceptnet.h>
#include <vespa/fsamanagers/conceptnetmanager.h>

using namespace fsa;

int main(int argc, char **argv)
{
  int          opt;
  //extern char *optarg;
  extern int   optind;

  bool do_ext = false, do_assoc = false, do_cat = false;

  while((opt=getopt(argc,argv,"aec")) != -1){
    switch(opt){
    case 'a':
      do_assoc = true;
      break;
    case 'e':
      do_ext = true;
      break;
    case 'c':
      do_cat = true;
      break;
    case '?':
      fprintf(stderr,"conceptnet_test: unrecognized option");
      return 1;
    }
  }

  if(optind>=argc){
    fprintf(stderr,"usage: conceptnet_test [-aec] DOMAIN [UNIT ...]\n");
    return 1;
  }

  std::string domain = argv[optind];

  if(!ConceptNetManager::instance().load(domain,
                                         domain + ".fsa",
                                         domain + ".dat")){
    fprintf(stderr,"failed to load concept net %s\n",domain.c_str());
    return 1;
  }

  ConceptNet::Handle* cn = ConceptNetManager::instance().get(domain);

  if(cn!=NULL){
    for(int i=optind+1;i<argc;i++){
      int idx = (*cn)->lookup(argv[i]);
      printf("%s(%d) : (%d,%d,%d,%d) (%f,%f)\n",argv[i],idx,
             (*cn)->frq(idx),(*cn)->cFrq(idx),(*cn)->qFrq(idx),(*cn)->sFrq(idx),
             (*cn)->score(idx),(*cn)->strength(idx));
      if(do_ext){
        for(int e = 0; e<(*cn)->numExt(idx); e++){
          printf("  %s, %d\n",(*cn)->lookup((*cn)->ext(idx,e)),(*cn)->extFrq(idx,e));
        }
      }
      if(do_assoc){
        for(int a = 0; a<(*cn)->numAssoc(idx); a++){
          printf("  %s, %d\n",(*cn)->lookup((*cn)->assoc(idx,a)),(*cn)->assocFrq(idx,a));
        }
      }
      if(do_cat){
        for(int c = 0; c<(*cn)->numCat(idx); c++){
          printf("    %s\n",(*cn)->catName((*cn)->cat(idx,c)));
        }
      }
    }
  } else {
    fprintf(stderr,"failed to load concept net %s\n",domain.c_str());
    return 1;
  }
  return 0;
}
