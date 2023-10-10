// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnet.h
 * @brief   Concept network class definition.
 *
 */

#pragma once

#include <cassert>
#include <stdlib.h>
#include "file.h" // for FileAccessMethod
#include "fsa.h"


namespace fsa {

// {{{ class ConceptNet

/**
 * @class ConceptNet
 * @brief Class for compact representation of a concept network.
 */
class ConceptNet {

public:

  class Handle; // defined in conceptnethandle.h

private:
  static const uint32_t MAGIC = 238579428;    /**< Magic number identifying concept net files. */

  static const FileAccessMethod _default_file_access_method = FILE_ACCESS_MMAP;   /**< Default file access method (read/mmap). */

  /**
   * @struct Header
   * @brief Concept net data file header.
   */
  struct Header {
    uint32_t _magic;             /**< Magic number.                        */
    uint32_t _version;           /**< Version number. (currently not used) */
    uint32_t _checksum;          /**< Checksum. (currently not used)       */
    uint32_t _index_size;        /**< Size of index structure.             */
    uint32_t _info_size;         /**< Size of info structure.              */
    uint32_t _catindex_size;     /**< Size of category index.              */
    uint32_t _strings_size;      /**< Size of string storage.              */
    uint32_t _max_freq;          /**< Reseved for normalization purposes.  */
    uint32_t _max_cfreq;         /**< Reseved for normalization purposes.  */
    uint32_t _max_qfreq;         /**< Reseved for normalization purposes.  */
    uint32_t _max_sfreq;         /**< Reseved for normalization purposes.  */
    uint32_t _max_efreq;         /**< Reseved for normalization purposes.  */
    uint32_t _max_afreq;         /**< Reseved for normalization purposes.  */
    uint32_t _dummy[51];         /**< Reserved.                            */
  };

  /**
   * @struct UnitData
   * @brief Unit data structure.
   */
  struct UnitData {
    uint32_t     _term;          /**< Offset of unit string in string storage.     */
    uint32_t     _frq;           /**< Unit frequency.                              */
    uint32_t     _cfrq;          /**< Frequency of the unit as complete query.     */
    uint32_t     _qfrq;          /**< Frequency of the unit as part of a query.    */
    uint32_t     _sfrq;          /**< Number of queries containing all unit terms. */
    uint32_t     _exts;          /**< If non-zero: offset of extension info in info structure.   */
    uint32_t     _assocs;        /**< If non-zero: offset of association info in info structure. */
    uint32_t     _cats;          /**< If non-zero: offset of category info in info structure.    */
  };

  void         *_mmap_addr;      /**< mmap address, NULL is file has not been mmapped.   */
  size_t        _mmap_length;    /**< mmap length.                                       */

  FSA           _unit_fsa;       /**< %FSA containing the units (with hash).    */
  uint32_t      _index_size;     /**< Size of the index structure.              */
  UnitData     *_index;          /**< Pointer to the index structure in memory. */
  uint32_t      _info_size;      /**< Size of the info structure.               */
  uint32_t     *_info;           /**< Pointer to the info structure in memory.  */
  uint32_t      _catindex_size;  /**< Size of the catergory index.              */
  uint32_t     *_catindex;       /**< Pointer to the category index in memory.  */
  uint32_t      _strings_size;   /**< Size of the string storage.               */
  char         *_strings;        /**< Pointer to the string storage in memory.  */

  bool _ok;                      /**< Flag indicating successful initialization. */

  /**
   * @brief Reset the object.
   *
   * Resets the object to an empty %ConceptNet, and releases allocated memory.
   */
  void reset();

  /**
   * @brief Read the concept net data file from disk.
   *
   * @param datafile Name of the concept net data file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global default access mode will be used.
   * @return True on success.
   */
  bool read(const char *datafile, fsa::FileAccessMethod fam = FILE_ACCESS_UNDEF);

  /**
   * @brief Unimplemented private default constructor.
   */
  ConceptNet();
  /**
   * @brief Unimplemented private copy constructor.
   */
  ConceptNet(const ConceptNet&);
  /**
   * @brief Unimplemented private assignement operator.
   */
  const ConceptNet& operator=(const ConceptNet&);

public:

  /**
   * @brief Constructor.
   *
   * @param fsafile %FSA file containing the units, with a perfect has
   *                (used for indexing the data file).
   * @param datafile Concept net data file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global default access mode will be used.
   */
  ConceptNet(const char *fsafile, const char *datafile=NULL, FileAccessMethod fam = FILE_ACCESS_UNDEF);
  ConceptNet(const std::string &fsafile, const std::string &datafile, FileAccessMethod fam = FILE_ACCESS_UNDEF);

  /**
   * @brief Destructor.
   */
  virtual ~ConceptNet();

  /**
   * @brief Check if initialization was successful.
   *
   * @return True if the initialization of the object succeeded.
   */
  bool isOk() const
  {
    return _ok;
  }

  /**
   * @brief Get the concept net %FSA.
   *
   * Get the concept net %FSA. The object continues to be owned by the
   * concept net.
   *
   * @return The concept net %FSA.
   */
  const FSA& getFSA() const
  {
    assert(_ok);
    return _unit_fsa;
  }

