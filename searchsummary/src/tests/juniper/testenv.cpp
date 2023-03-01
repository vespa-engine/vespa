// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* Setup and parameter parsing for static Juniper environment to reuse
 * within test framework
 */

#include "testenv.h"
#include <vespa/juniper/propreader.h>
#include <unistd.h>

namespace juniper
{

bool color_highlight = false;
// Easy access in tests..
Config* TestConfig;
Juniper * _Juniper;


TestEnv::TestEnv(int argc, char **argv, const char* propfile) :
    _props(), _config(), _juniper(), _wordFolder()
{
    int c;

    while ((c = getopt(argc, argv, "d:hcm:")) != EOF)
    {
        switch (c)
        {
	case 'd':
            fprintf(stderr, "This version of Juniper compiled without debug\n");
            break;
	case 'c':
            color_highlight = true;
            break;
	case 'm':
            // option handled by test framework
            break;
	case 'h':
	default:
            Usage(argv[0]);
            return;
        }
    }

    int expected_args = 0;

    if (argc - optind < expected_args)
    {
        Usage(argv[0]);
        return;
    }

    _props.reset(new PropReader(propfile));

    if (color_highlight)
    {
        _props->UpdateProperty("juniper.dynsum.highlight_on", "\\1b[1;31m");
        _props->UpdateProperty("juniper.dynsum.highlight_off", "\\1b[0m");
    }

    _juniper.reset(new Juniper(_props.get(), &_wordFolder));
    _Juniper = _juniper.get();
    _config = _juniper->CreateConfig();
    TestConfig = _config.get();
}

TestEnv::~TestEnv()
{
}

void TestEnv::Usage(char* s)
{
    fprintf(stderr, "Usage: %s [options]\n", s);
    fprintf(stderr, "Available options:\n");
    fprintf(stderr, "  -d<debugmask>: Turn on debugging\n");
    fprintf(stderr, "  -h: This help\n");
}


TestQuery::TestQuery(const char* qexp, const char* options) :
    _qparser(qexp),
    _qhandle(_qparser, options, _Juniper->getModifier())
{ }


PropertyMap::PropertyMap()
    : _map()
{
}


PropertyMap::~PropertyMap()
{
}


PropertyMap &
PropertyMap::set(const char *name, const char *value)
{
    _map[std::string(name)] = std::string(value);
    return *this;
}


const char *
PropertyMap::GetProperty(const char* name, const char* def) const
{
    auto res = _map.find(std::string(name));
    if (res != _map.end()) {
        return res->second.c_str();
    }
    return def;
}


} // end namespace juniper
