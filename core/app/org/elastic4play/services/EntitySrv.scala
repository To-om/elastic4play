package org.elastic4play.services

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.Source
import org.elastic4play.{ InternalError, NotFoundError, UnknownAttributeError }
import org.elastic4play.database._
import org.elastic4play.models.{ DatabaseWrites, Entity, FNull, FPath, Model, UpdateOps }
import org.elasticsearch.action.search.SearchType
import play.api.libs.json.JsObject

import scala.concurrent.{ ExecutionContext, Future }
import org.scalactic.Accumulation._
import org.scalactic.{ Bad, Good, One, Or }

@Singleton
class EntitySrvFactory @Inject() (
  dbGet: DBGet,
  dbFind: DBFind,
  dbCreate: DBCreate,
  dbModify: DBModify,
  dbRemove: DBRemove,
  dbSequence: DBSequence,
  dbIndex: DBIndex,
  eventSrv: EventSrv,
  attachmentSrv: AttachmentSrv,
  ec: ExecutionContext) {

  //  def withParent[E, ACDA <: HList, AUO <: HList](model: Model.Aux[E, ACDA, AUO]) =
  //    new EntitySrv[E, ACDA, AUO](model, dbGet, dbFind, dbCreate, dbModify, dbRemove, dbSequence, dbIndex, eventSrv, attachmentSrv, ec) with CreatorWithParent[E, ACDA, AUO]
  //
  //  def withoutParent[E, ACDA <: HList, AUO <: HList](model: Model.Aux[E, ACDA, AUO]) =
  //    new EntitySrv[E, ACDA, AUO](model, dbGet, dbFind, dbCreate, dbModify, dbRemove, dbSequence, dbIndex, eventSrv, attachmentSrv, ec) with CreatorWithoutParent[E, ACDA, AUO]
  //  def withParent[E](model: Model.Base[E]) =
  //    new EntitySrv[E](model, dbGet, dbFind, dbCreate, dbModify, dbRemove, dbSequence, dbIndex, eventSrv, attachmentSrv, ec) with CreatorWithParent[E]

  def withoutParent[E](model: Model.Base[E]) =
    new EntitySrv[E](model, dbGet, dbFind, dbCreate, dbModify, dbRemove, dbSequence, dbIndex, eventSrv, attachmentSrv, ec) with CreatorWithoutParent[E]

}

trait CreatorWithoutParent[E] {
  _: EntitySrv[E] ⇒

  //  @deprecated("Use create(e: E) after transforming the record in E", "2.0.0")
  //  def create[L <: HList](record: Record[L])(implicit authContext: AuthContext): Future[E with Entity] = macro ServiceMacros.create
  //
  //  @deprecated("Use create(e: E) after transforming the record in E", "2.0.0")
  //  def createOps[L <: HList](record: Record[L])(operations: RecordOps[L] ⇒ FieldOps[_]*)(implicit authContext: AuthContext): Future[E with Entity] = macro ServiceMacros.createWithOps[L]

  def create(e: E)(implicit authContext: AuthContext): Future[E with Entity] = {
    for {
      eWithSavedAttachments ← saveAttachments(e)
      json ← model.databaseWrites(eWithSavedAttachments).fold(Future.failed, {
        case Some(o: JsObject) ⇒ Future.successful(o)
        case v                 ⇒ Future.failed(InternalError(s"Entity JSON has invalid type;\nfound: ${v.getClass}\nrequired: JsObject"))
      })
      id = (json \ "_id").asOpt[String]
      o ← dbCreate(model.name, id, None, None, json)
      entity ← model.databaseReads(Some(o)).fold(Future.failed, Future.successful)
      _ = eventSrv.publish(
        AuditOperation.create(entity, json)(authContext))
    } yield entity
  }
}

