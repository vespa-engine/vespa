// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    vectorizertest.cpp
 * @brief   Test for the vectorizer class
 *
 */

#include <string>
#include <iostream>
#include <iomanip>

#include <vespa/fsa/vectorizer.h>
#include <vespa/fsa/timestamp.h>

using namespace fsa;

int main(int argc, char **argv)
{
  FSA dict(argc>=2? argv[1] : "__testfsa__.__fsa__");

  Vectorizer v(dict);
  Vectorizer::TermVector tv;


  std::string text =
    "belfast northern ireland protestant extremists crashed a forklift "
    "truck into a belfast pub packed with catholics early friday and tossed "
    "gasoline bombs into the building on a road on the front line of "
    "tensions between the two communities "
    "no one was hurt in the attack police said, though the forklift came "
    "crashing through a window just above a bench where a patron had been "
    "sitting seconds earlier the bar s owner sean conlon said "
    "the customer had just gotten up to go to the toilet so it s really "
    "just by the grace of god still he s here today at all conlon said "
    "a protestant gang used the stolen vehicle to smash down a heavy metal "
    "security grill on a window at around 12 45 a m then to toss three "
    "gasoline bombs inside the pub on the crumlin road  an especially "
    "polarized part of north belfast where catholic protestant tensions "
    "have repeatedly flared "
    "no group claimed responsibility for the attack on the thirty two "
    "degrees north pub a catholic frequented bar across the street from a "
    "hard line protestant district but catholic leaders blamed the largest "
    "illegal protestant group the ulster defense association "
    "firefighters quickly doused the flames caused by the gasoline "
    "bombs the forklift remained wedged into the pub friday afternoon as "
    "engineers and architects discussed whether the newly refurbished pub "
    "would have to be partly demolished "
    "the uda is supposed to be observing a cease fire in support of "
    "northern ireland s 1998 peace accord but britain no longer recognizes "
    "the validity of the uda truce because the anti catholic group has "
    "violated it so often "
    "the crumlin road area of north belfast has suffered some of northern "
    "ireland s most graphic sectarian trouble in recent years  while both "
    "sides complain of suffering harassment and stone throwing protestants "
    "in particular accuse the expanding catholic community of seeking to "
    "force them from the area a charge the catholics deny. "
    "protestant mobs in 2001 and 2002 blocked catholics from taking their "
    "children to the local catholic elementary school which is in the "
    "predominantly protestant part of the area "
    "on july 12 hundreds of catholics from the area s ardoyne district "
    "swarmed over police and british soldiers protecting a protestant "
    "parade that had just passed down crumlin road dozens were wounded "
    "demographic tensions lie at the heart of the northern ireland "
    "conflict which was founded 84 years ago as a british territory with a "
    "70 percent protestant majority the most recent census in 2001 put the "
    "sectarian split at nearer 55 percent protestant and 45 percent "
    "catholic and confirmed that belfast now has a catholic majority";

  NGram tokenized_text(text);

  TimeStamp t;
  double t0,t1;
  unsigned int count=1000;

  std::cout << "Number of iterations: " << count << std::endl;
  std::cout << "Input string length: " << text.length() << std::endl;
  std::cout << "Number of input tokens: " << tokenized_text.length() << std::endl;
  std::cout << std::endl;

  t0=t.elapsed();
  for(unsigned int i=0; i<count; ++i){
    v.vectorize(tokenized_text,tv);
  }
  t1=t.elapsed()-t0;
  std::cout << "Vectorizer performance: \t" << t1 << " sec" << "\t\t"
            << count/t1 << " document/sec" << std::endl;
  for(unsigned int i=0; i<tv.size(); i++){
    std::cout << tv[i].term() << ", " << tv[i].weight() << std::endl;
  }

  return 0;
}
