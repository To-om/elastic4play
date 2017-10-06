package org.elastic4play.services

import java.io.InputStream
import java.nio.file.Files
import javax.inject.{ Inject, Singleton }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source, StreamConverters }
import akka.util.ByteString
import org.elastic4play.database.{ DBCreate, DBGet }
import org.elastic4play.models.{ FAttachment, FFile }
import org.elastic4play.utils.Hasher
import play.api.Configuration
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AttachmentSrv(
    mainHash: String,
    extraHashes: Seq[String],
    chunkSize: Int,
    dbCreate: DBCreate,
    dbGet: DBGet,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  //val model = Model(classOf[AttachmentChunk])

  @Inject() def this(
    configuration: Configuration,
    dbCreate: DBCreate,
    dbGet: DBGet,
    ec: ExecutionContext,
    mat: Materializer) =
    this(
      configuration.get[String]("datastore.hash.main"),
      configuration.get[Seq[String]]("datastore.hash.extra"),
      configuration.underlying.getBytes("datastore.chunksize").toInt,
      dbCreate,
      dbGet,
      ec,
      mat)

  val mainHasher = Hasher(mainHash)
  val extraHashers = Hasher(mainHash +: extraHashes: _*)

  def save(f: FFile): Future[FAttachment] = {
    for {
      hash ← mainHasher.fromPath(f.filepath).map(_.head.toString())
      hashes ← extraHashers.fromPath(f.filepath)
      aiv = FAttachment(f.filename, hashes, Files.size(f.filepath), f.contentType, hash)
      attachment ← dbGet("attachment", hash + "_0")
        .map { _ ⇒ aiv }
        .fallbackTo {
          // it it doesn't exist, create it
          FileIO.fromPath(f.filepath, chunkSize)
            .zip(Source.fromIterator { () ⇒ Iterator.iterate(0)(_ + 1) })
            .mapAsync(5) {
              case (buffer, index) ⇒
                val data = java.util.Base64.getEncoder.encodeToString(buffer.toArray)
                dbCreate("attachment", Some(s"${hash}_$index"), None, None, Json.obj("binary" → data))
            }
            .runWith(Sink.ignore)
            .map { _ ⇒ aiv }
        }
    } yield attachment
  }

  def source(id: String): Source[ByteString, NotUsed] = {
    Source.unfoldAsync(0) { chunkNumber ⇒
      dbGet("attachment", s"${id}_$chunkNumber")
        .map { entity ⇒ Some((chunkNumber + 1, ByteString((entity \ "binary").as[String]))) }
        .recover { case _ ⇒ None }
    }
  }

  def stream(id: String): InputStream = source(id).runWith(StreamConverters.asInputStream(1.minute))

}
