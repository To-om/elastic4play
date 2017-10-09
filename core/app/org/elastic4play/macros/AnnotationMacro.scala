package org.elastic4play.macros

import scala.reflect.macros.whitebox

import scala.util.{ Try ⇒ UTry }

class AnnotationMacro(val c: whitebox.Context) extends MacroUtil {
  import c.universe._

  def transformIntoImpl(annottees: Tree*): Tree = {

    val toClass: Tree = c.prefix.tree match {
      case q"new $_[$typ]" ⇒ typ.asInstanceOf[Tree]
      case _               ⇒ c.abort(c.enclosingPosition, "Transform annotation is malformed")
    }
    val toClassType: Type = UTry(c.typecheck(q"0.asInstanceOf[$toClass]").tpe)
      .getOrElse(c.abort(c.enclosingPosition, s"Type of target class ($toClass) can't be identified. It must not be in the same scope of the annoted class."))

    annottees.toList match {
      case ClassDef(classMods, className, _, classTemplate) :: _ if classMods.hasFlag(Flag.CASE) ⇒
        val availableFields = classTemplate.body
          .collect {
            case ValDef(valMods, fieldName, _, _) if valMods.hasFlag(Flag.CASEACCESSOR) ⇒ fieldName
            case DefDef(_, fieldName, _, _, _, _) if fieldName.toString.startsWith("_") ⇒ fieldName
          }
          .reverse

        val params = toClassType match {
          case CaseClassType(symbols @ _*) ⇒ symbols.map { s ⇒
            val sName = s.name.toString
            availableFields
              .find(_.toString.stripPrefix("_") == sName)
              .getOrElse(c.abort(c.enclosingPosition, s"Field $sName is missing. Try to implement a function named _$sName."))
          }
        }

        val transformMethodName = TermName("to" + toClassType.typeSymbol.name.toString)
        val transformMethod = q"def $transformMethodName: $toClassType = new $toClassType(..$params)"

        ClassDef(classMods, className, Nil, Template(classTemplate.parents, classTemplate.self, classTemplate.body :+ transformMethod))
      case t :: _ ⇒ c.abort(c.enclosingPosition, s"The annotation @TransformInto[_] can be used only with case class:\n${t.getClass}")
    }
  }

  def transformFromImpl(annottees: Tree*): Tree = {
    val fromClass: Tree = c.prefix.tree match {
      case q"new $_[$typ]" ⇒ typ.asInstanceOf[Tree]
      case _               ⇒ c.abort(c.enclosingPosition, "Transform annotation is malformed")
    }
    val fromClassType: Type = UTry(c.typecheck(q"0.asInstanceOf[$fromClass]").tpe)
      .getOrElse(c.abort(c.enclosingPosition, s"Type of target class ($fromClass) can't be identified. It must not be in the same scope of the annoted class."))

    val fromClassName = fromClassType.typeSymbol.name.toTermName
    val fromClassInstance = c.freshName(fromClassName)

    annottees.toList match {
      case (toClassDef: ClassDef) :: tail if toClassDef.mods.hasFlag(Flag.CASE) ⇒
        val toClassName = toClassDef.name

        val overrideFields = tail.headOption.fold[Map[String, Tree]](Map.empty) {
          case module: ModuleDef ⇒
            module.impl.body
              .collect {
                case DefDef(_, fieldName, _, paramss, _, _) if fieldName.toString.startsWith("_") ⇒
                  paramss.collect {
                    case ValDef(_, _, _, _) :: Nil ⇒ fieldName.toString.tail -> q"${module.name}.$fieldName($fromClassInstance)"
                  }
              }
              .flatten
              .toMap
        }

        val module = tail.headOption.getOrElse(q"object ${toClassName.toTermName} {}").asInstanceOf[ModuleDef]

        val availableFields = fromClassType match {
          case CaseClassType(symbols @ _*) ⇒
            symbols
              .map(symbol ⇒ symbol.name.toString -> q"$fromClassInstance.${symbol.name.toTermName}")
              .toMap ++ overrideFields
        }

        val params = toClassDef.impl.body
          .collect {
            case ValDef(valMods, fieldName, _, _) if valMods.hasFlag(Flag.CASEACCESSOR) ⇒
              val field = fieldName.toString
              availableFields.getOrElse(field, c.abort(c.enclosingPosition, s"Field $field is missing. Try to implement a function named _$field(x: $fromClass)."))
          }

        val transformMethodName = TermName("from" + fromClassType.typeSymbol.name.toString)
        val transformMethod = q"def $transformMethodName($fromClassInstance: $fromClassType): $toClassName = new $toClassName(..$params)"

        val moduleTemplate = module.impl
        val modelModule = ModuleDef(module.mods, module.name, Template(moduleTemplate.parents, moduleTemplate.self, moduleTemplate.body :+ transformMethod))
        Block(toClassDef :: modelModule :: Nil, Literal(Constant(())))
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