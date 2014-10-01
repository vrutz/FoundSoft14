import fos.Arithmetic._
import fos._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import org.scalatest.{ Matchers, FunSuite }

class TestQuickCheck extends Properties("Term") {

    def leafs: Gen[Term] = Gen.oneOf(True, False, Zero)

    def ifNodes(max: Int): Gen[Term] = for {
        b <- terms(max - 1)
        t1 <- terms(max - 1)
        t2 <- terms(max - 1)
    } yield If(b, t1, t2)

    def singleNodes(max: Int): Gen[Term] = for {
        t1 <- terms(max - 1)
        term <- Gen.oneOf(Succ(t1), Pred(t1), IsZero(t1))
    } yield term

    def terms(max: Int): Gen[Term] =
        if (max <= 1) leafs
        else Gen.oneOf(leafs, singleNodes(max), ifNodes(max))

    property(" eval === multi(reduce) ") = forAll(terms(12)) { (term: Term) =>

        val res = multiReduce(_ => {})(term)

        if (res.isValue) {
            eval(term) == res
        } else {
            try {
                eval(res)
                false
            } catch {
                case StuckTermException(stuck) => true
            }
        }
    }

    def intLeafs: Gen[Term] = Zero

    def boolLeafs: Gen[Term] = Gen.oneOf(True, False)

    def succPredNodes(max: Int): Gen[Term] = for {
        t1 <- intTerms(max - 1)
        term <- Gen.oneOf(Succ(t1), Pred(t1))
    } yield term

    def isZeroNodes(max: Int): Gen[Term] = for {
        t1 <- intTerms(max - 1)
    } yield IsZero(t1)

    def intIfNodes(max: Int): Gen[Term] = for {
        b <- boolTerms(max - 1)
        t1 <- intTerms(max - 1)
        t2 <- intTerms(max - 1)
    } yield If(b, t1, t2)

    def boolIfNodes(max: Int): Gen[Term] = for {
        b <- boolTerms(max - 1)
        t1 <- boolTerms(max - 1)
        t2 <- boolTerms(max - 1)
    } yield If(b, t1, t2)

    def boolTerms(max: Int): Gen[Term] =
        if (max <= 1) boolLeafs
        else Gen.oneOf(boolLeafs, isZeroNodes(max), boolIfNodes(max))

    def intTerms(max: Int): Gen[Term] =
        if (max <= 1) intLeafs
        else Gen.oneOf(intLeafs, succPredNodes(max), intIfNodes(max))

    def typedTerms(max: Int): Gen[Term] = Gen.oneOf(boolTerms(max), intTerms(max))

    property("typedTerms eval === multi(reduce) ") = forAll(typedTerms(12)) { (term: Term) =>

        val res1 = multiReduce(_ => {})(term)
        val res2 = eval(term)

        res1 == res2 && res1.isValue
    }
}
