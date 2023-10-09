// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>

#include <iostream>
#include <fstream>
#include <memory>

#include <vespa/fsa/base64.h>
#include <vespa/fsa/fsa.h>
#include <vespa/fsa/automaton.h>

using namespace fsa;

enum FSA_Input_Format {
  INPUT_UNDEF,
  INPUT_TEXT,
  INPUT_TEXT_EMPTY,
  INPUT_TEXT_NUM,
  INPUT_BINARY,
  INPUT_BINARY_RAW };

void usage(const char *name, const char *errormsg = NULL)
{
  if(errormsg!=NULL){
    fprintf(stderr,"%s: %s\n",name,errormsg);
  }
  fprintf(stderr,"usage:\n");
  fprintf(stderr,"    %s [OPTIONS] [input_file] output_file\n",name);
  fprintf(stderr,"\n");
  fprintf(stderr,"      Valid options are:\n");
  fprintf(stderr,"      -h         display this help\n");
  fprintf(stderr,"      -b         use binary input format with Base64 encoded info\n");
  fprintf(stderr,"      -B         use binary input format with raw\n");
  fprintf(stderr,"      -e         use text input format with no info (default)\n");
  fprintf(stderr,"      -n         use text input format with (unsigned) numerical info\n");
  fprintf(stderr,"      -s bytes   data size for numerical info: 1,2 or 4(default)\n");
  fprintf(stderr,"      -z bytes   data size for binary info (-B) (0 means NUL terminated)\n");
  fprintf(stderr,"      -t         use text input format\n");
  fprintf(stderr,"      -p         build automaton with perfect hash\n");
  fprintf(stderr,"      -i         ignore info string, regardless of input format\n");
  fprintf(stderr,"      -S serial  serial number\n");
  fprintf(stderr,"      -v         be verbose\n");
  fprintf(stderr,"      -V         display version number\n");
  fprintf(stderr,"\n");
  fprintf(stderr,"      If input_file is not specified, standard input is used.\n");
}

