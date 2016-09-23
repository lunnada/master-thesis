package knub.master_thesis.welda

import java.io.File
import java.util

import be.tarsos.lsh.{CommandLineInterface, LSH, Vector}
import breeze.linalg.{DenseMatrix, DenseVector}
import de.uni_potsdam.hpi.coheel.util.Timer
import knub.master_thesis.Args
import knub.master_thesis.util.Sampler
import org.apache.commons.lang3.text.WordUtils
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer

import scala.collection.mutable
import scala.collection.JavaConverters._

abstract class ReplacementWELDA(p: Args) extends BaseWELDA(p) {
    println("Removing LSH cache")
    new File(".").listFiles().filter(_.getName.contains("be.tarsos")).foreach { f => println(f); f.delete() }

    // dimensions to sample embeddings down to, so we can estimate the parameters of the distribution from a few samples only
    val PCA_DIMENSIONS = 10
    // how many samples to estimate the distribution
    val DISTRIBUTION_ESTIMATION_SAMPLES = 20
    override val TOPIC_OUTPUT_EVERY = 1

    val LAMBDA = p.lambda
    val DISTANCE_FUNCTION = p.weldaDistanceFunction

    println(s"LAMBDA is set to ${LAMBDA.toString}")
    println(s"Distance Function is set to $DISTANCE_FUNCTION")
    assert(LAMBDA >= 0.0, "lambda must be at least zero")
    assert(LAMBDA <= 1.0, "lambda must be at most one")

    var pcaVectors: mutable.Map[String, Array[Double]] = _
    var nrReplacedWordsSuccessful = 0L
    var nrReplacedWordsTries = 0L

    var lsh: LSH = _

    var folder: String = _

    override def init(): Unit = {
        super.init()
        val word2Vec = WordVectorSerializer.loadTxtVectors(new File(s"${p.modelFileName.replaceAll("/model$", "/")}$embeddingName.restricted.vocab.embedding.txt"))
        println(s"Loaded vectors have ${word2Vec.getWordVector("house").length} dimensions")
        pcaVectors = mutable.Map()

        val embeddingsList = new mutable.ArrayBuffer[Array[Double]](word2IdVocabulary.size)
        val vocabulary: Array[String] = word2IdVocabulary.keys.toArray
        vocabulary.foreach { word =>
            val actualWord =
                if (word2Vec.hasWord(word))
                    word
                else if (word2Vec.hasWord(WordUtils.capitalize(word)))
                    WordUtils.capitalize(word)
                else if (word2Vec.hasWord(word.toUpperCase))
                    word.toUpperCase()
                else
                    throw new RuntimeException(word)
            val embeddingDimensions = word2Vec.getWordVector(actualWord)
            if (embeddingDimensions == null) {
                throw new RuntimeException(word)
            }
            embeddingsList += embeddingDimensions
        }
        val rows = embeddingsList.toArray
        val M = DenseMatrix(rows: _*)
        println(s"Dimensions of embedding matrix = ${M.rows}, ${M.cols}")

        val pcaM = pca(M, PCA_DIMENSIONS)

        val lshVectors = for (i <- vocabulary.indices) yield {
            val vec = transformVector(pcaM(i, ::).t.toArray)
            val word = vocabulary(i)
            pcaVectors(word) = vec
            new Vector(word, vec)
        }

        println(s"Words in vocabulary: ${word2Vec.vocab().numWords()}, words in LSH: ${lshVectors.size} (should be equal)")

        val hashFamily = CommandLineInterface.getHashFamily(0.0, DISTANCE_FUNCTION, PCA_DIMENSIONS)
        lsh = new LSH(new util.ArrayList(lshVectors.asJava), hashFamily)
        DISTANCE_FUNCTION match {
            case "cos" =>
                lsh.buildIndex(32, 4)
            case "l2" =>
                lsh.buildIndex(4, 4)
        }


        /*
         * Give an intuiton about sampling
         */
        val topWords = getTopTopicWords()
        estimateDistributionParameters()

        val NR_SAMPLES = 3
        for (j <- 0 until numTopics) {
            println(s"Topic: ${topWords(j)}")
            for (i <- 0 until NR_SAMPLES) {
                val v = sampleFromDistribution(j)
                val nearestNeighbours = lsh.query(new Vector("", v.data), 1)
                val nearestWordLsh = if (nearestNeighbours.isEmpty) "<NULL>" else nearestNeighbours.get(0).getKey
                println(s"Sample: $nearestWordLsh")
            }
        }
    }

