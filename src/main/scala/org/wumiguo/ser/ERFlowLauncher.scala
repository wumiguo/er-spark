package org.wumiguo.ser

import org.slf4j.LoggerFactory
import org.wumiguo.ser.flow.End2EndFlow

/**
 * @author levinliu
 *         Created on 2020/6/18
 *         (Change file header on Settings -> Editor -> File and Code Templates)
 */
object ERFlowLauncher {
  val log = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {
    log.info("start spark-er flow now")
    End2EndFlow.run
    log.info("end spark-er flow now")
  }
}