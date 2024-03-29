package fos

import scala.util.parsing.input._
import scala.collection.mutable.{ Map, HashMap };
import scala.language.postfixOps

case class TypeError(pos: Position, msg: String) extends Exception(msg) {
    override def toString = msg + "\n" + pos.longString
}

object Type {

    import CT._

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
            case None => throw TypeError(expr.pos, "variable " + name + " is not defined.")
            case Some(typ) => typ
        }
        // T-FIELD
        case Select(obj, field) => {
            val classDef = CT.lookup(typeOf(obj, ctx)).getOrElse(throw new TypeError(expr.pos, "Class not found"))
            classDef findField field match {
                case None =>
                    throw TypeError(expr.pos, classDef.name + " does not contain field " + field)
                case Some(FieldDef(tpe, name)) => tpe
            }
        }
        // T-INVK
        case Apply(obj, method, args) => {
            val classDef = CT.lookup(typeOf(obj, ctx)).getOrElse(throw new TypeError(expr.pos, "Class not found"))
            val methodDef = classDef findMethod method getOrElse {
                throw TypeError(expr.pos, "method " + method + " is not defined in " + classDef.name)
            }
            methodDef checkTypeArguments (args map (typeOf(_, ctx)))
            methodDef.tpe
        }
        // T-NEW
        case New(cls, args) => {
            val classDef = CT.lookup(cls).getOrElse(throw new TypeError(expr.pos, "Class not found"))
            classDef checkTypeArguments (args map (typeOf(_, ctx)))
            cls
        }
        // T-[UDS]CAST
        case Cast(cls, expr) => {
            val classD = CT.lookup(typeOf(expr, ctx)).getOrElse(throw new TypeError(expr.pos, "Class not found"))
            val classC = CT.lookup(cls).getOrElse(throw new TypeError(expr.pos,
                "Class not found"))
            if (classD isSubClassOf classC) {
                /* T-UCAST */
                cls
            } else if (classC isSubClassOf classD) {
                /* T-DCAST */
                cls
            } else {
                /* T-SCAST */
                Console.err.println(s"Warning: You are trying to do a stupid cast from ${classD.name} to $cls")
                cls
            }
        }
    }
    // T-METHOD
    def typeOf(method: MethodDef, container: ClassDef): Unit = {
        val MethodDef(tpe, name, args, body) = method
        val newCtx = (args map { _.asTuple } toMap) + (("this", container.name))
        val bodyType = CT.lookup(typeOf(body, newCtx)).getOrElse(throw new TypeError(method.pos, "Class not found"))
        if (!(bodyType isSubClassOf tpe)) throw TypeError(method.pos,
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

    def apply(expr: Expr) = eval(expr)

    def eval(expr: Expr): Expr = {
        /* At this point we consider that expr has correctly type checked */
        val (steps, result) = Stream.iterate(expr)(reduce).span(e => !isValue(e))
        if (!steps.isEmpty) steps.tail.foreach(expr => println(s"REDUCE TO: $expr"))
        result.head
    }

    def reduce(expr: Expr): Expr = expr match {

        // R-FIELD
        /* No call by value here, as only one arg will be used only once */
        case Select(New(cls, args), field) =>
            args(getClassDef(cls) indexOfField field)

        // R-INVK
        /* to make method application more efficient 
         * Call by value on argument list
         * No call by value on object creation (no all fields necessary evaluated)
         */
        case Apply(cExpr @ New(cls, cArgs), method, Values(mArgs)) => {
            val MethodDef(_, _, args, body) =
                getClassDef(cls) findMethod method getOrElse {
                    throw new EvaluationException(s"Error 'method $method not found in class $cls ' was not thrown by typeOf")
                }
            substituteInBody(body, cExpr, args zip mArgs)
        }
        // R-CAST
        /* No call by value, cast simply removed */
        case Cast(cls, e @ New(nCls, args)) =>
            val D = getClassDef(cls)
            val C = getClassDef(nCls)
            if (C.isSubClassOf(D)) {
                e
            } else {
                throw new EvaluationException(s"ClassCastException, cannot cast $nCls into $cls")
            }

        // Congruence

        // RC-FIELD
        case Select(obj, field) => Select(reduce(obj), field)

        // RC-INVK-ARG
        /* evaluate method arguments in left to right order */
        case Apply(o @ New(cls, cArgs), method, mArgs) =>
            val (vals, arg :: rest) = splitVals(mArgs)
            Apply(o, method, vals ::: (reduce(arg) :: rest))

        // RC-INVK-RECV
        case Apply(obj, method, args) => Apply(reduce(obj), method, args)

        // RC-NEW-ARG
        case New(cls, cArgs) =>
            val (vals, arg :: rest) = splitVals(cArgs)
            New(cls, vals ::: (reduce(arg) :: rest))

        // RC-CAST
        case Cast(cls, e) => Cast(cls, reduce(e))
    }

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

    // added by Valerian
    def firstInheritanceLoop: Option[ClassDef] = ct.values find { classDef =>
        classDef isSuperclassOf classDef.superClass
    }

    // added by Valerian
    def checkInheritanceLoop: Unit = firstInheritanceLoop match {
        case None => ()
        case Some(classDef) =>
            throw new ClassHierarchyException(classDef.name + "is part of an inheritance loop")
    }
}

object Utils {

    def getClassDef(className: String): ClassDef = CT lookup className match {
        case None => throw new EvaluationException("class " + className + " not declared")
        case Some(c: ClassDef) => c
    }

    // added by Valerian
    def isValue(expr: Expr): Boolean = expr match {
        case Var(_) => true
        case New(_, args) => args forall { isValue(_) }
        case _ => false
    }

    def splitVals(exprs: List[Expr]): (List[Expr], List[Expr]) = exprs span isValue

    // TODO discuss correctness of isValue

    // added by Valerian
    object Value {
        def unapply(expr: Expr): Option[Expr] =
            if (isValue(expr)) Some(expr)
            else None
    }

    // added by Valerian
    object Values {
        def unapply(exprs: List[Expr]): Option[List[Expr]] =
            if (exprs forall isValue) Some(exprs)
            else None
    }
}
