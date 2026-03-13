// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <vespa/log/log.h>
LOG_SETUP("echo_client");

class EchoClient {
public:
    int main(int argc, char** argv);
};

int EchoClient::main(int argc, char** argv) {
    if (argc < 2) {
        printf("usage  : echo_client <connectspec>\n");
        return 1;
    }
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor&          supervisor = server.supervisor();

    FRT_Target*     target = supervisor.GetTarget(argv[1]);
    FRT_RPCRequest* req = supervisor.AllocRPCRequest();
    FRT_Values*     args = req->GetParams();
    req->SetMethodName("frt.rpc.echo");
    args->EnsureFree(16);

    args->AddInt8(8);
    uint8_t* pt_int8 = args->AddInt8Array(3);
    pt_int8[0] = 1;
    pt_int8[1] = 2;
    pt_int8[2] = 3;

    args->AddInt16(16);
    uint16_t* pt_int16 = args->AddInt16Array(3);
    pt_int16[0] = 2;
    pt_int16[1] = 4;
    pt_int16[2] = 6;

    args->AddInt32(32);
    uint32_t* pt_int32 = args->AddInt32Array(3);
    pt_int32[0] = 4;
    pt_int32[1] = 8;
    pt_int32[2] = 12;

    args->AddInt64(64);
    uint64_t* pt_int64 = args->AddInt64Array(3);
    pt_int64[0] = 8;
    pt_int64[1] = 16;
    pt_int64[2] = 24;

    args->AddFloat(32.5);
    float* pt_float = args->AddFloatArray(3);
    pt_float[0] = 0.25;
    pt_float[1] = 0.5;
    pt_float[2] = 0.75;

    args->AddDouble(64.5);
    double* pt_double = args->AddDoubleArray(3);
    pt_double[0] = 0.1;
    pt_double[1] = 0.2;
    pt_double[2] = 0.3;

    args->AddString("string");
    FRT_StringValue* pt_string = args->AddStringArray(3);
    args->SetString(&pt_string[0], "str1");
    args->SetString(&pt_string[1], "str2");
    args->SetString(&pt_string[2], "str3");

    args->AddData("data", 4);
    FRT_DataValue* pt_data = args->AddDataArray(3);
    args->SetData(&pt_data[0], "dat1", 4);
    args->SetData(&pt_data[1], "dat2", 4);
    args->SetData(&pt_data[2], "dat3", 4);

    target->InvokeSync(req, 5.0); // Invoke
    req->Print();                 // Dump request data
    if (req->GetReturn()->Equals(req->GetParams())) {
        printf("Return values == parameters.\n");
    } else {
        printf("Return values != parameters.\n");
    }
    req->internal_subref();
    target->internal_subref();
    return 0;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    EchoClient myapp;
    return myapp.main(argc, argv);
}
