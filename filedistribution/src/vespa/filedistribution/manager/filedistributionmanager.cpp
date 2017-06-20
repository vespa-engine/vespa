// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jnistring.h"
#include "field.h"
#include "createtorrent.h"
#include "filedb.h"
#include <vespa/filedistribution/manager/com_yahoo_vespa_filedistribution_FileDistributionManager.h>

#include <vespa/filedistribution/model/filedistributionmodel.h>
#include <vespa/filedistribution/model/zkfiledbmodel.h>
#include <vespa/filedistribution/model/mockfiledistributionmodel.h>
#include <vespa/filedistribution/model/zkfacade.h>
#include <memory>

using namespace filedistribution;

namespace fs = boost::filesystem;

namespace {

class NativeFileDistributionManager {
  public:
    std::unique_ptr<FileDBModel> _fileDBModel;
    std::unique_ptr<FileDB> _fileDB;
};

LongField<NativeFileDistributionManager*> nativeFileDistributionManagerField;

void throwRuntimeException(const char* msg, JNIEnv* env)
{
    //do not mask active exception.
    if (!env->ExceptionOccurred()) {
        jclass runtimeExceptionClass = env->FindClass("java/lang/RuntimeException");
        if (runtimeExceptionClass) {
            env->ThrowNew(runtimeExceptionClass, msg);
        }
    }
}

template <class FIELD>
void deleteField(FIELD& field, jobject self, JNIEnv* env)
{
    delete field.get(self, env);
    field.set(self, 0, env);
}

std::unique_ptr<ZKLogging> _G_zkLogging;

} //anonymous namespace


#define STANDARDCATCH(returnStatement)                         \
    catch (const std::bad_alloc&) {                            \
        std::cerr<<"Error: Out of memory" <<std::endl;         \
        /*might fail, therefore also uses stderror message*/   \
        throwRuntimeException("Out of memory", env);           \
        returnStatement;                                       \
    } catch(const ZKException& e) {                            \
        std::stringstream ss;                                  \
        ss << "In" << __FUNCTION__ << ": ";                    \
        ss << e.what();                   \
        throwRuntimeException(ss.str().c_str(), env);          \
        returnStatement;                                       \
    } catch(const std::exception& e) {                         \
        throwRuntimeException(e.what(), env);                  \
        returnStatement;                                       \
    }

JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_setup(
        JNIEnv *env, jclass self)
{
    try {
        _G_zkLogging = std::make_unique<ZKLogging>();
        nativeFileDistributionManagerField = LongField<NativeFileDistributionManager*>(self, "nativeFileDistributionManager", env);
    } STANDARDCATCH()
}


namespace {
void initMockFileDBModel(NativeFileDistributionManager& manager)
{
    manager._fileDBModel.reset(new MockFileDBModel());
}

void initFileDBModel(NativeFileDistributionManager& manager, const std::string& zkServers)
{
    //Ignored for now, since we're not installing any watchers.
    std::shared_ptr<ZKFacade> zk(new ZKFacade(zkServers, true));
    manager._fileDBModel.reset(new ZKFileDBModel(zk));
}
} //end anonymous namespace

JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_init(
        JNIEnv *env, jobject self,
        jbyteArray fileDBPathArg, jbyteArray zkServersArg)
{
    try {
        JNIString zkServers(zkServersArg, env);

        nativeFileDistributionManagerField.set(self, new NativeFileDistributionManager(), env);
        NativeFileDistributionManager& manager = *nativeFileDistributionManagerField.get(self, env);
        if (zkServers._value == "mockfiledistributionmodel.testing") {
            initMockFileDBModel(manager);
        } else {
            initFileDBModel(manager, zkServers._value);
        }

        JNIString fileDBPath(fileDBPathArg, env);
        manager._fileDB.reset(new FileDB(fileDBPath._value));

    } STANDARDCATCH()
}


