package org.wumiguo.ser.methods.similarityjoins.simjoin

import java.util.Calendar

import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.wumiguo.ser.methods.datastructure.EDJoinPrefixIndexPartitioner
import org.wumiguo.ser.methods.similarityjoins.common.ed.{CommonEdFunctions, EdFilters}
import org.slf4j.LoggerFactory

/**
 * @author levinliu
 *         Created on 2020/9/10
 *         (Change file header on Settings -> Editor -> File and Code Templates)
 *
 *         PRELIMINARIES
 *         1.
 *         A q-gram is a contiguous substring of length q; and its starting position in a string is called its position or location.
 *         A positional q-gram is a q-gram together with its position, usually represented in the form of (token,pos)
 *
 * 2.Count Filtering mandates that s and t must share at least LBs;t = (max(|s|,|t|) − q + 1) − q · τ common q-grams.
 *
 * 3.Length Filtering mandates that ||s| − |t|| ≤ τ.
 */
object EDBatchJoin {
  /**
   * since the blocks all has at least one token common in the prefix, the prefix filtering is completed in here while doing the groupByKey
   */
  def buildPrefixIndex(sortedDocs: RDD[(Int, String, Array[(Int, Int)])], qgramLen: Int, threshold: Int): RDD[(Int, Array[(Int, Int, Array[(Int, Int)], String)])] = {
    val prefixLen = EdFilters.getPrefixLen(qgramLen, threshold)

    //output [(tokenId,(docId,index of q-gram,q-grams/*tokenId,token index of q-gram */,string)...)]
    val allQgrams = sortedDocs.flatMap { case (docId, doc, qgrams) =>
      val prefix = qgrams.take(prefixLen)
      prefix.zipWithIndex.map { case (qgram, index) =>
        (qgram._1, (docId, index, qgrams, doc))
      }
    }


    //output [(tokenId,[strings share the same token])...],and filter the token only be owned by one string
    val blocks = allQgrams.groupByKey().filter(_._2.size > 1)

    blocks.map(b => (b._1, b._2.toArray.sortBy(_._3.length)))
  }

  val log = LoggerFactory.getLogger(getClass.getName)

  /**
   * filter token that is unique, which means no other item can match with it
   *
   * @param sortedDocs
   * @param qgramLen
   * @param threshold
   * @return
   */
  def buildPrefixIndexV2(sortedDocs: RDD[(Int, Int, String, Array[(Int, Int)])], qgramLen: Int, threshold: Int): RDD[(Int, Array[(Int, Int, Array[(Int, Int)], String)])] = {
    val prefixLen = EdFilters.getPrefixLen(qgramLen, threshold)

    //output [(tokenId,(docId,index of q-gram,q-grams/*tokenId,token index of q-gram */,string)...)]
    val allQgrams = sortedDocs.flatMap { case (attrId, docId, doc, qgrams) =>
      val prefix = qgrams.take(prefixLen)
      //log.info("prefix=" + prefix.map(x => (x._1, x._2)).toList)
      prefix.zipWithIndex.map { case (qgram, index) =>
        (qgram._1, (docId, index, qgrams, doc))
      }
    }

    //allQgrams.take(3).foreach(x => log.info("oneofgram=" + x._1 + ", 1=" + x._2._1 + ",2=" + x._2._2 + "，3=" + x._2._3.toList + ",4=" + x._2._4))

    //output [(tokenId,[strings share the same token])...],and filter the token only be owned by one string
    val blocks = allQgrams.groupByKey().filter(_._2.size > 1)

    blocks.map(b => (b._1, b._2.toArray.sortBy(_._3.length)))
  }


