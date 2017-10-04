package org.elastic4play

import org.scalactic.{ Every, Or }

package object models {
  implicit class ErrorNameSetter[A, B](t: (A Or Every[AttributeError], B)) {
    def setNameInErrors(name: String): (A Or Every[AttributeError], B) = {
      val e = t._1.badMap { everyErrors ⇒
        everyErrors.map(error ⇒ error.withName(name))
      }
      (e, t._2)
    }

    def setModelInErrors(name: String): (A Or Every[AttributeError], B) = {
      val e = t._1.badMap { everyErrors ⇒
        everyErrors.map(error ⇒ error.withModel(name))
      }
      (e, t._2)
    }
  }
}