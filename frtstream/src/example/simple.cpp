// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/app.h>

#include <vespa/frtstream/frtclientstream.h>

using namespace std;
using frtstream::FrtClientStream;
using frtstream::Method;
using frtstream::InvokationException;

const string connectionSpec = "tcp/test-tonyv:9997";

class TestApp : public FastOS_Application {
public:
    int Main() {
      FrtClientStream s(connectionSpec);

      try {
          s <<Method("add") <<1 <<2;

          int res;
          s >> res;

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
