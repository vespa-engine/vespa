// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>

#include <vespa/fsa/fsa.h>
#include <vespa/fsa/automaton.h>
#include <vespa/fsa/timestamp.h>

using namespace fsa;

int main(int, char**)
{

  Automaton *aut = new Automaton;

  Blob fruit("Fruit"), veggie("Vegetable"), city("City");

  TimeStamp t;

  aut->init();

  aut->insertSortedString("Cupertino",city);
  aut->insertSortedString("Foster City",city);
  aut->insertSortedString("Los Altos",city);
  aut->insertSortedString("Menlo Park",city);
  aut->insertSortedString("Mountain View",city);
  aut->insertSortedString("Palo Alto",city);
  aut->insertSortedString("San Francisco",city);
  aut->insertSortedString("San Jose",city);
  aut->insertSortedString("Santa Clara",city);
  aut->insertSortedString("Saratoga",city);
  aut->insertSortedString("Sunnyvale",city);
  aut->insertSortedString("apple",fruit);
  aut->insertSortedString("apricot",fruit);
  aut->insertSortedString("artichoke",veggie);
  aut->insertSortedString("banana",fruit);
  aut->insertSortedString("cabbage",veggie);
  aut->insertSortedString("carrot",veggie);
  aut->insertSortedString("cherry",fruit);
  aut->insertSortedString("chili",veggie);
  aut->insertSortedString("cucumber",veggie);
  aut->insertSortedString("eggplant",veggie);
  aut->insertSortedString("grapes",fruit);
  aut->insertSortedString("lettuce",veggie);
  aut->insertSortedString("onion",veggie);
  aut->insertSortedString("paprika",veggie);
  aut->insertSortedString("passion fruit",fruit);
  aut->insertSortedString("pea",veggie);
  aut->insertSortedString("peach",fruit);
  aut->insertSortedString("pear",fruit);
  aut->insertSortedString("pineapple",fruit);
  aut->insertSortedString("plum",fruit);
  aut->insertSortedString("potato",veggie);
  aut->insertSortedString("pumpkin",veggie);
  aut->insertSortedString("sour cherry",fruit);
  aut->insertSortedString("squash",veggie);
  aut->insertSortedString("tomato",veggie);

  aut->finalize();

  double d1 = t.elapsed();

  aut->addPerfectHash();

  double d2 = t.elapsed();

  aut->write("__testfsa__.__fsa__");

  double d3 = t.elapsed();

  FSA *fsa = aut->getFSA();

  double d4 = t.elapsed();

  std::cout << "Automoaton build finished (" << 1000*d1 << "ms," << 1000*(d2-d1) << "ms)"
            << ", fsa retrieval (" << 1000*(d4-d3) << "ms) " << ((fsa==NULL)?"failed":"succeded") << ".\n";

  if(fsa!=NULL){
    FSA::State fs(*fsa);
    const unsigned char *pb = fs.lookup("cucumber");
    std::cout << "Lookup(\"cucumber\") -> ";
    if(pb!=NULL){
      std::cout << "\"" << pb << "\"";
    }
    else{
      std::cout << "not found.";
    }
    std::cout << "\n";
  }

  delete aut;
  delete fsa;

  return 0;
}
