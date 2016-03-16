/*#
  * @file commands.scala
  * @begin 8-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

package object commands {

  import reactivemongo.bson.{BSONString, BSONDocument}
  import reactivemongo.core.commands.{CommandError, BSONCommandResultMaker, Command}

  /**
    * Finds the distinct values for a specified field across a single collection
    * and returns the results in an array of strings.
    *
    * @constructor  Initializes a new instance of the [[Distinct]] class.
    *
    * @param collectionName The name of the target collection.
    * @param field  The field to return the distinct values for.
    * @param query  The query that specifies the documents to retrieve the
    *               distinct values from.
    */
  private[mongo] case class Distinct(
    collectionName: String,
    field: String,
    query: Option[BSONDocument] = None
  ) extends Command[Seq[String]] {

    /**
      * Makes the `BSONDocument` that is send as body of the command's query.
      */
    override def makeDocuments = BSONDocument(
      "distinct" -> BSONString(collectionName),
      "key" -> field,
      "query" -> query
    )

    val ResultMaker = Distinct
  }

  private[mongo] object Distinct extends BSONCommandResultMaker[Seq[String]] {

    import bsonFormatters._

    /**
      * Deserializes a list of strings from the specified BSON document.
      *
      * @param document  The BSON document to deserialize.
      * @return          A list of strings deserialized from `document`.
      */
    def apply(document: BSONDocument) = CommandError.checkOk(
      document,
      Some("distinct")
    ).toLeft(
      document.getAs[List[String]]("values").getOrElse(List.empty)
    )
  }
}