  def buildPrefixIndexV3(sortedDocs: RDD[(Int, Int, String, Array[(Int, Int)])], qgramLen: Int, threshold: Int): RDD[(Int, Int, Array[(Int, Int, Array[(Int, Int)], String)])] = {
    val prefixLen = EdFilters.getPrefixLen(qgramLen, threshold)

    //output [(tokenId,(docId,index of q-gram,q-grams/*tokenId,token index of q-gram */,string)...)]
    val allQgrams = sortedDocs.groupBy(_._1).flatMap {
      case (attrId, doc) => {
        val allQg = doc.flatMap {
          case (attrId, docId, text, qgrams) => {
            val prefix = qgrams.take(prefixLen)
            prefix.zipWithIndex.map { case (qgram, index) => (qgram._1, (docId, index, qgrams, text)) }
          }
        }
        val blocks = allQg.groupBy(_._1).filter(_._2.size > 1)
        blocks.map(b => (attrId, b._1, b._2.map(x => x._2).toArray.sortBy(_._3.length)))
      }
    }
    allQgrams
  }

  /**
   * Returns true if the token of the current block is the last common token
   *
   * @param doc1Tokens   tokens of the first document
   * @param doc2Tokens   tokens of the second document
   * @param currentToken id of the current block in which the documents co-occurs
   *
   */
  def isLastCommonTokenPosition(doc1Tokens: Array[(Int, Int)], doc2Tokens: Array[(Int, Int)], currentToken: Int, qgramLen: Int, threshold: Int): Boolean = {
    val prefixLen = EdFilters.getPrefixLen(qgramLen, threshold)
    var d1Index = math.min(doc1Tokens.length - 1, prefixLen - 1)
    var d2Index = math.min(doc2Tokens.length - 1, prefixLen - 1)
    var valid = true
    var continue = true

    /**
     * Starting from the prefix looking for the last common token
     * One exists for sure
     **/
    while (d1Index >= 0 && d2Index >= 0 && continue) {
      /**
       * Common token
       **/
      if (doc1Tokens(d1Index)._1 == doc2Tokens(d2Index)._1) {
        /**
         * If the token is the same of the current block, stop the process
         **/
        if (currentToken == doc1Tokens(d1Index)._1) {
          continue = false
        }
        else {
          /**
           * If it is different, it is not considered valid: needed to avoid to emit duplicates
           **/
          continue = false
          valid = false
        }
      }

      /**
       * Decrement the indexes (note: the tokens are sorted)
       **/
      else if (doc1Tokens(d1Index)._1 > doc2Tokens(d2Index)._1) {
        d1Index -= 1
      }
      else {
        d2Index -= 1
      }
    }
    valid
  }


  def getCandidatePairs(prefixIndex: RDD[(Int, Array[(Int, Int, Array[(Int, Int)], String)])], qgramLength: Int, threshold: Int): RDD[((Int, String), (Int, String))] = {
    /**
     * Repartitions the blocks of the index based on the number of maximum comparisons involved by each block
     */
    val customPartitioner = new EDJoinPrefixIndexPartitioner(prefixIndex.getNumPartitions)
    val repartitionIndex = prefixIndex.map(_.swap).sortBy(x => -(x._1.length * (x._1.length - 1))).partitionBy(customPartitioner)

    repartitionIndex.flatMap { case (
      block /*string contain same token in the prefix*/ ,
      blockId /*tokenId*/ ) =>
      val results = new scala.collection.mutable.HashSet[((Int, String), (Int, String))]()

      var i = 0
      while (i < block.length) {
        var j = i + 1
        val d1Id = block(i)._1 // docId
        val d1Pos = block(i)._2 // index of the prefix
        val d1Qgrams = block(i)._3
        val d1 = block(i)._4 //the string 1

        while (j < block.length) {
          val d2Id = block(j)._1 // docId
          val d2Pos = block(j)._2 // index of the prefix
          val d2Qgrams = block(j)._3
          val d2 = block(j)._4 //the string 2

          if (d1Id != d2Id &&
            //make sure each string pair will only be do the common filter once(which consume lots of compute resource)
            isLastCommonTokenPosition(d1Qgrams, d2Qgrams, blockId, qgramLength, threshold) &&
            math.abs(d1Pos - d2Pos) <= threshold &&
            //length filtering
            math.abs(d1Qgrams.length - d2Qgrams.length) <= threshold
          ) {
            if (EdFilters.commonFilter(d1Qgrams, d2Qgrams, qgramLength, threshold)) {
              //avoid add duplicated pair
              if (d1Id < d2Id) {
                results.add(((d1Id, d1), (d2Id, d2)))
              }
              else {
                results.add(((d2Id, d2), (d1Id, d1)))
              }
            }
          }
          j += 1
        }
        i += 1
      }
      results
    }
  }


