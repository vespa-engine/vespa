// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/frtstream/frtclientstream.h>

#include <iostream>
#include <csignal>
#include <vector>
#include <string>
#include <set>

using namespace std;
using frtstream::FrtClientStream;
using frtstream::Method;
using frtstream::InvokationException;

const string connectionSpec = "tcp/test-tonyv:9997";

class TestApp : public FastOS_Application {
public:
    int Main() {
      FrtClientStream s(connectionSpec);

      std::vector<std::string> vec;
      vec.push_back("Hello"); vec.push_back("world");

      std::set<std::string> codeSet;
      codeSet.insert("abc"); codeSet.insert("def");

      std::vector<double> doubleVec;
      doubleVec.push_back(99.98); doubleVec.push_back(98.97);

      std::vector<float> floatVec;
      floatVec.push_back(99.98); floatVec.push_back(98.97);

      uint8_t i1 = 1;
      int8_t i2 = 2;

      uint16_t i3 = 1;
      int16_t i4 = 2;

      uint32_t i5 = 1;
      int32_t i6 = 2;

      uint64_t i7 = 1;
      int64_t i8 = 2;

      float f1 = 3.14;
      double d1 = 123.456;

      try {
          s <<Method("add") <<1 <<2 <<i1 <<f1 <<d1 <<vec <<codeSet <<doubleVec <<floatVec
            <<i1 <<i2 <<i3 <<i4 <<i5 <<i6 <<i7 <<i8;

          int res;
          s >> res >>vec >>codeSet >>doubleVec >>floatVec >>i1 >>f1 >>d1
            >>i1 >>i2 >>i3 >>i4 >>i5 >>i6 >>i7 >>i8;

          cout <<"Result = " <<res <<endl;
      } catch(const InvokationException& e) {
          cerr <<e <<endl;
      }
      return 0;

    }
};


int main(int argc, char** argv) {
    TestApp app;
    return app.Entry(argc, argv);


}
