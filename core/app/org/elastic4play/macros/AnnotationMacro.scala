package org.elastic4play.macros

import scala.reflect.macros.whitebox

import scala.util.{ Try ⇒ UTry }

class AnnotationMacro(val c: whitebox.Context) extends MacroUtil {
  import c.universe._

  def transformIntoImpl(annottees: Tree*): Tree = {

    val target: Tree = c.prefix.tree match {
      case q"new $_[$typ]" ⇒ typ
      case _               ⇒ c.abort(c.enclosingPosition, "Transform annotation is malformed")
    }
    val targetType: Type = UTry(c.typecheck(q"0.asInstanceOf[$target]").tpe)
      .getOrElse(c.abort(c.enclosingPosition, s"Type of target class ($target) can't be identified. It must not be in the same scope of the annoted class."))

    annottees.toList match {
      case ClassDef(classMods, className, _, classTemplate) :: _ if classMods.hasFlag(Flag.CASE) ⇒
        val availableFields = classTemplate.body
          .collect {
          case ValDef(valMods, fieldName, _, _) if valMods.hasFlag(Flag.CASEACCESSOR) ⇒ fieldName
          case DefDef(_, fieldName, _, _, _, _) if fieldName.toString.startsWith("_") ⇒ fieldName
        }
          .reverse

        val params = targetType match {
          case CaseClassType(symbols @ _*) ⇒ symbols.map { s ⇒
            val sName = s.name.toString
            availableFields
              .find(_.toString.stripPrefix("_") == sName)
              .getOrElse(c.abort(c.enclosingPosition, s"Field $s is missing. Try to implement a function named _$s."))
          }
        }

        val transformMethodName = TermName("to" + targetType.typeSymbol.name.toString)
        val transformMethod = q"def $transformMethodName: $targetType = new $targetType(..$params)"

        ClassDef(classMods, className, Nil, Template(classTemplate.parents, classTemplate.self, classTemplate.body :+ transformMethod))
      case t :: _ ⇒ c.abort(c.enclosingPosition, s"The annotation @TransformInto[_] can be used only with case class:\n${t.getClass}")
    }
  }

  def modelImpl(annottees: Tree*): Tree = {
    annottees.toList match {
      case (modelClass @ ClassDef(classMods, className, Nil, _)) :: tail if classMods.hasFlag(Flag.CASE) ⇒
        val modelDef = q"val model = org.elastic4play.models.Model[$className]"
        val modelModule = tail match {
          case ModuleDef(moduleMods, moduleName, moduleTemplate) :: Nil ⇒
            ModuleDef(moduleMods, moduleName, Template(
              parents = moduleTemplate.parents,
              self    = moduleTemplate.self,
              body    = moduleTemplate.body :+ modelDef))
          case Nil ⇒
            val moduleName = className.toTermName
            q"object $moduleName { $modelDef }"
        }

        Block(modelClass :: modelModule :: Nil, Literal(Constant(())))
    }
  }
}