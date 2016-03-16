/*#
  * @file Observation.scala
  * @begin 2-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.machineLearning

import org.apache.mahout.math.Vector

/**
  * Represents an observation for logistic regression.
  *
  * @constructor  Initializes a new instance of the [[Observation]] class.
  * @param vector The vector containing the details of the observation.
  * @param actual The actual category according to the input data.
  */
class Observation(val vector: Vector, val actual: Int) {}