  def getCandidatePairsV2(prefixIndex: RDD[(Int, Int, Array[(Int, Int, Array[(Int, Int)], String)])], qgramLength: Int, threshold: Int): RDD[(Int, ((Int, String), (Int, String)))] = {
    prefixIndex.groupBy(_._1).flatMap {
      case (attrId, indexDetail) => {
        val repartitionIndex = indexDetail.map(x => (x._2, x._3)).map(_.swap)
        repartitionIndex.flatMap { case (
          block /*string contain same token in the prefix*/ ,
          blockId /*tokenId*/ ) =>
          val results = new scala.collection.mutable.HashSet[(Int, ((Int, String), (Int, String)))]()

          var i = 0
          while (i < block.length) {
            var j = i + 1
            val d1Id = block(i)._1 // docId
            val d1Pos = block(i)._2 // index of the prefix
            val d1Qgrams = block(i)._3
            val d1 = block(i)._4 //the string 1

            while (j < block.length) {
              val d2Id = block(j)._1 // docId
              val d2Pos = block(j)._2 // index of the prefix
              val d2Qgrams = block(j)._3
              val d2 = block(j)._4 //the string 2

              if (d1Id != d2Id &&
                //make sure each string pair will only be do the common filter once(which consume lots of compute resource)
                isLastCommonTokenPosition(d1Qgrams, d2Qgrams, blockId, qgramLength, threshold) &&
                math.abs(d1Pos - d2Pos) <= threshold &&
                //length filtering
                math.abs(d1Qgrams.length - d2Qgrams.length) <= threshold
              ) {
                if (EdFilters.commonFilter(d1Qgrams, d2Qgrams, qgramLength, threshold)) {
                  //avoid add duplicated pair
                  if (d1Id < d2Id) {
                    results.add((attrId, ((d1Id, d1), (d2Id, d2))))
                  }
                  else {
                    results.add((attrId, ((d2Id, d2), (d1Id, d1))))
                  }
                }
              }
              j += 1
            }
            i += 1
          }
          results
        }
      }
    }
  }


  def getPositionalQGrams(documents: RDD[(Int, Array[String])], qgramLength: Int): RDD[(Int, Int, String, Array[(String, Int)])] = {
    documents.flatMap(x => {
      x._2.zipWithIndex.map { case (str, attrId) => (attrId, x._1, str, CommonEdFunctions.getQgrams(str, qgramLength)) }
    })
  }

  def getCandidatesV0(documents: RDD[(Int, Array[String])], qgramLength: Int, threshold: Int): RDD[((Int, String), (Int, String))] = {
    //Transforms the documents into n-grams
    //output example
    //(docId,string,positional q-gram)
    //(P001,Array("hello","test")) =>(0,P001,(he,0),(el,1),(ll,2),(lo,3)),(1,P001,(te,0),(es,1),(st,2))
    val docs = getPositionalQGrams(documents, qgramLength)
    val log = LogManager.getRootLogger

    //Sorts the n-grams by their document frequency
    /** From the paper
     * We can extract all the positional q-grams of a string and order them by decreasing order of their idf values and increasing order of their locations.
     */
    //output [(attrId,docId,string,[(token index,token position of q-gram),...]),...]
    //q-gram is order by rare decreasing, the rarest one in the head of the array
    val sortedDocs = CommonEdFunctions.getSortedQgrams3(docs)
    sortedDocs.persist(StorageLevel.MEMORY_AND_DISK)
    val ts = Calendar.getInstance().getTimeInMillis
    //output [(tokenId,[strings contain same token])...]
    val prefixIndex = buildPrefixIndex(sortedDocs, qgramLength, threshold)
    prefixIndex.persist(StorageLevel.MEMORY_AND_DISK)
    sortedDocs.unpersist()
    val te = Calendar.getInstance().getTimeInMillis
    // perfDebug(prefixIndex)
    log.info("[EDJoin] EDJOIN index time (s) " + (te - ts) / 1000.0)

    val t1 = Calendar.getInstance().getTimeInMillis
    val candidates = getCandidatePairs(prefixIndex, qgramLength, threshold)
    // val nc = candidates.count()
    prefixIndex.unpersist()
    val t2 = Calendar.getInstance().getTimeInMillis
    // log.info("[EDJoin] Candidates number " + nc)
    log.info("[EDJoin] EDJOIN join time (s) " + (t2 - t1) / 1000.0)

    candidates
  }


