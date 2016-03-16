/*#
  * @file FundraisingObservation.scala
  * @begin 2-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.machineLearning

import org.apache.mahout.math.DenseVector
import org.apache.mahout.vectorizer.encoders.{ConstantValueEncoder, StaticWordValueEncoder}

/**
  * Factory class for creating [[Observation]] instances initialized
  * with fundraising information.
  */
object FundraisingObservation {

  /**
    * Initializes a new instance of the [[Observation]] class with the
    * specified fundraising information.
    *
    * @param range    The range in which the target amount falls.
    * @param duration The duration of the fundraising period, in days.
    * @param target   The target amount.
    * @param raised   The raised amount.
    * @param category `1` if the raised amount is enough to get funded; otherwise, `0`.
    * @param scaling  The scaling to be applied to `target` and `raised`.
    * @return         A new instance of the [[Observation]] class.
    */
  def apply(
    range: String,
    duration: Double,
    target: Double,
    raised: Double,
    category: Int,
    scaling: Int
  ): Observation = {
    val vector = new DenseVector(5)
    val interceptEncoder = new ConstantValueEncoder("intercept")
    val featureEncoder = new ConstantValueEncoder("feature")

    interceptEncoder.addToVector("1", vector)
    vector.set(0, duration)
    vector.set(1, target / scaling)
    vector.set(2, raised / scaling)

    featureEncoder.addToVector(range, vector)
    new Observation(vector, category)
  }
}
