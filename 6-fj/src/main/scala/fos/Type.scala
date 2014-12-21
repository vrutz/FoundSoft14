package fos

import scala.collection.mutable.{ Map, HashMap };
import scala.language.postfixOps

case class TypeError(msg: String) extends Exception(msg)

object Type {

    import CT._
    import Utils._

    type Class = String
    type Context = scala.collection.immutable.Map[String, Class]

    /*
    * Original signature of typeOf
    * Replaced by multiple overloaded version to implement easily different
    * behaviours.
    * 
    def typeOf(tree: Tree, ctx: Context): Class = ???
        //   ... To complete ...
    */

    // added by Valerian
    def typeOf(expr: Expr, ctx: Context): String = expr match {
        // the cases are treated in order given by reference paper

        // T-VAR
        case Var(name) => ctx.get(name) match {
            case None => throw TypeError("variable " + name + " is not defined.")
            case Some(typ) => typ
        }
        // T-FIELD
        case Select(obj, field) => {
            val classDef = getClassDef(typeOf(obj, ctx))
            classDef findField field match {
                case None =>
                    throw TypeError(classDef.name + " does not contain field " + field)
                case Some(FieldDef(tpe, name)) => tpe
            }
        }
        // T-INVK
        case Apply(obj, method, args) => {
            val classDef = getClassDef(typeOf(obj, ctx))
            val methodDef = classDef findMethod method getOrElse {
                throw TypeError("method " + method + " is not defined in " + classDef.name)
            }
            methodDef checkTypeArguments (args map (typeOf(_, ctx)))
            methodDef.tpe
        }
        // T-New
        case New(cls, args) => {
            val classDef = getClassDef(cls)
            classDef checkTypeArguments (args map (typeOf(_, ctx)))
            cls
        }
        // T-[UDS]CAST
        case Cast(cls, expr) => {
            val classD = getClassDef(typeOf(expr, ctx))
            val classC = getClassDef(cls)
            if (classD isSubClassOf classC)
                /* T-UCAST */ cls
            else if (classC isSubClassOf classD)
                /* T-DCAST */ cls // type error ?
            else
                /* T-SCAST */ cls // type error ?
        }
    }
    // T-METHOD
    def typeOf(method: MethodDef, container: ClassDef): Unit = {
        val MethodDef(tpe, name, args, body) = method
        val newCtx = (args map { _.asTuple } toMap) + (("this", container.name))
        val bodyType = getClassDef(typeOf(body, newCtx))
        if (!(bodyType isSubClassOf tpe)) throw TypeError(
            "Type mismatch: found: " + bodyType.name + " expected: " + tpe)
        container.overrideMethod(tpe, name, args, body)
    }

    // T-CLASS
    def typeOf(classDef: ClassDef): Unit = {
        val ClassDef(name, superclass, fields, ctor, methods) = classDef
        classDef.checkFields
        classDef.verifyConstructorArgs
        for (m <- classDef.methods) typeOf(m, classDef)
    }
}

case class EvaluationException(msg: String) extends Exception

object Evaluate extends (Expr => Expr) {

    import Utils._

    def apply(expr: Expr): Expr = ???
    //   ... To complete ... 

    def substituteInBody(exp: Expr, thiss: New, substs: List[(FieldDef, Expr)]): Expr = exp match {
        case Select(obj: Expr, field: String) => Select(substituteInBody(obj, thiss, substs), field)
        case New(cls, args) => New(cls, args map (arg => substituteInBody(arg, thiss, substs)))
        case Cast(cls, e) => Cast(cls, substituteInBody(e, thiss, substs))
        case Var("this") => thiss
        case Var(bd) => substs find (subs => subs._1.name == bd) match {
            case None => exp
            case Some((_, sub)) => sub
        }

        case Apply(obj, method, args) => Apply(substituteInBody(obj, thiss, substs), method, args map (arg => substituteInBody(arg, thiss, substs)))
        case _ => throw new EvaluationException("Apply: Forgot expression " + exp)
    }
}

object CT {

    val objectClass: String = "Object"
    private val objectClassDef = ClassDef(objectClass, null, Nil, CtrDef(objectClass, Nil, Nil, Nil), Nil)

    private var ct: Map[String, ClassDef] = new HashMap[String, ClassDef]

    add(objectClass, objectClassDef)

    def elements = ct iterator

    def lookup(classname: String): Option[ClassDef] = if (classname != null) ct get classname else None

    def add(key: String, element: ClassDef): Unit = ct += key -> element

    def delete(key: String) = ct -= key

    def clear(): Unit = {
        ct clear;
        add(objectClass, objectClassDef)
    }

    /*-------------------------------------------------------*/
    // Code added by Valerian

    def firstInheritanceLoop: Option[ClassDef] = ct.values find { classDef =>
        classDef isSuperclassOf classDef.superClass
    }

    def checkInheritanceLoop: Unit = firstInheritanceLoop match {
        case None => ()
        case Some(classDef) =>
            throw new ClassHierarchyException(classDef.name + "is part of an inheritance loop")
    }

    // end of code added by Valerian
    /*--------------------------------------------------------*/

}

object Utils {

    def getClassDef(className: String): ClassDef = CT lookup className match {
        case None => throw new TypeError("class " + className + " not declared")
        case Some(c: ClassDef) => c
    }
}
