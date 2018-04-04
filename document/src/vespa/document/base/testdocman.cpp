// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testdocman.h"
#include "exceptions.h"
#include <vespa/document/datatype/datatypes.h>
#include <boost/random.hpp>
#include <sstream>

namespace document {

namespace {
    std::vector<char> createBuffer() {
        // Text from Shakespeare's Hamlet, http://www.ibiblio.org/pub/docs/books/gutenberg/etext98/2ws2610.txt
        const char* content =
            "To be, or not to be: that is the question:\n"
            "Whether 'tis nobler in the mind to suffer\n"
            "The slings and arrows of outrageous fortune,\n"
            "Or to take arms against a sea of troubles,\n"
            "And by opposing end them? To die: to sleep;\n"
            "No more; and by a sleep to say we end\n"
            "The heart-ache and the thousand natural shocks\n"
            "That flesh is heir to, 'tis a consummation\n"
            "Devoutly to be wish'd. To die, to sleep;\n"
            "To sleep: perchance to dream: ay, there's the rub;\n"
            "For in that sleep of death what dreams may come\n"
            "When we have shuffled off this mortal coil,\n"
            "Must give us pause: there's the respect\n"
            "That makes calamity of so long life;\n"
            "For who would bear the whips and scorns of time,\n"
            "The oppressor's wrong, the proud man's contumely,\n"
            "The pangs of despised love, the law's delay,\n"
            "The insolence of office and the spurns\n"
            "That patient merit of the unworthy takes,\n"
            "When he himself might his quietus make\n"
            "With a bare bodkin? who would fardels bear,\n"
            "To grunt and sweat under a weary life,\n"
            "But that the dread of something after death,\n"
            "The undiscover'd country from whose bourn\n"
            "No traveller returns, puzzles the will\n"
            "And makes us rather bear those ills we have\n"
            "Than fly to others that we know not of?\n"
            "Thus conscience does make cowards of us all;\n"
            "And thus the native hue of resolution\n"
            "Is sicklied o'er with the pale cast of thought,\n"
            "And enterprises of great pith and moment\n"
            "With this regard their currents turn awry,\n"
            "And lose the name of action. - Soft you now!\n"
            "The fair Ophelia! Nymph, in thy orisons\n"
            "Be all my sins remember'd.\n\n";
        std::vector<char> buffer(content, content + strlen(content));
        return buffer;
    }
}

std::vector<char> TestDocMan::_buffer = createBuffer();

TestDocMan::TestDocMan()
    : _test_repo(),
      _repo(_test_repo.getTypeRepoSp()),
      _typeCfg(&_test_repo.getTypeConfig())
{ }

TestDocMan::~TestDocMan() { }

void
TestDocMan::setTypeRepo(const std::shared_ptr<const DocumentTypeRepo> &repo)
{
    _repo = repo;
    _typeCfg = NULL;
}

Document::UP
TestDocMan::createDocument(
        const std::string& content,
        const std::string& id,
        const std::string& type) const
{
    const DocumentType *type_ptr = 0;
    type_ptr = _repo->getDocumentType(type);
    assert(type_ptr);
    Document::UP doc(new Document(*type_ptr, DocumentId(id)));
    doc->setValue(doc->getField("content"), StringFieldValue(content.c_str()));
    return doc;
}

Document::UP
TestDocMan::createRandomDocument(int seed, int maxContentSize) const
{
    // Currently only one document type added.
    return createRandomDocument("testdoctype1", seed, maxContentSize);
}

Document::UP
TestDocMan::createRandomDocumentAtLocation(
                int location, int seed, int maxContentSize) const
{
    boost::rand48 rnd(seed);
    std::ostringstream id;
    id << "id:mail:testdoctype1:n=" << location << ":" << (rnd() % 0x10000)
       << ".html";
    return createDocument(generateRandomContent(rnd() % maxContentSize),
                          id.str(), "testdoctype1");
}

Document::UP
TestDocMan::createRandomDocumentAtLocation(
        int location, int seed, int minContentSize, int maxContentSize) const
{
    boost::rand48 rnd(seed);
    std::ostringstream id;
    id << "id:mail:testdoctype1:n=" << location << ":" << (rnd() % 0x10000)
       << ".html";

    int size = maxContentSize > minContentSize ?
               rnd() % (maxContentSize - minContentSize) + minContentSize :
               minContentSize;
    return
        createDocument(generateRandomContent(size), id.str(), "testdoctype1");
}

Document::UP
TestDocMan::createRandomDocument(
        const std::string& type, int seed, int maxContentSize) const
{
    boost::rand48 rnd(seed);
    std::ostringstream id;
    id << "id:mail:" << type << ":n=" << (rnd() % 0xFFFF);
    id << ":" << (rnd() % 256) << ".html";
    return createDocument(generateRandomContent(rnd() % maxContentSize), id.str(), type);
}

std::string
TestDocMan::generateRandomContent(uint32_t size)
{
    std::ostringstream content;
    for (uint32_t i=0; i<size; i += _buffer.size()) {
        uint32_t sz = (i + _buffer.size() > size ? size - i : _buffer.size());
        content << std::string(&_buffer[0], sz);
    }
    return content.str();
}

} // document
