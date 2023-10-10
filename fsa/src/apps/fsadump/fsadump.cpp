// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>

#include <iostream>
#include <fstream>

#include <vespa/fsa/base64.h>
#include <vespa/fsa/fsa.h>
#include <vespa/fsa/automaton.h>

using namespace fsa;

enum FSA_Input_Format {
  OUTPUT_UNDEF,
  OUTPUT_TEXT,
  OUTPUT_TEXT_EMPTY,
  OUTPUT_TEXT_NUM,
  OUTPUT_BINARY,
  OUTPUT_BINARY_RAW,
  OUTPUT_PHASH,
  OUTPUT_DOT
};

void error(const char *name, const char *errormsg = NULL)
{
  if(errormsg!=NULL){
    fprintf(stderr,"%s: %s\n",name,errormsg);
  }
}

void usage(const char *name, const char *errormsg = NULL)
{
  error(name,errormsg);
  fprintf(stderr,"usage:\n");
  fprintf(stderr,"    %s [OPTIONS] fsafile\n",name);
  fprintf(stderr,"\n");
  fprintf(stderr,"      Valid options are:\n");
  fprintf(stderr,"      -h         display this help\n");
  fprintf(stderr,"      -b         use binary output format with Base64 encoded info\n");
  fprintf(stderr,"      -B         use binary output format with raw info\n");
  fprintf(stderr,"      -e         use text output format with no info (default)\n");
  fprintf(stderr,"      -n         use text output format with (unsigned) numerical info\n");
  fprintf(stderr,"      -t         use text input format\n");
  fprintf(stderr,"      -p         use perfect hash value instead of meta info (text output)\n");
  fprintf(stderr,"      -d         output dot format\n");
  fprintf(stderr,"      -V         display version number\n");
  fprintf(stderr,"\n");
}

void version()
{
  std::cout << "fsadump "
            << FSA::VER/1000000 << "." << (FSA::VER/1000)%1000 << "." << FSA::VER%1000;
  if(FSA::VER != FSA::libVER()){
    std::cout << " (library "
              << FSA::libVER()/1000000 << "." << (FSA::libVER()/1000)%1000 << "." << FSA::libVER()%1000
              << ")";
  }
  std::cout << std::endl;
}


namespace {

template <typename T>
T
read_unaligned(const void* data)
{
    T value;
    memcpy(&value, data, sizeof(T));
    return value;
}

}

int main(int argc, char** argv)
{
  FSA_Input_Format  format = OUTPUT_UNDEF;
  const char *input_file;

  int          opt;
  extern int   optind;

  while((opt=getopt(argc,argv,"ebBhntpdV")) != -1){
    switch(opt){
    case 'b':
      format = OUTPUT_BINARY;
      break;
    case 'B':
      format = OUTPUT_BINARY_RAW;
      break;
    case 'h':
      usage(argv[0]);
      return 0;
    case 'V':
      version();
      return 0;
    case 't':
      format = OUTPUT_TEXT;
      break;
    case 'n':
      format = OUTPUT_TEXT_NUM;
      break;
    case 'e':
      format = OUTPUT_TEXT_EMPTY;
      break;
    case 'p':
      format = OUTPUT_PHASH;
      break;
    case 'd':
      format = OUTPUT_DOT;
      break;
    case '?':
      usage(argv[0],"unrecognized option");
      return 1;
    }
  }

  if(optind!=argc-1){
    usage(argv[0],"required parameter(s) missing");
    return 1;
  }

  if(format==OUTPUT_UNDEF) // use default format (warning?)
    format=OUTPUT_TEXT_EMPTY;

  input_file = argv[optind];

  FSA fsa(input_file);

  if(!fsa.isOk()){
    std::cerr << "Failed to open fsa file (" << input_file << ")" << std::endl;
    return 1;
  }

  std::string meta,temp;
  uint32_t num_meta;
  uint32_t lines=0;

  if(format!=OUTPUT_DOT){

    for(FSA::iterator it(fsa); it!=fsa.end(); ++it){

      switch(format){
      case OUTPUT_BINARY:
        temp.assign((const char *)(it->data()),it->dataSize());
        Base64::encode(temp,meta);
        std::cout << it->str() << '\0' << meta << '\0';
        break;
      case OUTPUT_BINARY_RAW:
        meta.assign((const char *)(it->data()),it->dataSize());
        std::cout << it->str() << '\0' << meta << '\0';
        break;
      case OUTPUT_TEXT:
        meta.assign((const char *)(it->data()),it->dataSize());
        if(meta.size()>0 && meta[meta.size()-1]==0){
          meta.resize(meta.size()-1);
        }
        std::cout << it->str() << '\t' << meta << '\n';
        break;
      case OUTPUT_TEXT_NUM:
        switch(it->dataSize()){
        case 1:
          num_meta = *((const uint8_t*)it->data());
          break;
        case 2:
        case 3:
          num_meta = read_unaligned<uint16_t>(it->data());
          break;
        case 4:
        default:
          num_meta = read_unaligned<uint32_t>(it->data());
          break;
        }
        std::cout << it->str() << '\t' << num_meta << '\n';
        break;
      case OUTPUT_PHASH:
        std::cout << it->str() << '\t' << lines << '\n';
        break;
      case OUTPUT_TEXT_EMPTY:
        std::cout << it->str() << '\n';
        break;
      default:
        assert(0);
        break;
      }

      ++lines;
    }
  }

  else {
    fsa.printDot();
  }

  return 0;
}
