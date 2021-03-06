package firepile.tests

import firepile._
import firepile.Device
import firepile.Spaces._
import firepile.util.BufferBackedArray._

object TestTimeMap1b {
  val floatX2 = (a:Float) => a * 2.0f

  def main(args: Array[String]) = {
    val dataSize = if (args.length > 0) args(0).toInt else 1000
    val n = if (args.length > 1) args(1).toInt else 128

    println("size = " + dataSize)

    val a = Array.tabulate(dataSize)(_.toFloat)

    {
      println(n + " array sequential map x2");
      time ( {
        for (i <- 0 until n) {
          val result = a.map(floatX2)
          result
        }
      }, "TestTimeMap1b")
    }
  }
}
