// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <fcntl.h>

#include <iostream>
#include <fstream>

#include <vespa/fsa/fsa.h>

using namespace fsa;

void usage(const char *name, const char *errormsg = NULL)
{
  if(errormsg!=NULL){
    fprintf(stderr,"%s: %s\n",name,errormsg);
  }
  fprintf(stderr,"usage:\n");
  fprintf(stderr,"    %s [OPTIONS] fsa\n",name);
  fprintf(stderr,"\n");
  fprintf(stderr,"      Valid options are:\n");
  fprintf(stderr,"      -h       display this help\n");
  fprintf(stderr,"      -V       display version number\n");
  fprintf(stderr,"\n");
}

void version()
{
  std::cout << "fsainfo "
            << FSA::VER/1000000 << "." << (FSA::VER/1000)%1000 << "." << FSA::VER%1000;
  if(FSA::VER != FSA::libVER()){
    std::cout << " (library "
              << FSA::libVER()/1000000 << "." << (FSA::libVER()/1000)%1000 << "." << FSA::libVER()%1000
              << ")";
  }
  std::cout << std::endl;
}

int main(int argc, char** argv)
{
  const char *fsa_file;

  int          opt;
  extern int   optind;

  while((opt=getopt(argc,argv,"hV")) != -1){
    switch(opt){
    case 'h':
      usage(argv[0]);
      return 0;
    case 'V':
      version();
      return 0;
    case '?':
      usage(argv[0],"unrecognized option");
      return 1;
    }
  }

  if(optind!=argc-1){
    usage(argv[0],"required parameter fsa is missing");
    return 1;
  }

  fsa_file = argv[optind];



  FSA::Header header;

  size_t r;

  int fd = ::open(fsa_file,O_RDONLY);
  if(fd<0){
    std::cerr << "Failed to open fsa file (" << fsa_file << ")" << std::endl;
    return 1;
  }
  else{
    r=::read(fd,&header,sizeof(header));
    ::close(fd);
    if(r<sizeof(header) || header._magic!=FSA::MAGIC){
      std::cout << "Unrecognized file format (" << fsa_file << ")\n";
    }
    else if(header._version<1000){
      std::cout << "Obsolete fsa file (" << fsa_file << ")\n";
    }
    else {
      std::cout << "Information about " << fsa_file << ":\n";
      std::cout << "  Header size:       " << sizeof(header)    << " bytes" <<std::endl;
      std::cout << "  Magic:             " << header._magic     << std::endl;
      std::cout << "  Version:           " << header._version/1000000 << "."
                                           << (header._version%1000000)/1000 << "."
                                           << header._version%1000 << std::endl;
      std::cout << "  Serial number:     " << header._serial    << std::endl;
      std::cout << "  Checksum:          " << header._checksum  << std::endl;
      std::cout << "  FSA size:          " << header._size      << " cells" <<std::endl;
      std::cout << "                     " << header._size*(sizeof(unsigned char)+sizeof(unsigned int))
                                       << " bytes" <<std::endl;
      std::cout << "  Start state:       " << header._start     << std::endl;
      std::cout << "  Data size:         " << header._data_size << " bytes" << std::endl;
      std::cout << "  Data item type:    " << (header._data_type==FSA::DATA_FIXED?
                                          "fixed size":"variable size") << std::endl;
      if(header._data_type==FSA::DATA_FIXED)
        std::cout << "  Fixed item size:   " << header._fixed_data_size << std::endl;
      std::cout << "  Perfect hash:      " << (header._has_perfect_hash?
                                          "yes":"no") << std::endl;
      if(header._has_perfect_hash)
        std::cout << "  Perfect hash size: " << header._size*sizeof(unsigned int) << " bytes" << std::endl;
      std::cout << "  Total size:        "
                << (header._size*(sizeof(unsigned char)+
                                  sizeof(unsigned int)*(header._has_perfect_hash?2:1)) +
                    header._data_size +
                    sizeof(header))
                << " bytes" << std::endl;
      std::cout << "  Trying to load FSA ... " << std::flush;

      FSA fsa(fsa_file);
      std::cout << (fsa.version()==header._version ? "succeeded.":"failed.") << std::endl;
    }
  }

  return 0;
}
