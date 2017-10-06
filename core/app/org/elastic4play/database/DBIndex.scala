package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import com.sksamuel.elastic4s.ElasticDsl._
import org.elastic4play.models.Model
import org.elasticsearch.index.IndexNotFoundException
import play.api.Logger
//import shapeless.hlist.

import scala.concurrent.{ ExecutionContext, Future, blocking }

@Singleton
class DBIndex @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  private[DBIndex] lazy val logger = Logger(getClass)

  /**
   * Create a new index. Collect mapping for all attributes of all entities
   *
   * @param models list of all ModelAttributes to used in order to build index mapping
   * @return a future which is completed when index creation is finished
   */
  //  def initIndex(models: Iterable[Model]) = {
  //    val modelsMapping = models.map { m ⇒
  //      val attributeMappings = m.attributes
  //        .filterNot(_.name == "_id")
  //        .map(_.mapping)
  //      logger.info(s"Inserting mapping for ${m.name}: $attributeMappings")
  //      val modelMapping = mapping(m.name)
  //        .fields(attributeMappings)
  //        .dateDetection(false)
  //        .numericDetection(false)
  //      m.parents.headOption.fold(modelMapping)(p ⇒ modelMapping.parent(p.name))
  //    }
  //    db.execute {
  //      createIndex(db.indexName).mappings(modelsMapping)
  //    }
  //      .map { _ ⇒ () }
  //  }
  def initIndex(models: Iterable[Model]) = {
    val modelsMapping = models.flatMap { model ⇒
      model.databaseMapping
    }
      .map {
        case (entityName, entityMapping) ⇒
          val fieldDefinitions = entityMapping.fields
            .map {
              case (fieldName, fieldDefinition) ⇒ fieldDefinition.mapping(fieldName)
            }
          mapping(entityName)
            .fields(fieldDefinitions)
            .dateDetection(false)
            .numericDetection(false)
        //m.parents.headOption.fold(modelMapping)(p ⇒ modelMapping.parent(p.name))
      }
    db.execute {
      createIndex(db.indexName).mappings(modelsMapping)
    }
      .map { _ ⇒ () }
  }

  /**
   * Tests whether the index exists
   *
   * @return future of true if the index exists
   */
  def getIndexStatus: Future[Boolean] = {
    db.execute {
      index exists db.indexName
    } map { indicesExistsResponse ⇒
      indicesExistsResponse.isExists
    }
  }

  /**
   * Tests whether the index exists
   *
   * @return true if the index exists
   */
  def indexStatus: Boolean = blocking {
    getIndexStatus.await
  }

  /**
   * Get the number of document of this type
   *
   * @param modelName name of the document type from which the count must be done
   * @return document count
   */
  def getSize(modelName: String): Future[Long] = db.execute {
    search(db.indexName → modelName).size(0)
  } map { searchResponse ⇒
    searchResponse.totalHits
  } recover {
    case infe: IndexNotFoundException ⇒ throw infe
    case e ⇒
      logger.error(s"search(${db.indexName}, $modelName).size(0) returns error", e)
      0L
  }
}