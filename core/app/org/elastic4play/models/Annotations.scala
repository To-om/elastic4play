package org.elastic4play.models

import scala.annotation.{ StaticAnnotation, compileTimeOnly }
import scala.language.experimental.macros

import org.elastic4play.macros.AnnotationMacro

@compileTimeOnly("enable macro paradise to expand macro annotations")
class TransformInto[T] extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro AnnotationMacro.transformIntoImpl
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class TransformFrom[T] extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro AnnotationMacro.transformFromImpl
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class EntityModel extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro AnnotationMacro.modelImpl
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class JsonOutput extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro AnnotationMacro.outputImpl
}