  private def perfDebug(prefixIndex: RDD[(Int, Array[(Int, Int, Array[(Int, Int)], String)])]) = {
    val np = prefixIndex.count()
    log.info("[EDJoin] Number of elements in the index " + np)
    if (!prefixIndex.isEmpty()) {
      //only use to do the statistics, not a part of the algorithm
      val a = prefixIndex.map(x => x._2.length.toDouble * (x._2.length - 1))
      val min = a.min()
      val max = a.max()
      val cnum = a.sum()
      val avg = cnum / np

      log.info("[EDJoin] Min number of comparisons " + min)
      log.info("[EDJoin] Max number of comparisons " + max)
      log.info("[EDJoin] Avg number of comparisons " + avg)
      log.info("[EDJoin] Estimated comparisons " + cnum)
    }
  }

  def getCandidates(documents: RDD[(Int, Array[String])], qgramLength: Int, threshold: Int): RDD[(Int, ((Int, String), (Int, String)))] = {
    //Transforms the documents into n-grams
    //output example
    //(docId,string,positional q-gram)
    //(P001,Array("hello","test")) =>(0,P001,(he,0),(el,1),(ll,2),(lo,3)),(1,P001,(te,0),(es,1),(st,2))
    val docs = getPositionalQGrams(documents, qgramLength)
    val log = LogManager.getRootLogger

    //Sorts the n-grams by their document frequency
    /** From the paper
     * We can extract all the positional q-grams of a string and order them by decreasing order of their idf values and increasing order of their locations.
     */
    //output [(attrId,docId,string,[(token index,token position of q-gram),...]),...]
    //q-gram is order by rare decreasing, the rarest one in the head of the array
    val sortedDocs = CommonEdFunctions.getSortedQgrams4(docs)
    sortedDocs.persist(StorageLevel.MEMORY_AND_DISK)
    // log.info("[EDJoin] sorted docs count " + sortedDocs.count())
    val ts = Calendar.getInstance().getTimeInMillis
    //output [(tokenId,[strings contain same token])...]
    val prefixIndex = buildPrefixIndexV3(sortedDocs, qgramLength, threshold)
    prefixIndex.persist(StorageLevel.MEMORY_AND_DISK)
    sortedDocs.unpersist()
    val te = Calendar.getInstance().getTimeInMillis
    // statisticsOnCandidate(prefixIndex)
    log.info("[EDJoin] EDJOIN index time (s) " + (te - ts) / 1000.0)

    val t1 = Calendar.getInstance().getTimeInMillis
    val candidates = getCandidatePairsV2(prefixIndex, qgramLength, threshold)
    prefixIndex.unpersist()
    val t2 = Calendar.getInstance().getTimeInMillis
    log.info("[EDJoin] EDJOIN join time (s) " + (t2 - t1) / 1000.0)

    candidates
  }

  private def statisticsOnCandidate(prefixIndex: RDD[(Int, Int, Array[(Int, Int, Array[(Int, Int)], String)])]) = {
    val np = prefixIndex.count()
    log.info("[EDJoin] Number of elements in the index " + np)
    if (!prefixIndex.isEmpty()) {
      //only use to do the statistics, not a part of the algorithm
      val a = prefixIndex.map(x => x._3.length.toDouble * (x._3.length - 1))
      val min = a.min()
      val max = a.max()
      val cnum = a.sum()
      val avg = cnum / np

      log.info("[EDJoin] Min number of comparisons " + min)
      log.info("[EDJoin] Max number of comparisons " + max)
      log.info("[EDJoin] Avg number of comparisons " + avg)
      log.info("[EDJoin] Estimated comparisons " + cnum)
    }
  }

