// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import com.yahoo.security.tls.CapabilitySet;

import java.util.Collection;


class MandatoryMethods {

    Supervisor parent;

    public MandatoryMethods(Supervisor parent) {
        this.parent = parent;
        //---------------------------------------------------------------------
        Method m;
        //---------------------------------------------------------------------
        m = new Method("frt.rpc.ping", "", "", this::ping);
        m.requireCapabilities(CapabilitySet.none());
        m.methodDesc("Method that may be used to "
                     + "check if the server is online");
        parent.addMethod(m);
        //---------------------------------------------------------------------
        m = new Method("frt.rpc.getMethodList", "", "SSS", this::getMethodList);
        m.requireCapabilities(CapabilitySet.none());
        m.methodDesc("Obtain a list of all available methods");
        m.returnDesc(0, "names",  "Method names");
        m.returnDesc(1, "params", "Method parameter types");
        m.returnDesc(2, "return", "Method return types");
        parent.addMethod(m);
        //---------------------------------------------------------------------
        m = new Method("frt.rpc.getMethodInfo", "s", "sssSSSS", this::getMethodInfo);
        m.requireCapabilities(CapabilitySet.none());
        m.methodDesc("Obtain detailed information about a single method");
        m.paramDesc (0, "methodName",  "The method we want information about");
        m.returnDesc(0, "desc",        "Description of what the method does");
        m.returnDesc(1, "params",      "Method parameter types");
        m.returnDesc(2, "return",      "Method return values");
        m.returnDesc(3, "paramNames",  "Method parameter names");
        m.returnDesc(4, "paramDesc",   "Method parameter descriptions");
        m.returnDesc(5, "returnNames", "Method return value names");
        m.returnDesc(6, "returnDesc",  "Method return value descriptions");
        parent.addMethod(m);
        //---------------------------------------------------------------------
    }

    private void ping(Request req) {
        // no code needed :)
    }

    private void getMethodList(Request req) {
        Collection<Method> methods = parent.methodMap().values();
        int cnt = methods.size();
        String[] ret0_names  = new String[cnt];
        String[] ret1_params = new String[cnt];
        String[] ret2_return = new String[cnt];

        int i = 0;
        for (Method m: methods) {
            ret0_names[i]  = m.name();
            ret1_params[i] = m.paramTypes();
            ret2_return[i] = m.returnTypes();
            i++;
        }
        req.returnValues().add(new StringArray(ret0_names));
        req.returnValues().add(new StringArray(ret1_params));
        req.returnValues().add(new StringArray(ret2_return));
    }

    private void getMethodInfo(Request req) {
        Method method = parent.methodMap().get(req.parameters().get(0).asString());
        if (method == null) {
            req.setError(ErrorCode.METHOD_FAILED, "No Such Method");
            return;
        }
        req.returnValues().add(new StringValue(method.methodDesc()));
        req.returnValues().add(new StringValue(method.paramTypes()));
        req.returnValues().add(new StringValue(method.returnTypes()));

        int paramCnt  = method.paramTypes().length();
        int returnCnt = method.returnTypes().length();
        String[] ret3_paramName  = new String[paramCnt];
        String[] ret4_paramDesc  = new String[paramCnt];
        String[] ret5_returnName = new String[returnCnt];
        String[] ret6_returnDesc = new String[returnCnt];
        for (int i = 0; i < paramCnt; i++) {
            ret3_paramName[i] = method.paramName(i);
            ret4_paramDesc[i] = method.paramDesc(i);
        }
        for (int i = 0; i < returnCnt; i++) {
            ret5_returnName[i] = method.returnName(i);
            ret6_returnDesc[i] = method.returnDesc(i);
        }
        req.returnValues().add(new StringArray(ret3_paramName));
        req.returnValues().add(new StringArray(ret4_paramDesc));
        req.returnValues().add(new StringArray(ret5_returnName));
        req.returnValues().add(new StringArray(ret6_returnDesc));
    }
}
