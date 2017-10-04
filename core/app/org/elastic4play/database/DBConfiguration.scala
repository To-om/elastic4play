package org.elastic4play.database

import javax.inject.{ Inject, Named, Singleton }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import com.sksamuel.elastic4s.ElasticDsl.{ ClearScrollDefinitionExecutable, CreateIndexDefinitionExecutable, DeleteByIdDefinitionExecutable, GetDefinitionExecutable, IndexDefinitionExecutable, IndexExistsDefinitionExecutable, ScrollExecutable, SearchDefinitionExecutable, UpdateDefinitionExecutable }
import com.sksamuel.elastic4s.admin.IndexExistsDefinition
import com.sksamuel.elastic4s.bulk.RichBulkItemResponse
import com.sksamuel.elastic4s.delete.DeleteByIdDefinition
import com.sksamuel.elastic4s.get.{ GetDefinition, RichGetResponse }
import com.sksamuel.elastic4s.index.RichIndexResponse
import com.sksamuel.elastic4s.indexes.{ CreateIndexDefinition, IndexDefinition }
import com.sksamuel.elastic4s.searches._
import com.sksamuel.elastic4s.streams.ReactiveElastic.ReactiveElastic
import com.sksamuel.elastic4s.streams.{ RequestBuilder, ResponseListener }
import com.sksamuel.elastic4s.update.{ RichUpdateResponse, UpdateDefinition }
import com.sksamuel.elastic4s.{ ElasticsearchClientUri, TcpClient }
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.common.settings.Settings
import play.api.inject.ApplicationLifecycle
import play.api.{ Configuration, Logger }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * This class is a wrapper of ElasticSearch client from Elastic4s
 * It builds the client using configuration (ElasticSearch addresses, cluster and index name)
 * It add timed annotation in order to measure storage metrics
 */
@Singleton
class DBConfiguration(
  searchHost: Seq[String],
  searchCluster: String,
  baseIndexName: String,
  lifecycle: ApplicationLifecycle,
  val version: Int,
  implicit val ec: ExecutionContext,
  implicit val actorSystem: ActorSystem) {

  @Inject() def this(
    configuration: Configuration,
    lifecycle: ApplicationLifecycle,
    @Named("databaseVersion") version: Int,
    ec: ExecutionContext,
    actorSystem: ActorSystem) = {
    this(
      configuration.get[Seq[String]]("search.host"),
      configuration.get[String]("search.cluster"),
      configuration.get[String]("search.index"),
      lifecycle,
      version,
      ec,
      actorSystem)
  }

  lazy val logger = Logger(getClass)

  /**
   * Underlying ElasticSearch client
   */
  private val client = TcpClient.transport(
    Settings.builder().put("cluster.name", searchCluster).build(),
    ElasticsearchClientUri(searchHost.map(h ⇒ s"elasticsearch://$h").mkString(",")))
  // when application close, close also ElasticSearch connection
  lifecycle.addStopHook { () ⇒ Future { client.close() } }

  def execute(indexDefinition: IndexDefinition): Future[RichIndexResponse] = client.execute(indexDefinition)
  def execute(searchDefinition: SearchDefinition): Future[RichSearchResponse] = client.execute(searchDefinition)
  def execute(createIndexDefinition: CreateIndexDefinition): Future[CreateIndexResponse] = client.execute(createIndexDefinition)
  def execute(updateDefinition: UpdateDefinition): Future[RichUpdateResponse] = client.execute(updateDefinition)
  def execute(searchScrollDefinition: SearchScrollDefinition): Future[RichSearchResponse] = client.execute(searchScrollDefinition)
  def execute(indexExistsDefinition: IndexExistsDefinition): Future[IndicesExistsResponse] = client.execute(indexExistsDefinition)
  def execute(deleteByIdDefinition: DeleteByIdDefinition): Future[DeleteResponse] = client.execute(deleteByIdDefinition)
  def execute(getDefinition: GetDefinition): Future[RichGetResponse] = client.execute(getDefinition)
  def execute(clearScrollDefinition: ClearScrollDefinition): Future[ClearScrollResult] = client.execute(clearScrollDefinition)

  /**
   * Creates a Source (akka stream) from the result of the search
   */
  def source(searchDefinition: SearchDefinition): Source[RichSearchHit, NotUsed] = Source.fromPublisher(client.publisher(searchDefinition))

  private lazy val sinkListener = new ResponseListener {
    override def onAck(resp: RichBulkItemResponse): Unit = ()
    override def onFailure(resp: RichBulkItemResponse): Unit = {
      logger.warn(s"Document index failure ${resp.id}: ${resp.failureMessage}")
    }
  }

  /**
   * Create a Sink (akka stream) that create entity in ElasticSearch
   */
  def sink[T](implicit builder: RequestBuilder[T]): Sink[T, Future[Unit]] = {
    val end = Promise[Unit]
    def complete() = {
      if (!end.isCompleted)
        end.success(())
      ()
    }
    def failure(t: Throwable) = {
      end.failure(t)
      ()
    }
    Sink.fromSubscriber(client.subscriber[T](failureWait  = 1.second, maxAttempts = 10, listener = sinkListener, completionFn = complete _, errorFn = failure _))
      .mapMaterializedValue { _ ⇒ end.future }
  }

  /**
   * Name of the index, suffixed by the current version
   */
  val indexName: String = baseIndexName + "_" + version

  /**
   * return a new instance of DBConfiguration that points to the previous version of the index schema
   */
  def previousVersion: DBConfiguration = new DBConfiguration(searchHost, searchCluster, baseIndexName, lifecycle, version - 1, ec, actorSystem)
}