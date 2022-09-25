// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    vectorizer.cpp
 * @brief   Simple document vectorizer based on %FSA (%Finite %State %Automaton) (implementation)
 */

#include <list>
#include <algorithm>
#include <cmath>

#include "vectorizer.h"
#include "fsa.h"

namespace fsa {

// {{{ Vectorizer::TfIdf::weight

double Vectorizer::TfIdf::weight(unsigned int tfnorm, unsigned int idfnorm,
                                    double tfexp, double idfexp) const
{
  double tf_n, idf_n;

  if(tfnorm==0 || tfexp==0.0){
    tf_n = 1.0;
  }
  else{
    tf_n = (double)_tf/tfnorm;
    if(tfexp!=1.0 && tf_n!=0.0){
      tf_n = std::exp(tfexp * std::log(tf_n));
    }
  }

  if(idfnorm==0 || idfexp==0.0){
    idf_n = 1.0;
  }
  else{
    idf_n = 1.0-(double)_idf/idfnorm;
    if(idf_n<0.0)
      idf_n = 0.0;
    if(idfexp!=1.0 && idf_n!=0.0){
      idf_n = std::exp(idfexp * std::log(idf_n));
    }
  }

  return tf_n * idf_n;
}

// }}}

// {{{ Vectorizer::vectorize

void Vectorizer::vectorize(const NGram &text, TermVector &vector, unsigned int limit,
                           bool keephits, double tfexp, double idfexp) const
{
  RawVector             raw_vect(keephits);
  RawVector::iterator   rvi;

  _detector.detect(text,raw_vect);
  vector.clear();
  unsigned int tfmax=1;
  for(rvi=raw_vect.begin(); rvi!=raw_vect.end(); ++rvi){
    if(rvi->second.first.tf()>tfmax)
      tfmax=rvi->second.first.tf();
  }
  vector.reserve(raw_vect.size());
  for(rvi=raw_vect.begin(); rvi!=raw_vect.end(); ++rvi){
    vector.push_back(VectorItem(rvi->first,rvi->second.first.weight(tfmax,_idf_docs,tfexp,idfexp),rvi->second.second));
  }
  std::sort(vector.begin(),vector.end());
  if(vector.size()>limit){
    vector.resize(limit);
  }
}

void Vectorizer::vectorize(const NGram &text, TermVector &vector, unsigned int limit,
                           double tfexp, double idfexp) const
{
  vectorize(text, vector, limit, false, tfexp, idfexp);
}

// }}}

} // namespace fsa
