package knub.master_thesis

import java.io.{File, PrintWriter}

import cc.mallet.topics.ParallelTopicModel
import cc.mallet.types.{FeatureSequence, Instance, InstanceList}
import scala.collection.JavaConversions._

class TopicModelResult(val model: ParallelTopicModel) {
    def save(fileName: String): Unit = {
        model.write(new File(fileName))
    }

    // The data alphabet maps word IDs to strings
    val dataAlphabet = model.alphabet

    /**
      * Show the words and topics in the given instance
      */
    def showInstance(instanceIdx: Int): Unit = {
        val tokens = model.getData.get(instanceIdx).instance.getData.asInstanceOf[FeatureSequence]
        val topics = model.getData.get(instanceIdx).topicSequence

        for (i <- 0 until tokens.getLength) {
            print(s"${dataAlphabet.lookupObject(tokens.getIndexAtPosition(i))}-${topics.getIndexAtPosition(i)} ")
        }
        println()
    }

    def getWordTopics: Array[Array[Double]] = {
        val result = Array.ofDim[Double](model.numTypes, model.numTopics)
        for (wordType <- 0 until model.numTypes) {
            val topicCounts = model.typeTopicCounts(wordType)
            var index = 0
            while (index < topicCounts.length && topicCounts(index) > 0) {
                val topic = topicCounts(index) & model.topicMask
                val count = topicCounts(index) >> model.topicBits
                result(wordType)(topic) += count
                index += 1
            }
        }
        for (wordType <- 0 until model.numTypes) {
            for (topic <- 0 until model.numTopics) {
                result(wordType)(topic) += model.beta
            }
        }
        val topicNormalizers = Array.ofDim[Double](model.numTopics)
        for (topic <- 0 until model.numTopics) {
            topicNormalizers(topic) = 1.0 / (model.tokensPerTopic(topic) + model.numTypes * model.beta)
        }
        for (topic <- 0 until model.numTopics; wordType <- 0 until model.numTypes) {
            result(wordType)(topic) *= topicNormalizers(topic)
        }
        result
    }

    def showTopWordsPerTopics(): Unit = {
//        val stdout = new PrintWriter(System.out)
//        model.printDocumentTopics(stdout)
//        model.printTopicDocuments(stdout)
        model.printTopWords(System.out, 10, false)
    }

    def displayTopWords(numWords: Int = 10): String = {
        val out = new StringBuilder()
        val topicSortedWords = model.getSortedWords
        for (topic <- 0 until model.numTopics) {
            val sortedWords = topicSortedWords.get(topic)
            var word = 0
            val iterator = sortedWords.iterator()

            out.append(dataAlphabet.lookupObject(iterator.next().getID))
            word += 1
            while (iterator.hasNext && word < numWords) {
                val info = iterator.next()
                out.append(" " + dataAlphabet.lookupObject(info.getID))
                word += 1
            }
            out.append("\n")
        }
        out.toString
    }

    def findBestTopicsForWord(word: String, nrTopics: Int = 3): Array[Int] = {
        // The format for typeTopicCounts array is
        //  the topic in the rightmost bits
        //  the count in the remaining (left) bits.
        // Since the count is in the high bits, sorting (desc)
        //  by the numeric value of the int guarantees that
        //  higher counts will be before the lower counts.
        val idx = dataAlphabet.lookupIndex(word)
        model.typeTopicCounts(idx).take(nrTopics).filter(_ != 0).map(_ & model.topicMask)

        // OLD IMPLEMENTATION -- SLOW
//        val wordId = dataAlphabet.lookupIndex(word)
//        val topicSortedWords = model.getSortedWords
//        topicSortedWords.zipWithIndex.maxBy { case (topic, _) =>
//            topic.iterator()
//                .find { idSorter => idSorter.getID == wordId }
//                .map(_.getWeight)
//                .getOrElse(0.0)
//        }._2
    }

    def estimateTopicDistribution(): Unit = {
        // Estimate the topic distribution of the first instance,
        //  given the current Gibbs state.
        val topicDistribution = model.getTopicProbabilities(0)
        // Get an array of sorted sets of word ID/count pairs
        val topicSortedWords = model.getSortedWords

        // Show top 5 words in topics with proportions for the first document
        for (topic <- 0 until model.numTopics) {
            val iterator = topicSortedWords.get(topic).iterator()

            println(f"$topic\t${topicDistribution(topic)}%.3f\t")
            var rank = 0
            while (iterator.hasNext && rank < 5) {
                val idCountPair = iterator.next()
                println(s"${dataAlphabet.lookupObject(idCountPair.getID)} (${idCountPair.getWeight}%.0f) ")
                rank += 1
            }
        }

        // Create a new instance with high probability of topic 0
        val topicZeroText = new java.lang.StringBuilder()
        val iterator = topicSortedWords.get(0).iterator()

        var rank = 0
        while (iterator.hasNext && rank < 5) {
            val idCountPair = iterator.next()
            topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID) + " ")
            rank += 1
        }

//        // Create a new instance named "test instance" with empty target and source fields.
//        val testing = new InstanceList(PreprocessingPipe.pipe)
//        testing.addThruPipe(new Instance(topicZeroText.toString, null, "test instance", null))
//
//        val inferencer = model.getInferencer
//        val testProbabilities = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5)
//        System.out.println("0\t" + testProbabilities(0))
    }

}