    def transformVector(a: Array[Double]): Array[Double]

    override def sampleSingleIteration(): Unit = {
        estimateDistributionParameters()
        for (docIdx <- 0 until p.numDocuments) {
            val docSize = corpusWords(docIdx).size
            for (wIndex <- 0 until docSize) {
                // determine topic first
                val topicId = corpusTopics(docIdx).get(wIndex)
                // now determine the word we "observe"
                val wordId = if (Sampler.nextCoinFlip(LAMBDA)) {
                    // sample a vector from the multivariate gaussian
                    // use vector form here to get nearest word to a sampled vector
                    val vectorSample = sampleFromDistribution(topicId)
                    //                    Timer.start("FINDING WORD2VEC")
                    //                    val sample = new NDArray(Array(vectorSample.data))
                    //                    val nearestWordWord2Vec = word2Vec.wordsNearest(sample, 1).iterator().next()
                    //                    Timer.end("FINDING WORD2VEC")
                    //                    Timer.start("FINDING LSH")
                    val nearestNeighbours = lsh.query(new Vector("", vectorSample.data), 1)
                    val nearestWordLsh = if (nearestNeighbours.isEmpty) "<NULL>" else nearestNeighbours.get(0).getKey
                    //                    Timer.end("FINDING LSH")
                    //                    println(s"$nearestWordLsh -- $nearestWordWord2Vec")
                    nrReplacedWordsTries += 1
                    word2IdVocabulary.get(nearestWordLsh) match {
                        case Some(id) =>
                            nrReplacedWordsSuccessful += 1
                            id
                        case None =>
                            corpusWords(docIdx).get(wIndex)
                    }
                } else {
                    corpusWords(docIdx).get(wIndex)
                }

                docTopicCount(docIdx)(topicId) -= 1
                topicWordCountLDA(topicId)(wordId) -= 1
                sumTopicWordCountLDA(topicId) -= 1

                for (tIndex <- 0 until numTopics) {
                    multiPros(tIndex) =
                        (docTopicCount(docIdx)(tIndex) + alpha(tIndex)) * (topicWordCountLDA(tIndex)(wordId) + beta) /
                            (sumTopicWordCountLDA(tIndex) + betaSum)
                }
                val newTopicId = Sampler.nextDiscrete(multiPros)

                docTopicCount(docIdx)(newTopicId) += 1
                topicWordCountLDA(newTopicId)(wordId) += 1
                sumTopicWordCountLDA(newTopicId) += 1

                // update topic
                corpusTopics(docIdx).set(wIndex, newTopicId)
            }
            //            println(s"How often does replacing work: ${nrReplacedWordsSuccessful.toDouble / nrReplacedWordsTries}")
            Timer.printAll()
        }

    }

    def pca(m: DenseMatrix[Double], numDimensions: Int): DenseMatrix[Double] = {
        val rawPCA = breeze.linalg.princomp(m).scores
        val pcaResult = rawPCA(::, 0 until numDimensions)
        pcaResult
    }

    //noinspection AccessorLikeMethodIsEmptyParen
    def getTopTopicWords(): Array[Seq[String]] = {
        val result = new Array[Seq[String]](numTopics)
        for (tIndex <- 0 until numTopics) {
            val topicWordProbs = mutable.Map[Int, Double]()
            for (wIndex <- 0 until vocabularySize) {
                val pro = topicWordCountLDA(tIndex)(wIndex)
                topicWordProbs(wIndex) = pro
            }
            val mostLikelyWords = topicWordProbs.toSeq.sortBy(-_._2).take(DISTRIBUTION_ESTIMATION_SAMPLES)
            result(tIndex) = for (wordId <- mostLikelyWords) yield {
                id2WordVocabulary(wordId._1)
            }
        }
        result
    }

    def getTopTopicVectors(): Array[Seq[Array[Double]]] = {
        val topTopicWords = getTopTopicWords()
        val topTopicVectors = topTopicWords.map { topWords =>
            topWords.flatMap { topWord =>
                if (pcaVectors.contains(topWord))
                    Some(pcaVectors(topWord))
                else
                    None
            }
        }
        topTopicVectors
    }

    def estimateDistributionParameters(): Unit
    def sampleFromDistribution(topicId: Int): DenseVector[Double]

}