package org.wumiguo.ser.flow

import org.apache.spark.rdd.RDD
import org.scalatest.flatspec.AnyFlatSpec
import org.wumiguo.ser.common.SparkEnvSetup
import org.wumiguo.ser.flow.SchemaBasedBatchV2SimJoinECFlow.doJoin
import org.wumiguo.ser.flow.configuration.FlowOptions

import scala.collection.mutable.ArrayBuffer

/**
 * @author levinliu
 *         Created on 2020/8/27
 *         (Change file header on Settings -> Editor -> File and Code Templates)
 */
class SchemaBasedBatchV2SimJoinECFlowTest extends AnyFlatSpec with SparkEnvSetup {
  val spark = createLocalSparkSession(getClass.getName)
  it should "doJoin weighted " in {
    val flowOptions = FlowOptions.getOptions(Array("optionSize=3", "option0=algorithm:EDJoin", "option1=threshold:0", "option2=q:2"))
    var attrPairArray = (
      spark.sparkContext.makeRDD(Seq((1, Array("AE0024")))),
      spark.sparkContext.makeRDD(Seq((5, Array("GAE0024"))))
    )
    val res = doJoin(flowOptions, attrPairArray, true, List(1.0))
    assertResult(true)(res.isEmpty())
    val flowOptions2 = FlowOptions.getOptions(Array("optionSize=3", "option0=algorithm:EDJoin", "option1=threshold:1", "option2=q:2"))
    val res2 = doJoin(flowOptions2, attrPairArray, true, List(1.0))
    assertResult(false)(res2.isEmpty())
    assertResult(List((1, 5, 0.8571428571428571)))(res2.collect.toList)
    attrPairArray = (
      spark.sparkContext.makeRDD(Seq((1, Array("AE0024")))),
      spark.sparkContext.makeRDD(Seq((5, Array("AE0024"))))
    )
    val res3 = doJoin(flowOptions2, attrPairArray, true, List(1.0))
    assertResult(List((1, 5, 1.0)))(res3.collect.toList)
    attrPairArray = (
      spark.sparkContext.makeRDD(Seq((1, Array("AE0024", "DEFG")))),
      spark.sparkContext.makeRDD(Seq((5, Array("AE0024", "DEFF"))))
    )
    val res4 = doJoin(flowOptions2, attrPairArray, true, List(1.0, 0.0))
    assertResult(List((1, 5, 1.0)))(res4.collect.toList)
    val res5 = doJoin(flowOptions2, attrPairArray, true, List(0.5, 0.5))
    assertResult(List((1, 5, 0.875)))(res5.collect.toList)
  }
  //
  //  it should "doJoin unweighted" in {
  //    val flowOptions = FlowOptions.getOptions(Array("optionSize=3", "option0=algorithm:EDJoin", "option1=threshold:0", "option2=q:2"))
  //    val weights = List(1.0)
  //    val weighted = false
  //    var attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "GAE0024"))))
  //    val res = doJoin(flowOptions, attrPairArray, weighted, weights)
  //    assertResult(true)(res.isEmpty())
  //    val flowOptions2 = FlowOptions.getOptions(Array("optionSize=3", "option0=algorithm:EDJoin", "option1=threshold:1", "option2=q:2"))
  //    val res2 = doJoin(flowOptions2, attrPairArray, weighted, weights)
  //    assertResult(false)(res2.isEmpty())
  //    assertResult(List((1, 5, 1.0)))(res2.collect.toList)
  //    attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "AE0024"))))
  //    val res3 = doJoin(flowOptions2, attrPairArray, weighted, weights)
  //    assertResult(List((1, 5, 0.0)))(res3.collect.toList)
  //    attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "AE0024"))))
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "UPGK882"))), spark.sparkContext.makeRDD(Seq((5, "UPGK882"))))
  //    val res4 = doJoin(flowOptions2, attrPairArray, weighted, weights).collect.toList
  //    assertResult(List((1, 5, 0.0)))(res4)
  //  }
  //
  //  it should "doJoin weighted simple" in {
  //    val flowOptions0 = FlowOptions.getOptions(Array("optionSize=4", "option0=algorithm:EDJoin", "option1=threshold:0", "option2=q:2", "option3=scale:2"))
  //    val flowOptions = FlowOptions.getOptions(Array("optionSize=4", "option0=algorithm:EDJoin", "option1=threshold:1", "option2=q:2", "option3=scale:2"))
  //    val weights = List(0.2, 0.8)
  //    var attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "GAU0024"))))
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "UPGK882"))), spark.sparkContext.makeRDD(Seq((5, "UPGK882I"))))
  //    val res0 = doJoin(flowOptions0, attrPairArray, true, weights).collect.toList
  //    assertResult(List())(res0)
  //    var res = doJoin(flowOptions, attrPairArray, true, weights).collect.toList
  //    assertResult(List((1, 5, 0.4)))(res)
  //    val flowOptions2 = FlowOptions.getOptions(Array("optionSize=4", "option0=algorithm:EDJoin", "option1=threshold:2", "option2=q:2", "option3=scale:2"))
  //    res = doJoin(flowOptions2, attrPairArray, true, weights).collect.toList
  //    assertResult(List((1, 5, 0.47)))(res)
  //    attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "AE0024"))))
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "UPGK882"))), spark.sparkContext.makeRDD(Seq((5, "UPGK882I"))))
  //    res = doJoin(flowOptions2, attrPairArray, true, weights).collect.toList
  //    assertResult(List((1, 5, 0.6)))(res)
  //    attrPairArray = ArrayBuffer[(RDD[(Int, String)], RDD[(Int, String)])]()
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "AE0024"))), spark.sparkContext.makeRDD(Seq((5, "AE0024"))))
  //    attrPairArray :+= (spark.sparkContext.makeRDD(Seq((1, "UPGK882"))), spark.sparkContext.makeRDD(Seq((5, "UPGK882"))))
  //    res = doJoin(flowOptions2, attrPairArray, true, weights).collect.toList
  //    assertResult(List((1, 5, 1.0)))(res)
  //  }
}