trait CreatorWithParent[E] {
  _: EntitySrv[E] ⇒
  def create(parent: Entity, e: E)(implicit authContext: AuthContext): Future[E with Entity] = {
    for {
      eWithSavedAttachments ← saveAttachments(e)
      json ← model.databaseWrites(eWithSavedAttachments).fold(Future.failed, {
        case Some(o: JsObject) ⇒ Future.successful(o)
        case v                 ⇒ Future.failed(InternalError(s"Entity JSON has invalid type;\nfound: ${v.getClass}\nrequired: JsObject"))
      })
      id = (json \ "_id").asOpt[String]
      o ← dbCreate(model.name, id, Some(parent._id), Some(parent._routing), json)
      entity ← model.databaseReads(Some(o)).fold(Future.failed, Future.successful)
      _ = eventSrv.publish(
        AuditOperation.create(entity, json)(authContext))
    } yield entity
  }
  //  def create[L <: HList](parent: Entity, record: Record[L])(implicit authContext: AuthContext): Future[E with Entity] = macro ServiceMacros.createWithParent
  //
  //  def createOps[L <: HList](parent: Entity, record: Record[L])(operations: RecordOps[L] ⇒ FieldOps[_]*)(implicit authContext: AuthContext): Future[E with Entity] = macro ServiceMacros.createWithParentAndOps[L]
}

class EntitySrv[E](
  val model: Model.Base[E],
  dbGet: DBGet,
  dbFind: DBFind,
  val dbCreate: DBCreate,
  val dbModify: DBModify,
  dbRemove: DBRemove,
  dbSequence: DBSequence,
  dbIndex: DBIndex,
  val eventSrv: EventSrv,
  val attachmentSrv: AttachmentSrv,
  implicit val ec: ExecutionContext) {

  def saveAttachments(entity: E): Future[E] = model.saveAttachment(attachmentSrv, entity)

  def nextSequence(name: String): Future[Int] = dbSequence.next(name)

  def get(id: String): Future[E with Entity] = {
    dbGet(model.name, id).map(attrs ⇒ model.databaseReads(Some(attrs)).get) // FIXME
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): Source[E with Entity, Future[Long]] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    dbFind(range, sortBy)(indexName ⇒ search(indexName → model.name).query(queryDef.query))
      .map(attrs ⇒ model.databaseReads(Some(attrs)).get) // FIXME
  }

  def stats(queryDef: QueryDef, aggs: Agg*): Future[JsObject] = {
    import com.sksamuel.elastic4s.ElasticDsl._
    dbFind(indexName ⇒ search(indexName → model.name)
      .query(queryDef.query)
      .aggregations(aggs.flatMap(_.apply(model)))
      .searchType(SearchType.QUERY_THEN_FETCH)
      .size(0))
      .map { searchResponse ⇒
        aggs
          .map(_.processResult(model, searchResponse.aggregations))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject(Nil))
      }
  }

  def delete(id: String)(implicit authContext: AuthContext): Future[Unit] = {
    get(id).flatMap(delete)
  }

  def delete(entity: E with Entity)(implicit authContext: AuthContext): Future[Unit] = {
    dbRemove(entity._model.name, entity._id, entity._routing).map {
      case true  ⇒ eventSrv.publish(AuditOperation.delete(entity))
      case false ⇒ throw NotFoundError(s"entity $entity not found")
    }
  }

  def update(entity: E with Entity, updates: Map[FPath, UpdateOps.Type], isDelete: Boolean)(implicit authContext: AuthContext): Future[E with Entity] = { //macro ServiceMacros.update
    for {
      u ← model.saveUpdateAttachment(attachmentSrv, updates)
      ud = u.validatedBy {
        case (path, UpdateOps.SetAttribute(value)) ⇒
          model.databaseMaps
            .get(path)
            .map {
              case databaseWrites: DatabaseWrites[t] ⇒
                Or.from(databaseWrites(value.asInstanceOf[t]))
                  .map(value ⇒ path → UpdateOps.SetDBAttribute(value))
                  .badMap(_ ⇒ One(UnknownAttributeError(path.toString, FNull)))
              case _ ⇒ ???
            }
            .getOrElse(Bad(One(UnknownAttributeError(path.toString, FNull))))
        case (path, UpdateOps.UnsetAttribute) ⇒ Good(path → UpdateOps.UnsetAttribute)
      }
        .get // FIXME
        .toMap
      //ud = model.updatesToDatabase(u)
      o ← dbModify(model.name, entity._id, entity._routing, entity._parent, ud)
      newEntity ← model.databaseReads(Some(o)).fold(Future.failed, Future.successful)
      ao = if (isDelete) AuditOperation.delete(newEntity)(authContext)
      else AuditOperation.update(newEntity, ud)(authContext)
      _ = eventSrv.publish(ao)
    } yield newEntity
  }

  def getSize: Future[Long] = dbIndex.getSize(model.name)
}