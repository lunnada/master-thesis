package knub.master_thesis.welda

import java.io.File
import java.util
import java.util.Date

import breeze.linalg.{DenseMatrix, DenseVector}
import jMEF._
import knub.master_thesis.Args
import org.apache.commons.exec.{CommandLine, DefaultExecutor}
import org.apache.commons.io.FileUtils
import weka.clusterers.EM
import weka.core.{Attribute, DenseInstance, Instance, Instances}
import breeze.stats.distributions.MultivariateGaussian
import knub.master_thesis.util.Sampler

import scala.collection.JavaConverters._
import scala.io.Source

class GaussianMixtureWELDA(p: Args) extends ReplacementWELDA(p) {

    override def transformVector(a: Array[Double]): Array[Double] = a

    override def getFolderName(): String = {
        s"${p.modelFileName}.$embeddingName.welda.gaussian-mixture." +
            s"pca-$PCA_DIMENSIONS." +
            s"des-$DISTRIBUTION_ESTIMATION_SAMPLES." +
            s"lambda-${LAMBDA.toString.replace('.', '-')}"
    }

    /**
      * WITH WEKA: Does not work, only one-dimensional
      */

//    override def estimateDistributionParameters(): Unit = {
//        val topTopicVectors = getTopTopicVectorsWithWords()
//        topTopicVectors.map { topVectors =>
//            val attributes = new util.ArrayList[Attribute](PCA_DIMENSIONS)
//            for (i <- 0 until PCA_DIMENSIONS)
//                attributes.add(new Attribute(s"v$i"))
//            val instances = new Instances("words", attributes, DISTRIBUTION_ESTIMATION_SAMPLES)
//            topVectors.foreach { case (word, a) =>
//                instances.add(new DenseInstance(1.0, a))
//            }
//            val gaussians = new EM
//            gaussians.setNumClusters(-1)
//            gaussians.setSeed(21011991)
//
//            gaussians.buildClusterer(instances)
//            println(gaussians.toString)
//            null
//        }
//    }
    /**
      * WITH scikit
      */
    var mixtureModels: Array[GaussianMixture] = _
    override def estimateDistributionParameters(): Unit = {
        val current = System.currentTimeMillis()

        val iterationFolder = new File(s"$folder/$currentIteration")
        iterationFolder.mkdir()

        val topTopicVectors = getTopTopicVectorsWithWords()
        mixtureModels = topTopicVectors.zipWithIndex.map { case (topVectors, idx) =>
            val topicFile = new File(s"${iterationFolder.getAbsolutePath}/$idx")
            val linesInFile = topVectors.map { case (w, v) =>
                s"$w\t${v.mkString("\t")}"
            }
            FileUtils.writeLines(topicFile, "UTF-8", linesInFile.asJava)

            val fileName = List("/home/stefan.bunk/anaconda2/envs/py27/bin/python","/opt/anaconda3/envs/py27/bin/python")
                .find(new File(_).exists()).get
            val cl = new CommandLine(fileName)
            cl.addArgument("gaussian_mixture.py")
            cl.addArgument(topicFile.getAbsolutePath)

            val exec = new DefaultExecutor
            exec.setExitValue(0)
            exec.execute(cl)

            val lines = Source.fromFile(s"${topicFile.getAbsolutePath}.output").getLines()

            def toDoubleArray(s: String): Array[Double] = {
                s.split('\t').map(_.toDouble)
            }

            val nrComponents = lines.next().toInt
            val covType = lines.next()
            val weights = toDoubleArray(lines.next())
            val means = (0 until nrComponents).map { _ =>
                toDoubleArray(lines.next())
            }.toArray
            val covs = (0 until nrComponents).map { _ =>
                (0 until PCA_DIMENSIONS).map { _ =>
                    toDoubleArray(lines.next())
                }.toArray.flatten
            }.toArray

            val gaussians = means.zip(covs).map { case (mean, cov) =>
                new MultivariateGaussian(new DenseVector(mean), new DenseMatrix[Double](PCA_DIMENSIONS, PCA_DIMENSIONS, cov))
            }

            val mixture = GaussianMixture(weights, gaussians)
//            Source.fromFile(s"${topicFile.getAbsolutePath}.output").getLines().foreach(println)
//            println(weights.deep)
//            println(gaussians.deep)
            mixture
        }
        println(s"\t\t${new Date}: Estimated distribution parameters in ${(System.currentTimeMillis() - current) / 1000} s")
    }

    override def sampleFromDistribution(topicId: Int): DenseVector[Double] = {
        val gaussianMixture = mixtureModels(topicId)
        val mixture = Sampler.nextDiscrete(gaussianMixture.mixture)
        gaussianMixture.gaussians(mixture).sample()
    }

    /**
      * WITH jMEF
      */
//    var mixtureModels: Array[MixtureModel] = _
//    override def estimateDistributionParameters(): Unit = {
//        println("Estimating distribution parameters")
//        val topTopicVectors = getTopTopicVectorsWithWords()
//        mixtureModels = topTopicVectors.map { topVectors =>
//            val points = topVectors.map { case (w, v) =>
//                val pVector = new PVector(v.length)
//                pVector.array = v
//                pVector
//            }.toArray
//            val clusters = KMeans.run(points, 2)
//            var gaussians = BregmanSoftClustering.initialize(clusters, new MultivariateGaussian)
//            gaussians = BregmanSoftClustering.run(points, gaussians)
////            points.foreach { point =>
////                println(gaussians.mixtureNumber(point))
////            }
//            gaussians.param
//            gaussians
//        }
//    }
//
//    override def sampleFromDistribution(topicId: Int): DenseVector[Double] = {
//        val a = mixtureModels(topicId).drawRandomPoints(1)(0).array
//        a.foreach { v =>
//            assert(!v.isNaN, s"sampled value is nan in ${a.deep.toString}")
//        }
//        new DenseVector(a)
//    }

}
