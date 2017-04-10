// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/memory/memorymanager.h>
#include <vespa/storageframework/defaultimplementation/memory/simplememorylogic.h>
#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovider.h>
#include <tests/spi/memfiletestutils.h>

LOG_SETUP(".test.dummyimpl");

namespace storage {
namespace memfile {

struct ProviderConformanceTest : public spi::ConformanceTest {
    struct Factory : public PersistenceFactory {
        framework::defaultimplementation::ComponentRegisterImpl _compRegister;
        framework::defaultimplementation::RealClock _clock;
        framework::defaultimplementation::MemoryManager _memoryManager;
        std::unique_ptr<MemFileCache> cache;

        Factory()
            : _compRegister(),
              _clock(),
              _memoryManager(
                    framework::defaultimplementation::AllocationLogic::UP(
                        new framework::defaultimplementation::SimpleMemoryLogic(
                            _clock, 1024 * 1024 * 1024)))
        {
            _compRegister.setClock(_clock);
            _compRegister.setMemoryManager(_memoryManager);
        }

        spi::PersistenceProvider::UP
        getPersistenceImplementation(const document::DocumentTypeRepo::SP& repo,
                                     const document::DocumenttypesConfig&) override
        {
            system("rm -rf vdsroot");
            system("mkdir -p vdsroot/disks/d0");
            vdstestlib::DirConfig config(getStandardConfig(true));

            MemFilePersistenceProvider::UP result(
                    new MemFilePersistenceProvider(
                            _compRegister,
                            config.getConfigId()));
            result->setDocumentRepo(*repo);
            return spi::PersistenceProvider::UP(result.release());
        }

        bool
        supportsRevert() const
        {
            return true;
        }
    };

    ProviderConformanceTest()
        : spi::ConformanceTest(PersistenceFactory::UP(new Factory)) {}

    CPPUNIT_TEST_SUITE(ProviderConformanceTest);
    DEFINE_CONFORMANCE_TESTS();
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ProviderConformanceTest);

} // memfile
} // storage
