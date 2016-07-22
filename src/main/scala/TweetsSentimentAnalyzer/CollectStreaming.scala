package TweetsSentimentAnalyzer

import java.io.File

import com.google.gson.Gson
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder
import java.util.Properties

import TweetsSentimentAnalyzer.Sentiment._
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations

import scala.collection.convert.wrapAll._

/**
  * Collect at least the specified number of tweets into json text files.
  */
object CollectStreaming{
  private var numTweetsCollected = 0L
  private var partNum = 0
  private var gson = new Gson()

  val props = new Properties()
  props.setProperty("annotators", "tokenize, ssplit, parse, sentiment")
  val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)

  def mainSentiment(input: String): Sentiment = Option(input) match {
    case Some(text) if !text.isEmpty => extractSentiment(text)
    case _ => throw new IllegalArgumentException("input can't be null or empty")
  }

  private def extractSentiment(text: String): Sentiment = {
    val (_, sentiment) = extractSentiments(text)
      .maxBy { case (sentence, _) => sentence.length }
    sentiment
  }

  def extractSentiments(text: String): List[(String, Sentiment)] = {
    val annotation: Annotation = pipeline.process(text)
    val sentences = annotation.get(classOf[CoreAnnotations.SentencesAnnotation])
    sentences
      .map(sentence => (sentence, sentence.get(classOf[SentimentCoreAnnotations.AnnotatedTree])))
      .map { case (sentence, tree) => (sentence.toString,Sentiment.toSentiment(RNNCoreAnnotations.getPredictedClass(tree))) }
      .toList
  }

  def main(args: Array[String]) {
    val outputDir = new File("./temp")
    if (outputDir.exists()) {
      System.err.println("ERROR - %s already exists: delete or specify another directory")
      System.exit(1)
    }
    outputDir.mkdirs()

    println("Initializing Streaming Spark Context...")
    val conf = new SparkConf().setAppName(this.getClass.getSimpleName)
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(2))

    val tweetStream = TwitterUtils.createStream(ssc, Some(new OAuthAuthorization(new ConfigurationBuilder().setOAuthAccessToken("2523499370-jKz9tm4RWh96HcNs1G6kN5wMsUeuT3eJXSGoiAV")
      .setOAuthAccessTokenSecret("Wy29SE0LZBL2xoHo3mAv17e4mSNYK18Hfh59dzDSUzW9i")
      .setOAuthConsumerKey("dvUkoBr8N3kePgtaNXgFqIW2E")
      .setOAuthConsumerSecret("6Yix2c6gn5oGbOcdDsIgLkLy4EoJvjdArl2wcVnJT2hkdJBeA0").build())))


    tweetStream.foreachRDD((rdd, time) => {
      val count = rdd.count()
      val textTagPair = rdd.map(status => (status.getText(), status.getHashtagEntities().mkString(" ")))
      val tweetsSentimentPair = textTagPair.map(record => (record._1, mainSentiment(record._1)))
      tweetsSentimentPair.foreach(pair => println(pair._1 + "~~~~~~~~" + pair._2))
      if (count > 0) {
//        val outputRDD = tweetsSentimentPair.repartition(5)
//        outputRDD.saveAsTextFile("./temp" + "/tweets_" + time.milliseconds.toString)
        numTweetsCollected += count
        if (numTweetsCollected > 10000) {
          System.exit(0)
        }
      }
    })
    ssc.start()
    ssc.awaitTermination()
  }
}