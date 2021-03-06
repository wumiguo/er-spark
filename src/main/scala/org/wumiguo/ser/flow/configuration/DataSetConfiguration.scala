package org.wumiguo.ser.flow.configuration

import org.wumiguo.ser.entity.parameter.DataSetConfig
import org.wumiguo.ser.methods.datastructure.KeyValue

import scala.beans.BeanProperty
import scala.collection.mutable

/**
 * @author levinliu
 *         Created on 2020/9/2
 *         (Change file header on Settings -> Editor -> File and Code Templates)
 */
case class DataSetConfiguration(
                                 @BeanProperty var path: String,
                                 @BeanProperty var idField: String,
                                 @BeanProperty var joinAttrs: Seq[String] = Seq(),
                                 @BeanProperty var additionalAttrs: Seq[String] = Seq(),
                                 @BeanProperty var filterOptions: Seq[KeyValue] = Seq()
                               ) {
  override def toString: String = {
    "DataSetConfiguration(\"" + path + "\"," +
      idField + "," +
      joinAttrs.toList + "," +
      additionalAttrs.toList + "," +
      filterOptions.toList + ")"
  }

  def includeRealID(): Boolean = idField != null && !idField.trim.isEmpty && joinAttrs.contains(idField)

}
