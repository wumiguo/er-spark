package org.wumiguo.ser.flow

import org.apache.spark.rdd.RDD
import org.wumiguo.ser.dataloader.filter.SpecificFieldValueFilter
import org.wumiguo.ser.dataloader.{DataTypeResolver, ProfileLoaderFactory, ProfileLoaderTrait}
import org.wumiguo.ser.entity.parameter.DataSetConfig
import org.wumiguo.ser.flow.SchemaBasedSimJoinECFlow.log
import org.wumiguo.ser.flow.configuration.{DataSetConfiguration, FilterOptions}
import org.wumiguo.ser.methods.datastructure.Profile
import org.wumiguo.ser.methods.similarityjoins.common.CommonFunctions

import scala.collection.mutable.ArrayBuffer

/**
 * @author levinliu
 *         Created on 2020/9/10
 *         (Change file header on Settings -> Editor -> File and Code Templates)
 */
trait SimJoinCommonTrait {
  val debug = false

  def collectAttributesFromProfiles(profiles1: RDD[Profile], profiles2: RDD[Profile], dataSet1: DataSetConfiguration, dataSet2: DataSetConfiguration):
  ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])] = {
    var attributesArray = new ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
    log.info("dataSet1Attr=" + dataSet1.joinAttrs.toList + " vs dataSet2Attr=" + dataSet2.joinAttrs.toList)
    for (i <- 0 until dataSet1.joinAttrs.length) {
      val attributes1 = CommonFunctions.extractField(profiles1, dataSet1.joinAttrs(i))
      val attributes2 = Option(dataSet2.joinAttrs).map(x => CommonFunctions.extractField(profiles2, x(i))).orNull
      attributesArray :+= ((attributes1, attributes2))
    }
    log.info("attrsArrayLength=" + attributesArray.length)
    if (debug && attributesArray.length > 0) {
      log.info("attrsArrayHead _1count=" + attributesArray.head._1.count() + ", _2count=" + attributesArray.head._2.count())
      log.info("attrsArrayHead _1first=" + attributesArray.head._1.first() + ", _2first=" + attributesArray.head._2.first())
    }
    attributesArray
  }


  def collectAttributesPairFromProfiles(profiles1: RDD[Profile], profiles2: RDD[Profile], dataSet1: DataSetConfiguration, dataSet2: DataSetConfiguration):
  (RDD[(Int, Array[String])], RDD[(Int, Array[String])]) = {
    log.info("dataSet1Attr=" + dataSet1.joinAttrs.toList + " vs dataSet2Attr=" + dataSet2.joinAttrs.toList)
    val attributes1 = CommonFunctions.extractFieldArray(profiles1, dataSet1.joinAttrs.toArray)
    val attributes2 = CommonFunctions.extractFieldArray(profiles2, dataSet2.joinAttrs.toArray)
    //    if (!attributes1.isEmpty()) {
    //      attributes1.take(3).foreach(x => log.info("dataSet1=" + x._1 + " " + x._2.toSeq))
    //    }
    //    if (!attributes2.isEmpty()) {
    //      attributes2.take(3).foreach(x => log.info("dataSet2=" + x._1 + " " + x._2.toSeq))
    //    }
    val attributesArray = (attributes1, attributes2)
    attributesArray
  }

  def preCheckOnProfile(numberOfProfile: Long) = {
    if (numberOfProfile <= 0) {
      throw new RuntimeException("Empty profile data set")
    }
  }

  def preCheckOnProfile(profiles: RDD[Profile]) = {
    if (profiles.isEmpty()) {
      throw new RuntimeException("Empty profile data set")
    }
  }


  def getProfileLoader(dataFile: String): ProfileLoaderTrait = {
    ProfileLoaderFactory.getDataLoader(DataTypeResolver.getDataType(dataFile))
  }


  def preCheckOnWeight(weights: List[Double]) = {
    val sum = weights.reduce(_ + _)
    if (sum != 1.0) {
      throw new RuntimeException("Cannot continue with weights summary > 1.0, sum=" + sum + " given weights=" + weights)
    }
  }


  def checkAndResolveWeights(joinFieldsWeight: String, dataSet1: DataSetConfig) = {
    val weights = joinFieldsWeight.split(',').toList
    if (weights.size != dataSet1.attributes.size) {
      throw new RuntimeException("Cannot resolve same weight size as the given attributes size ")
    }
    weights.map(_.toDouble)
  }

  def checkAndResolveWeights(joinFieldsWeight: String, dataSet1: DataSetConfiguration) = {
    val weights = joinFieldsWeight.split(',').toList
    if (weights.size != dataSet1.joinAttrs.size) {
      throw new RuntimeException("Cannot resolve same weight size as the given attributes size ")
    }
    weights.map(_.toDouble)
  }

  def preCheckOnAttributePair(dataSet1: DataSetConfig, dataSet2: DataSetConfig) = {
    if (dataSet1.attributes.size == 0 || dataSet2.attributes.size == 0) {
      throw new RuntimeException("Cannot join data set with no attributed")
    }
    if (dataSet1.attributes.size != dataSet2.attributes.size) {
      throw new RuntimeException("Cannot join if the attribute pair size not same on two data set")
    }
  }

  def preCheckOnAttributePair(dataSet1: DataSetConfiguration, dataSet2: DataSetConfiguration) = {
    if (dataSet1.joinAttrs.length == 0 || dataSet2.joinAttrs.length == 0) {
      throw new RuntimeException("Cannot join data set with no attribute")
    }
    if (dataSet1.joinAttrs.length != dataSet2.joinAttrs.length) {
      throw new RuntimeException("Cannot join on attribute pair with different length")
    }
  }


  def loadDataWithGivenOptionOnly(dataSetConfig: DataSetConfiguration,
                                  epStartID: Int, sourceId: Int): RDD[Profile] = {
    val path = dataSetConfig.path
    val loader = ProfileLoaderFactory.getDataLoader(DataTypeResolver.getDataType(path))
    log.info("profileLoader is " + loader)
    val data = loader.load(
      path, realIDField = dataSetConfig.idField,
      startIDFrom = epStartID,
      sourceId = sourceId, keepRealID = dataSetConfig.includeRealID,
      fieldsToKeep = dataSetConfig.joinAttrs.toList,
      fieldValuesScope = dataSetConfig.filterOptions.toList,
      filter = SpecificFieldValueFilter)
    data
  }


  /**
   * load data with filter option and additional fields in one go,
   * which is to load all required data in one-off data loading
   *
   * @param dataSetConfig
   * @param epStartID
   * @param sourceId
   * @return
   */
  def loadDataInOneGo(dataSetConfig: DataSetConfiguration,
                      epStartID: Int, sourceId: Int): RDD[Profile] = {
    val path = dataSetConfig.path
    val loader = ProfileLoaderFactory.getDataLoader(DataTypeResolver.getDataType(path))
    log.info("profileLoader is " + loader)
    val data = loader.load(
      path, realIDField = dataSetConfig.idField,
      startIDFrom = epStartID,
      sourceId = sourceId, keepRealID = dataSetConfig.includeRealID,
      fieldsToKeep = (dataSetConfig.joinAttrs.toList ++ dataSetConfig.additionalAttrs),
      fieldValuesScope = dataSetConfig.filterOptions.toList,
      filter = SpecificFieldValueFilter)
    data
  }
}
