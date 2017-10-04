package org.elastic4play.database

import com.sksamuel.elastic4s.ElasticDsl.fieldSort
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elastic4play.utils
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.transport.RemoteTransportException

object DBUtils {
  def sortDefinition(sortBy: Seq[String]): Seq[FieldSortDefinition] = {
    import org.elasticsearch.search.sort.SortOrder._
    val byFieldList: Seq[(String, FieldSortDefinition)] = sortBy
      .map {
        case f if f.startsWith("+") ⇒ f.drop(1) → fieldSort(f.drop(1)).order(ASC)
        case f if f.startsWith("-") ⇒ f.drop(1) → fieldSort(f.drop(1)).order(DESC)
        case f if f.length() > 0    ⇒ f → fieldSort(f)
      }
    // then remove duplicates
    // Same as : val fieldSortDefs = byFieldList.groupBy(_._1).map(_._2.head).values.toSeq
    utils.Collection
      .distinctBy(byFieldList)(_._1)
      .map(_._2) :+ fieldSort("_uid").order(DESC)
  }

  @scala.annotation.tailrec
  def isIndexMissing(t: Throwable): Boolean = t match {
    case t: RemoteTransportException ⇒ isIndexMissing(t.getCause)
    case _: IndexNotFoundException   ⇒ true
    case _                           ⇒ false
  }
}