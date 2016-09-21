package knub.master_thesis.util

import java.io.{BufferedWriter, FileWriter}

import knub.master_thesis.welda.BaseWELDA

import scala.collection.mutable

class TopicModelWriter(private val model: BaseWELDA) {

    val params = model.p

    def baseName = model.fileBaseName

    def writeParameters() {
        val writer = new BufferedWriter(new FileWriter(baseName + ".welda.params"))
        writer.write("-model" + "\t" + "WELDA")
        writer.write("\n-topicmodel" + "\t" + params.modelFileName)
        writer.write("\n-ntopics" + "\t" + model.numTopics)
        writer.write("\n-alpha" + "\t" + model.alpha.deep)
        writer.write("\n-beta" + "\t" + model.beta)
        writer.write("\n-niters" + "\t" + params.numIterations)
        if (params.saveStep > 0) writer.write("\n-sstep" + "\t" + params.saveStep)
        writer.close()
    }

    def writeTopicAssignments(name: String) {
        val writer = new BufferedWriter(new FileWriter(baseName + s".$name.topic-assignments"))
        for (dIndex <- 0 until params.numDocuments) {
            val docSize = model.corpusWords(dIndex).size
            for (wIndex <- 0 until docSize) {
                writer.write(model.corpusTopics(dIndex).get(wIndex) + " ")
            }
            writer.write("\n")
        }
        writer.close()
    }

    def writeTopTopicalWords(name: String): Unit = {
        val writer = new BufferedWriter(new FileWriter(baseName + s".$name.topics"))
        for (tIndex <- 0 until model.numTopics) {
            writer.write(String.valueOf(tIndex))
            val topicWordProbs = mutable.Map[Int, Double]()
            for (wIndex <- 0 until model.vocabularySize) {
                // TODO: Think about what is better sorting?
//                val pro = (model.topicWordCountLDA(tIndex)(wIndex) + params.beta) /
//                        (model.sumTopicWordCountLDA(tIndex) + model.betaSum)
                val pro = model.topicWordCountLDA(tIndex)(wIndex)
                topicWordProbs(wIndex) = pro
            }
            val TOP_WORDS = 10
            val mostLikelyWords = topicWordProbs.toSeq.sortBy(-_._2).take(TOP_WORDS)
//            topicWordProbs = FuncUtils.sortByValueDescending(topicWordProbs)
//            val mostLikelyWords = topicWordProbs.keySet
            for (wordId <- mostLikelyWords) {
                writer.write(" " + model.id2WordVocabulary(wordId._1))
            }
            writer.write("\n")
        }
        writer.close()
    }

    def writeTopicWordPros(name: String) {
        val writer = new BufferedWriter(new FileWriter(baseName + s".$name.phi"))
        for (t <- 0 until model.numTopics) {
            for (w <- 0 until model.vocabularySize) {
                val pro = (model.topicWordCountLDA(t)(w) + params.beta) /
                        (model.sumTopicWordCountLDA(t) + model.betaSum)
                writer.write(pro + " ")
            }
            writer.write("\n")
        }
        writer.close()
    }

    def writeDocTopicProbs(name: String) {
        val writer = new BufferedWriter(new FileWriter(baseName + s".$name.document-topics"))
        for (i <- 0 until params.numDocuments) {
            for (j <- 0 until model.numTopics) {
                val pro = (model.docTopicCount(i)(j) + params.alpha) / (model.docWordCount(i) + model.alphaSum)
                if (j == 0)
                    writer.write(pro.toString)
                else
                    writer.write(" " + pro)
            }
            writer.write("\n")
        }
        writer.close()
    }

    def write(it: Int) {
        val name = f"iteration-$it%03d"
        writeTopTopicalWords(name)
        if (it == model.p.numIterations) {
            writeDocTopicProbs(name)
            writeTopicAssignments(name)
            writeTopicWordPros(name)
        }
    }
}