  def getMatches(documents: RDD[(Int, Array[String])], qgramLength: Int, threshold: Int): RDD[(Int, Int, Double)] = {
    val log = LogManager.getRootLogger

    val t1 = Calendar.getInstance().getTimeInMillis
    val candidates = getCandidatesV0(documents, qgramLength, threshold)

    val t2 = Calendar.getInstance().getTimeInMillis

    val m = candidates.map { case ((d1Id, d1), (d2Id, d2)) => ((d1Id, d1), (d2Id, d2), CommonEdFunctions.editDist(d1, d2)) }
      .filter(_._3 <= threshold)
      .map { case ((d1Id, d1), (d2Id, d2), ed) => (d1Id, d2Id, ed.toDouble) }
    m.persist(StorageLevel.MEMORY_AND_DISK)
    // val nm = m.count()
    val t3 = Calendar.getInstance().getTimeInMillis
    //log.info("[EDJoin] Num matches " + nm)
    log.info("[EDJoin] Verify time (s) " + (t3 - t2) / 1000.0)
    log.info("[EDJoin] Global time (s) " + (t3 - t1) / 1000.0)
    m
  }

  def getMatchesV2(documents: RDD[(Int, Array[String])], qgramLength: Int, threshold: Int, weighted: Boolean, weightIndex: Map[Int, Double]): RDD[(Int, Int, Double)] = {
    val attrLength = if (documents.isEmpty) {
      0
    } else {
      //low performance
      documents.first()._2.size
    }
    getMatchesV2(documents, attrLength, qgramLength, threshold, weighted, weightIndex)
  }

  /**
   * weighted matches
   *
   * @param documents
   * @param qgramLength
   * @param threshold
   * @param weightIndex
   * @return
   */
  def getMatchesV2(documents: RDD[(Int, Array[String])], attrLength: Int, qgramLength: Int, threshold: Int, weighted: Boolean, weightIndex: Map[Int, Double]): RDD[(Int, Int, Double)] = {
    val log = LogManager.getRootLogger
    val t1 = Calendar.getInstance().getTimeInMillis
    log.info("[EDBatchJoin] start getMatchesV2")
    val candidates = getCandidates(documents, qgramLength, threshold)
    val t2 = Calendar.getInstance().getTimeInMillis
    log.info("[EDBatchJoin] candidates ready: " + (t2 - t1) + " ms")

    val matchesWithSimilarity = candidates.map { case (attrId, ((d1Id, d1), (d2Id, d2))) => {
      (attrId, (d1Id, d1), (d2Id, d2), CommonEdFunctions.editDist(d1, d2), Math.max(d1.length, d2.length))
    }
    }
      .filter(_._4 <= threshold)
      .map(x => (x._1, x._2, x._3, (x._5 - x._4) / x._5.toDouble))
      .map {
        case (attrId, (d1Id, d1), (d2Id, d2), ed) => {
          if (weighted) {
            (attrId, d1Id, d2Id, ed.toDouble * weightIndex(attrId))
          } else {
            (attrId, d1Id, d2Id, ed.toDouble / attrLength)
          }
        }
      }
    matchesWithSimilarity.persist(StorageLevel.MEMORY_AND_DISK)
    //    val nm = matchesWithSimilarity.count()
    val t3 = Calendar.getInstance().getTimeInMillis
    //    log.info("[EDJoin] Num matches " + nm)
    log.info("[EDBatchJoin] Verify time (s) " + (t3 - t2) / 1000.0)
    log.info("[EDBatchJoin] Global time (s) " + (t3 - t1) / 1000.0)
    val accumulatedResult = matchesWithSimilarity.groupBy(x => (x._2, x._3)).map {
      case ((p1, p2), detail) => {
        val res = detail.map(x => (x._2, x._3, x._4)).reduce((x, y) => (x._1, x._2, x._3 + y._3))
        res
      }
    }
    accumulatedResult
  }

}
