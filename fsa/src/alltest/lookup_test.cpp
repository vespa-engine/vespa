// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdlib.h>
#include <iostream>
#include <iomanip>
#include <string>

#include <vespa/fsa/fsa.h>

using namespace fsa;

int main(int argc, char** argv)
{

  if(argc!=2){
    std::cerr << "usage: lookup_test fsafile <input >output" << std::endl;
    return 1;
  }

  FSA f(argv[1]);
  FSA::HashedState fs(f);
  std::string input;

  while(!std::cin.eof()){
    getline(std::cin,input);

    if(input.size()>0){
      fs.start(input);
      if(fs.isFinal()){
        std::cout << "'" << input << "'" << " is accepted, hash value: " << fs.hash()
                  << ", data size: " << fs.dataSize()
                  << ", data string: \""
                  << std::setw(fs.dataSize()) << std::left << fs.data()
                  << "\"" << std::endl;
      }
      else{
        std::cout << "'" << input << "'" << " is not accepted." << std::endl;
      }
    }
  }

  return 0;
}
