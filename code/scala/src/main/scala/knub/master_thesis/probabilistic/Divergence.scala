package knub.master_thesis.probabilistic

import cc.mallet.util.Maths
import knub.master_thesis.SimFunction

object Divergence {

    val simMax = SimFunction("max", Divergence.maxDistance)
    val simSum = SimFunction("sum", Divergence.sumDistance)
    val simBhattacharyya = SimFunction("bhattacharyya", Divergence.bhattacharyyaDistance)
    val simHelling = SimFunction("hellinger", Divergence.hellingerDistance)
    val simJensenShannon = SimFunction("jensen-shannon", Divergence.jensenShannonDivergence)

    /**
      * The smaller, the more similar. Zero means exact
      * [0, 1]
      */
    def jensenShannonDivergence(p: Array[Double], q: Array[Double]): Double = {
        Maths.jensenShannonDivergence(p, q)
    }

    /**
      * The smaller, the more similar. Zero means exact
      * [0, 1]
      */
    def maxDistance(p: Array[Double], q: Array[Double]): Double = {
        var max = 0.0
        var i = 0
        while (i < p.length) {
            val diff = Math.abs(p(i) - q(i))
            if (diff > max)
                max = diff
            i += 1
        }
        max
    }

    /**
      * The smaller, the more similar. Zero means exact
      * [0, 1]
      */
    def sumDistance(p: Array[Double], q: Array[Double]): Double = {
        var sum = 0.0
        var i = 0
        while (i < p.length) {
            sum += Math.abs(p(i) - q(i))
            i += 1
        }
        0.5 * sum
    }

    /**
      * The smaller, the more similar. Zero means exact
      */
    def bhattacharyyaDistance(p: Array[Double], q: Array[Double]): Double = {
        var sum = 0.0
        var i = 0
        while (i < p.length) {
            sum += Math.sqrt(p(i) * q(i))
            i += 1
        }
        // avoiding the log, so we stay in [0, 1]
        // 1 -, so we get a divergence value
        1 - sum
        //        - Math.log(sum)
    }

    val sqrt2Rez = 1.0 / Math.sqrt(2)

    /**
      * The smaller, the more similar. Zero means exact
      * [0, 1]
      */
    def hellingerDistance(p: Array[Double], q: Array[Double]): Double = {
        var sum = 0.0
        var i = 0
        while (i < p.length) {
            val sqrtDiff = Math.sqrt(p(i)) - Math.sqrt(q(i))
            sum += sqrtDiff * sqrtDiff
            i += 1
        }
        sqrt2Rez * Math.sqrt(sum)
    }

}
