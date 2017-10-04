package org.elastic4play.services

import com.typesafe.config.ConfigValueType._
import com.typesafe.config.{ ConfigList, ConfigObject, ConfigValue }
import org.elastic4play.services.QueryDSL._
import play.api.libs.json._
import play.api.{ Configuration, Logger }

import scala.collection.JavaConverters._

object JsonFormat {
  lazy val log = Logger(getClass)

  //implicit def roleFormat = enumFormat(Role)

  implicit val configWrites = OWrites { (cfg: Configuration) ⇒
    JsObject(cfg.subKeys.map(key ⇒ key → configValueWrites.writes(cfg.underlying.getValue(key))).toSeq)
  }

  implicit val configValueWrites: Writes[ConfigValue] = Writes((value: ConfigValue) ⇒ value match {
    case v: ConfigObject             ⇒ configWrites.writes(Configuration(v.toConfig))
    case v: ConfigList               ⇒ JsArray(v.asScala.map(x ⇒ configValueWrites.writes(x)))
    case v if v.valueType == NUMBER  ⇒ JsNumber(BigDecimal(v.unwrapped.asInstanceOf[java.lang.Number].toString))
    case v if v.valueType == BOOLEAN ⇒ JsBoolean(v.unwrapped.asInstanceOf[Boolean])
    case v if v.valueType == NULL    ⇒ JsNull
    case v if v.valueType == STRING  ⇒ JsString(v.unwrapped.asInstanceOf[String])
  })

  //def jsonGet[A](json: JsValue, name:  String)(implicit reads: Reads[A]) = (json \ name).as[A]

  object JsObj {
    def unapply(v: JsValue): Option[Seq[(String, JsValue)]] = v match {
      case JsObject(f) ⇒ Some(f.toSeq)
      case _           ⇒ None
    }
  }

  object JsObjOne {
    def unapply(v: JsValue): Option[(String, JsValue)] = v match {
      case JsObject(f) if f.size == 1 ⇒ f.toSeq.headOption
      case _                          ⇒ None
    }
  }

  object JsVal {
    def unapply(v: JsValue): Option[Any] = v match {
      case JsString(s)  ⇒ Some(s)
      case JsBoolean(b) ⇒ Some(b)
      case JsNumber(i)  ⇒ Some(i)
      case _            ⇒ None
    }
  }

  object JsRange {
    def unapply(v: JsValue): Option[(String, Any, Any)] =
      for {
        field ← (v \ "_field").asOpt[String]
        jsFrom ← (v \ "_from").asOpt[JsValue]
        from ← JsVal.unapply(jsFrom)
        jsTo ← (v \ "_to").asOpt[JsValue]
        to ← JsVal.unapply(jsTo)
      } yield (field, from, to)
  }

  object JsParent {
    def unapply(v: JsValue): Option[(String, QueryDef)] =
      for {
        t ← (v \ "_type").asOpt[String]
        q ← (v \ "_query").asOpt[QueryDef]
      } yield (t, q)
  }

  object JsField {
    def unapply(v: JsValue): Option[(String, Any)] =
      for {
        f ← (v \ "_field").asOpt[String]
        maybeValue ← (v \ "_value").asOpt[JsValue]
        value ← JsVal.unapply(maybeValue)
      } yield (f, value)
  }

  object JsFieldIn {
    def unapply(v: JsValue): Option[(String, Seq[String])] =
      for {
        f ← (v \ "_field").asOpt[String]
        values ← (v \ "_values").asOpt[Seq[String]]
      } yield f → values
  }

