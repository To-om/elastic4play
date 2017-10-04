//package org.elastic4play.macros
//
//import org.elastic4play.models.{ FPath, Person }
//import org.specs2.mock.Mockito
//import org.specs2.mutable.Specification
//
//class UpdateMapTest extends Specification with TestUtils with Mockito {
//  "" should {
//    val personUpdatesMap = databaseMaps[Person]
//    "" in {
//      personUpdatesMap.keys must contain(exactly(
//        FPath("name"),
//        FPath("age"),
//        FPath("hairColor"),
//        FPath("isAdmin"),
//        FPath("birthDate"),
//        FPath("password"),
//        FPath("avatar"),
//        FPath("certificate"),
//        FPath("hobbies")))
//    }
//  }
//}
