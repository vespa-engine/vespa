// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fsa/fsa.h>
#include <vespa/fsamanagers/fsamanager.h>

#include <iostream>
#include <string>
#include <stdlib.h>

using namespace fsa;

int main(int argc, char** argv)
{
  if(argc<3){
    std::cerr << "usage: fsamanager_test cache_dir fsa_file_or_url [fsa_file_or_url ...]\n";
    return 1;
  }

  FSAManager::instance().setCacheDir(argv[1]);

  for(int i=2;i<argc;i++){
    std::cerr << "Loading " << argv[i] << " ... ";
    std::cerr << (FSAManager::instance().load(argv[i],argv[i]) ? "ok":"failed") << "\n";
  }

}