JNIEXPORT
jstring JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_addFileImpl(
        JNIEnv *env, jobject self,
        jbyteArray completePathArg)
{
    try {
        JNIString completePath(completePathArg, env);
        CreateTorrent createTorrent(completePath._value);

        std::string fileReference = createTorrent.fileReference();
        NativeFileDistributionManager& manager = *nativeFileDistributionManagerField.get(self, env);

        DirectoryGuard::UP guard = manager._fileDB->getGuard();// This prevents the filedistributor from working in an inconsistent state.
        bool freshlyAdded = manager._fileDB->add(*guard, completePath._value, fileReference);

        FileDBModel& model = *manager._fileDBModel;
        bool hasRegisteredFile = model.hasFile(fileReference);
        if (! hasRegisteredFile ) {
            model.addFile(fileReference, createTorrent.bencode());
        }
        if (freshlyAdded == hasRegisteredFile) {
            std::cerr << "freshlyAdded(" << freshlyAdded << ") == hasRegisteredFile(" << hasRegisteredFile
                      << "), which is very odd. File is '" << fileReference << "'" << std::endl;
        }

        //contains string with the characters 0-9 a-f
        return env->NewStringUTF(fileReference.c_str());
    } STANDARDCATCH(return 0)
}


JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_shutdown(
        JNIEnv *env, jobject self)
{
    deleteField(nativeFileDistributionManagerField, self, env);
}


JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_setDeployedFilesImpl(
        JNIEnv *env, jobject self, jbyteArray hostNameArg, 
        jbyteArray appIdArg, jobjectArray fileReferencesArg)
{
    try {
        JNIString hostName(hostNameArg, env);
        JNIString appId(appIdArg, env);
        JNIArray<JNIString> fileReferences(fileReferencesArg, env);

        nativeFileDistributionManagerField.get(self, env)->_fileDBModel->
            setDeployedFilesToDownload(hostName._value, appId._value, fileReferences._value);
    } STANDARDCATCH()
}


JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_limitSendingOfDeployedFilesToImpl(
        JNIEnv *env, jobject self, jobjectArray hostNamesArg, jbyteArray appIdArg)
{
    try {
        JNIArray<JNIString> hostNames(hostNamesArg, env);
        JNIString appId(appIdArg, env);

        nativeFileDistributionManagerField.get(self, env)->_fileDBModel->
            cleanDeployedFilesToDownload(hostNames._value, appId._value);
    } STANDARDCATCH()
}

JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_removeDeploymentsThatHaveDifferentApplicationIdImpl(
        JNIEnv *env, jobject self, jobjectArray hostNamesArg, jbyteArray appIdArg)
{
    try {
        JNIArray<JNIString> hostNames(hostNamesArg, env);
        JNIString appId(appIdArg, env);

        nativeFileDistributionManagerField.get(self, env)->_fileDBModel->
            removeDeploymentsThatHaveDifferentApplicationId(hostNames._value, appId._value);
    } STANDARDCATCH()
}



JNIEXPORT
void JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_limitFilesTo(
        JNIEnv *env, jobject self, jobjectArray fileReferencesArg)
{
    try {
        JNIArray<JNIString> fileReferences(fileReferencesArg, env);

        nativeFileDistributionManagerField.get(self, env)->_fileDBModel->
            cleanFiles(fileReferences._value);
    } STANDARDCATCH()
}


JNIEXPORT
jbyteArray JNICALL
Java_com_yahoo_vespa_filedistribution_FileDistributionManager_getProgressImpl(
        JNIEnv *env, jobject self, jbyteArray fileReferenceArg, jobjectArray hostNamesArg)
{
    try {
        JNIString fileReference(fileReferenceArg, env);
        JNIArray<JNIString> hostNames(hostNamesArg, env);

        const FileDBModel::Progress progress =
            nativeFileDistributionManagerField.get(self, env)->_fileDBModel->
            getProgress(fileReference._value, hostNames._value);

        jbyteArray result = env->NewByteArray(progress.size());
        if (!result)
            return 0; //exception thrown when returning

        env->SetByteArrayRegion(result, 0, progress.size(), &*progress.begin());
        return result;
    } STANDARDCATCH(return 0)
}