  implicit val queryReads: Reads[QueryDef] = {
    Reads { (json: JsValue) ⇒
      json match {
        case JsObjOne(("_and", JsArray(v)))            ⇒ JsSuccess(and(v.map(_.as[QueryDef]): _*))
        case JsObjOne(("_or", JsArray(v)))             ⇒ JsSuccess(or(v.map(_.as[QueryDef]): _*))
        case JsObjOne(("_contains", JsString(v)))      ⇒ JsSuccess(contains(v))
        case JsObjOne(("_not", v: JsObject))           ⇒ JsSuccess(not(v.as[QueryDef]))
        case JsObjOne(("_any", _))                     ⇒ JsSuccess(any)
        case j: JsObject if j.fields.isEmpty           ⇒ JsSuccess(any)
        case JsObjOne(("_gt", JsObjOne(n, JsVal(v))))  ⇒ JsSuccess(n ~> v)
        case JsObjOne(("_gte", JsObjOne(n, JsVal(v)))) ⇒ JsSuccess(n ~>= v)
        case JsObjOne(("_lt", JsObjOne(n, JsVal(v))))  ⇒ JsSuccess(n ~< v)
        case JsObjOne(("_lte", JsObjOne(n, JsVal(v)))) ⇒ JsSuccess(n ~<= v)
        case JsObjOne(("_between", JsRange(n, f, t)))  ⇒ JsSuccess(n ~<> (f → t))
        case JsObjOne(("_parent", JsParent(p, q)))     ⇒ JsSuccess(parent(p, q))
        case JsObjOne(("_id", JsString(id)))           ⇒ JsSuccess(withId(id))
        case JsField(field, value)                     ⇒ JsSuccess(field ~= value)
        case JsObjOne(("_child", JsParent(p, q)))      ⇒ JsSuccess(child(p, q))
        case JsObjOne(("_string", JsString(s)))        ⇒ JsSuccess(string(s))
        case JsObjOne(("_in", JsFieldIn(f, v)))        ⇒ JsSuccess(f in (v: _*))
        case JsObjOne(("_type", JsString(v)))          ⇒ JsSuccess(ofType(v))
        case JsObjOne((n, JsVal(v))) ⇒
          if (n.startsWith("_")) log.warn(s"""Potentially invalid search query : {"$n": "$v"}"""); JsSuccess(n ~= v)
        case other ⇒ JsError(s"Invalid query: unexpected $other")
      }
    }
  }

  implicit val aggReads: Reads[Agg] = Reads { (json: JsValue) ⇒
    (json \ "_agg").as[String] match {
      case "avg"   ⇒ JsSuccess(selectAvg((json \ "_field").as[String]))
      case "min"   ⇒ JsSuccess(selectMin((json \ "_field").as[String]))
      case "max"   ⇒ JsSuccess(selectMax((json \ "_field").as[String]))
      case "sum"   ⇒ JsSuccess(selectSum((json \ "_field").as[String]))
      case "count" ⇒ JsSuccess(selectCount)
      case "time" ⇒
        val fields = (json \ "_fields").as[Seq[String]]
        val interval = (json \ "_interval").as[String]
        val selectables = (json \ "_select").as[Seq[Agg]]
        JsSuccess(groupByTime(fields, interval, selectables: _*))
      case "field" ⇒
        val field = (json \ "_field").as[String]
        val size = (json \ "_size").asOpt[Int].getOrElse(10)
        val order = (json \ "_order").asOpt[Seq[String]].getOrElse(Nil)
        val selectables = (json \ "_select").as[Seq[Agg]]
        JsSuccess(groupByField(field, size, order, selectables: _*))
      case "category" ⇒
        val categories = (json \ "_categories").as[Map[String, QueryDef]]
        val selectables = (json \ "_select").as[Seq[Agg]]
        JsSuccess(groupByCaterogy(categories, selectables: _*))
    }
  }

  implicit val authContextWrites = Writes[AuthContext]((authContext: AuthContext) ⇒ Json.obj(
    "id" → authContext.userId,
    "name" → authContext.userName,
    "roles" → authContext.roles))

  //  implicit val auditableActionFormat = enumFormat(AuditableAction)
  //
  //  implicit val AuditOperationWrites = Json.writes[AuditOperation]
}