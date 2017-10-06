package org.elastic4play.macros

import java.util.Date

import org.elastic4play.models.FPath
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class UpdateMapTest extends Specification with TestUtils with Mockito {

  "Update map" should {

    "contain all updatable fields" in {
      case class SubClass1(name: String, option: Option[Int])
      case class ComplexClass(name: String, value: Int, subClasses: Seq[SubClass1])
      val complexClassMap = databaseMaps[ComplexClass]

      complexClassMap.keys must contain(exactly(
        FPath("name"),
        FPath("value"),
        FPath("subClasses"),
        FPath("subClasses[]"),
        FPath("subClasses[].name"),
        FPath("subClasses[].option")))
    }

    "contain all updatable fields 2" in {
      case class ClassAAA(s: String, i: Int)
      case class ClassAAB(l: Long, d: Date)
      case class ClassAA(aaa: Seq[ClassAAA], aab: Option[ClassAAB], s: String)
      case class ClassABA(i: Int)
      case class ClassAB(aba: ClassABA, l: Long)
      case class ClassA(aa: ClassAA, ab: Seq[ClassAB])

      val map = databaseMaps[ClassA]
      map.keys.map(_.toString) must contain(exactly(
        "aa",
        "aa.aaa",
        "aa.aaa[]",
        "aa.aaa[].s",
        "aa.aaa[].i",
        "aa.aab",
        "aa.aab.l",
        "aa.aab.d",
        "aa.s",
        "ab",
        "ab[]",
        "ab[].aba",
        "ab[].aba.i",
        "ab[].l"))
    }
  }
}