  /**
   * @brief Look up a unit.
   *
   * Look up a unit in the concept net, and get its index.
   *
   * @param unit Unit string.
   * @return Index of the unit, or -1 if not found.
   */
  int lookup(const char *unit) const;

  /**
   * @brief Look up a unit index.
   *
   * Look up a unit index in the concept net, and get the unit string.
   *
   * @param idx Unit index.
   * @return Pointer to the unit string, or NULL if index is out of range.
   */
  const char * lookup(int idx) const;

  /**
   * @brief Get the unit frequency of the unit.
   *
   * @param idx Unit index.
   * @return Unit frequency, or -1 if the index is out of range.
   */
  int frq(int idx) const;

  /**
   * @brief Get the unit frequency of the unit.
   *
   * @param unit Unit string.
   * @return Unit frequency, or -1 if the unit is not found.
   */
  int frq(const char *unit) const;

  /**
   * @brief Get the frequency of the unit as a complete query.
   *
   * @param idx Unit index.
   * @return Unit-C frequency, or -1 if the index is out of range.
   */
  int cFrq(int idx) const;

  /**
   * @brief Get the frequency of the unit as a complete query.
   *
   * @param unit Unit string.
   * @return Unit-C frequency, or -1 if the unit is not found.
   */
  int cFrq(const char *unit) const;

  /**
   * @brief Get the frequency of the unit as part of a query.
   *
   * @param idx Unit index.
   * @return Unit-Q frequency, or -1 if the index is out of range.
   */
  int qFrq(int idx) const;

  /**
   * @brief Get the frequency of the unit as part of a query.
   *
   * @param unit Unit string.
   * @return Unit-Q frequency, or -1 if the unit is not found.
   */
  int qFrq(const char *unit) const;

  /**
   * @brief Get the frequency of queries containing all terms of the unit.
   *
   * @param idx Unit index.
   * @return Unit-S frequency, or -1 if the index is out of range.
   */
  int sFrq(int idx) const;

  /**
   * @brief Get the frequency of queries containing all terms of the unit.
   *
   * @param unit Unit string.
   * @return Unit-Q frequency, or -1 if the unit is not found.
   */
  int sFrq(const char *unit) const;

  /**
   * @brief Get the unit score (100.0*cFrq/qFrq).
   *
   * @param idx Unit index.
   * @return Unit score, or -1.0 if the index is out of range.
   */
  double score(int idx) const;

  /**
   * @brief Get the unit score (100.0*cFrq/qFrq).
   *
   * @param unit Unit string.
   * @return Unit score, or -1. if the unit is not found.
   */
  double score(const char *unit) const;

  /**
   * @brief Get the unit strength (100.0*qFrq/sFrq).
   *
   * @param idx Unit index.
   * @return Unit strength, or -1.0 if the index is out of range.
   */
  double strength(int idx) const;

  /**
   * @brief Get the unit strength (100.0*qFrq/sFrq).
   *
   * @param unit Unit string.
   * @return Unit strength, or -1. if the unit is not found.
   */
  double strength(const char *unit) const;

  /**
   * @brief Get the number of extensions for the unit.
   *
   * @param idx Unit index.
   * @return Number of extensions for the unit, -1 if the index is out
   *         of range.
   */
  int numExt(int idx) const;

  /**
   * @brief Get the number of associations for the unit.
   *
   * @param idx Unit index.
   * @return Number of associations for the unit, -1 if the index is out
   *         of range.
   */
  int numAssoc(int idx) const;

  /**
   * @brief Get the number of categories for the unit.
   *
   * @param idx Unit index.
   * @return Number of categories for the unit, -1 if the index is out
   *         of range.
   */
  int numCat(int idx) const;

  /**
   * @brief Get the index of an extension.
   *
   * @param idx Unit index.
   * @param j Number of the extension (extensions of each unit are
   *          sorted by decreasing weight).
   * @return Extension (unit) index, -1 if idx or j is out
   *         of range.
   */
  int ext(int idx, int j) const;

  /**
   * @brief Get the frequency of an extension.
   *
   * @param idx Unit index.
   * @param j Number of the extension (extensions of each unit are
   *          sorted by decreasing weight).
   * @return Extension frequency, -1 if idx or j is out
   *         of range.
   */
  int extFrq(int idx, int j) const;

  /**
   * @brief Get the index of an association.
   *
   * @param idx Unit index.
   * @param j Number of the association (associations of each unit are
   *          sorted by decreasing weight).
   * @return Association (unit) index, -1 if idx or j is out
   *         of range.
   */
  int assoc(int idx, int j) const;

  /**
   * @brief Get the frequency of an association.
   *
   * @param idx Unit index.
   * @param j Number of the association (associations of each unit are
   *          sorted by decreasing weight).
   * @return Association frequency, -1 if idx or j is out
   *         of range.
   */
  int assocFrq(int idx, int j) const;

  /**
   * @brief Get the index of a category.
   *
   * @param idx Unit index.
   * @param j Number of the category.
   * @return Catergory index, -1 if idx or j is out of range.
   */
  int cat(int idx, int j) const;

  /**
   * @brief Get the name of a category.
   *
   * @param catIdx Category index.
   * @return Catergory name, or NULL if catIdx is out of range.
   */
  const char *catName(int catIdx) const;

};

// }}}

} // namespace fsa

