//package org.elastic4play.macros
//
//import scala.language.experimental.macros
//import scala.reflect.macros.blackbox
//
//case class CC1(s: String)
//
//case class CC2(opt: Option[CC1], cc1: CC1)
//
//class M1(val c:blackbox.Context) {
//  import c.universe._
//  def from[E:WeakTypeTag]: Tree = {
//    val eType = weakTypeOf[E]
//    println(s"etype=$eType")
//    val typeSymbol = eType.typeSymbol
//    println(s"typeSymbol=$typeSymbol")
//    val eTypeSymbol = typeSymbol.asType.toType
//    println(s"eTypeSymbol=$eTypeSymbol")
//    val paramSymbol = typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.head.head
//    println(s"paramSymbol=$paramSymbol")
//    val paramSymbolType = paramSymbol.typeSignature
//    println(s"paramSymbolType=$paramSymbolType")
//    val paramSymbolTypeSymbol = paramSymbolType.typeSymbol
//    println(s"paramSymbolTypeSymbol=$paramSymbolTypeSymbol")
//    /**/
//    val TypeRef(_, _, List(subElementType1)) = paramSymbolType
//    println(s"subElementType=$subElementType1")
//    /**/
//    val TypeRef(_, _, List(subElementType2)) = paramSymbolTypeSymbol.asType.toType
//    println(s"subElementType=$subElementType2")
////    val paramParamSymbol = paramSymbolTypeSymbol.asClass.primaryConstructor.typeSignature.paramLists.head.head
////    println(s"paramParamSymbol=$paramParamSymbol")
////    val paramParamSymbolType = paramParamSymbol.typeSignature
////    println(s"paramParamSymbolType=$paramParamSymbolType")
//    q"()"
//  }
//}
//
//def m1[E]: Unit = macro M1.from[E]
//
//m1[CC2]