void version()
{
  std::cout << "makefsa "
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
  FSA_Input_Format  format = INPUT_UNDEF;
  unsigned int      num_size = 4;
  unsigned int      info_size_binary = 0;
  bool build_phash = false;
  const char *input_file;
  const char *output_file;
  uint32_t serial = 0;
  bool ignore_info = false;
  bool verbose = false;
  unsigned int lines=0,count = 0;

  int          opt;
  extern char *optarg;
  extern int   optind;

  while((opt=getopt(argc,argv,"ebBhns:z:tpS:ivV")) != -1){
    switch(opt){
    case 'b':
      format = INPUT_BINARY;
      break;
    case 'B':
      format = INPUT_BINARY_RAW;
      break;
    case 'h':
      usage(argv[0]);
      return 0;
    case 'V':
      version();
      return 0;
    case 't':
      format = INPUT_TEXT;
      break;
    case 'n':
      format = INPUT_TEXT_NUM;
      break;
    case 's':
      num_size = strtoul(optarg,NULL,0);
      if(num_size!=1 && num_size!=2 && num_size!=4){
        usage(argv[0],"invalid numerical info size (-s)");
        return 1;
      }
      break;
    case 'z':
      info_size_binary = strtoul(optarg,NULL,0);
      break;
    case 'S':
      serial = strtoul(optarg,NULL,0);
      break;
    case 'e':
      format = INPUT_TEXT_EMPTY;
      break;
    case 'p':
      build_phash = true;
      break;
    case 'i':
      ignore_info = true;
      break;
    case 'v':
      verbose = true;
      break;
    case '?':
      usage(argv[0],"unrecognized option");
      return 1;
    }
  }

  if(format==INPUT_UNDEF) // use default format (warning?)
    format=INPUT_TEXT_EMPTY;

  if(optind+2==argc){
    input_file = argv[optind];
    output_file = argv[optind+1];
  }
  else if(optind+1==argc){
    input_file = NULL;
    output_file = argv[optind];
  }
  else{
    usage(argv[0],"required parameter(s) missing");
    return 1;
  }

  Automaton automaton;

  std::string input,last_input,meta,temp;
  union{
    uint8_t  u1;
    uint16_t u2;
    uint32_t u4;
  } num_meta;
  std::ifstream infile;
  std::istream *in;
  auto binary_info = std::make_unique<char[]>(info_size_binary);
  size_t split;
  bool empty_meta_str = false;

  if(verbose) version();

  if(verbose) std::cerr << "Initializing automaton ...";
  automaton.init();
  if(verbose) std::cerr << " done." << std::endl;

  if(input_file!=NULL){
    infile.open(input_file);
    if (infile.fail()) {
      std::cerr << "Error: Could not open file \"" << input_file << "\"\n";
      return(1);
    }
    in=&infile;
  }
  else{
    in=&std::cin;
  }
  if(verbose) std::cerr << "Inserting lines ...";
  while(!in->eof()){
    switch(format){
    case INPUT_BINARY:
      getline(*in,input,'\0');
      getline(*in,temp,'\0');
      Base64::decode(temp,meta);
      break;
    case INPUT_BINARY_RAW:
      getline(*in,input,'\0');
      if (info_size_binary) {
        in->read(binary_info.get(), info_size_binary);
        meta.assign(binary_info.get(), info_size_binary);
      }
      else
        getline(*in,meta,'\0');
      break;
    case INPUT_TEXT:
      getline(*in,temp,'\n');
      split = temp.find_first_of('\t');
      input = temp.substr(0, split);
      if (split == std::string::npos) {
        empty_meta_str = true;
        break;
      }
      meta = temp.substr(split + 1);
      meta+='\0';
      break;
    case INPUT_TEXT_NUM:
      getline(*in,temp,'\n');
      split = temp.find_first_of('\t');
      input = temp.substr(0, split);
      if (split == std::string::npos) {
        empty_meta_str = true;
        break;
      }
      temp = temp.substr(split + 1);
      switch(num_size){
      case 1:
        num_meta.u1=strtoul(temp.c_str(),NULL,0);
        meta.assign((const char*)&num_meta,1);
        break;
      case 2:
        num_meta.u2=strtoul(temp.c_str(),NULL,0);
        meta.assign((const char*)&num_meta,2);
        break;
      case 4:
      default:
        num_meta.u4=strtoul(temp.c_str(),NULL,0);
        meta.assign((const char*)&num_meta,4);
        break;
      }
      break;
    case INPUT_TEXT_EMPTY:
      getline(*in,input,'\n');
      break;
    case INPUT_UNDEF:
      assert(0);
      break;
    }

    ++lines;

    if(input.length()>0){
      if(last_input>input){
        std::cerr << "warning: ignoring unsorted line " << lines << ", \"" << input << "\"\n";
      }
      else if(last_input==input){
        std::cerr << "warning: ignoring duplicate line " << lines << ", \"" << input << "\"\n";
      }
      else if(empty_meta_str) {
        std::cerr << "warning: ignoring line " << lines << ", \"" << input << "\" with missing meta info\n";
      }
      else{
        if(format==INPUT_TEXT_EMPTY || ignore_info){
          automaton.insertSortedString(input);
        }
        else{
          automaton.insertSortedString(input,meta);
        }
        if(verbose){
          ++count;
          if(count%1000==0)
            std::cerr << "\rInserting lines ... (inserted " << count << " lines)";
        }
      }
      last_input=input;
    }
    empty_meta_str = false;
  }
  if(verbose) std::cerr << "\rInserting lines ... (inserted " << count << "/" <<  (lines-1) << " lines) ... done.\n";
  if(input_file!=NULL){
    infile.close();
  }


  if(verbose) std::cerr << "Finalizing ...";
  automaton.finalize();
  if(verbose) std::cerr << " done." << std::endl;


  if(build_phash){
    if(verbose) std::cerr << "Adding perfect hash ...";
    automaton.addPerfectHash();
    if(verbose) std::cerr << " done." << std::endl;
  }


  if(verbose) std::cerr << "Writing fsa file ...";
  if (!automaton.write(output_file,serial)) {
    std::cerr << "Failed to write fsa file '" << std::string(output_file) << "'. Please check write permissions" << std::endl;
    return 1;
  }
  if(verbose) std::cerr << " done." << std::endl;


  return 0;
}
