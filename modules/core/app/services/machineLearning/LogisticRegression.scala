/*#
  * @file LogisticRegression.scala
  * @begin 2-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.machineLearning

import org.apache.mahout.classifier.evaluation.Auc
import org.apache.mahout.classifier.sgd.{L1, OnlineLogisticRegression}
import utils.common._

/**
  * Implements the logistic regression algorithm.
  *
  * @constructor  Initializes a new instance of the [[LogisticRegression]] class.
  * @param model  The trained model used to classify new observations.
  */
class LogisticRegression private(private val model: OnlineLogisticRegression) {

  import java.io.{DataOutputStream, ByteArrayOutputStream}

  /**
    * Classifies the specified [[Observation]].
    *
    * @param observation  The [[Observation]] to classify.
    * @return             A `Tuple2` containing the probability scores.
    */
  def classify(observation: Observation) = {
    val scores =  model.classifyFull(observation.vector)
    (scores.get(0), scores.get(1))
  }

  /**
    * Serializes this model to a byte array.
    * @return A `Try` value containing the byte array, or `Failure` in case of error.
    */
  def serialize = {
    val byteArrayOutputStream = new ByteArrayOutputStream
    cleanly(new DataOutputStream(byteArrayOutputStream))(_.close){ out =>
      model.write(out)
      out.flush
      byteArrayOutputStream.toByteArray
    }
  }
}

/**
  * Factory class for creating [[LogisticRegression]] instances.
  */
object LogisticRegression {

  import java.io.{DataInputStream, ByteArrayInputStream}

  private final val MaxTrainPasses = 30
  private final val AccuracyCheckInterval = 10

  /**
    * Trains a model with the specified data.
    *
    * @param trainData  The data to be used to train the model.
    * @return           A trained model.
    */
  def train(trainData: List[Observation]): LogisticRegression = {
    val model = new OnlineLogisticRegression(2, trainData(0).vector.size, new L1)

    // train the model
    for (pass <- 0 to MaxTrainPasses) {
      trainData.foreach { observation =>
        model.train(observation.actual, observation.vector)
      }

      // check accuracy of the trained model
      if (pass % AccuracyCheckInterval == 0) {
        val eval = new Auc(0.5)

        trainData.foreach { observation =>
          eval.add(observation.actual, model.classifyScalar(observation.vector))
        }
      }
    }

    new LogisticRegression(model)
  }

  /**
    * Initializes a new instance of the [[LogisticRegression]] class with
    * the specified model data.
    *
    * @param modelData  The model data.
    * @return           A trained model.
    */
  def apply(modelData: Array[Byte]): LogisticRegression = {
    val model = new OnlineLogisticRegression

    cleanly(new DataInputStream(new ByteArrayInputStream(modelData)))(_.close){ in =>
      model.readFields(in)
    }

    new LogisticRegression(model)
  }
}
