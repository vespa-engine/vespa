// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfiletestutils.h"
#include <vespa/persistence/conformancetest/conformancetest.h>

namespace storage {
namespace memfile {

struct ProviderConformanceTest : public spi::ConformanceTest {
    struct Factory : public PersistenceFactory {
        framework::defaultimplementation::ComponentRegisterImpl _compRegister;
        framework::defaultimplementation::RealClock _clock;
        std::unique_ptr<MemFileCache> cache;

        Factory()
            : _compRegister(),
              _clock()
        {
            _compRegister.setClock(_clock);
        }

        spi::PersistenceProvider::UP
        getPersistenceImplementation(const std::shared_ptr<const document::DocumentTypeRepo>& repo,
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
