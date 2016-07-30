package TweetsSentimentAnalyzer

import com.google.gson.Gson
import org.apache.spark.streaming.twitter.{TwitterInputDStream, TwitterUtils}
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
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.convert.wrapAll._


object CollectStreaming{
  private var numTweetsCollected = 0L
  private var partNum = 0
  private var gson = new Gson()

  val props = new Properties()
  props.setProperty("annotators", "tokenize, ssplit, parse, sentiment")
  val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)

  //cacluate tweets sentiment score using stanford nlp library to analysis sentence
  def mainSentiment(input: String): Sentiment = Option(input) match {
    case Some(text) if !text.isEmpty => extractSentiment(text)
    case _ => throw new IllegalArgumentException("input can't be null or empty")
  }

  def extractSentiment(text: String): Sentiment = {
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

  //convert tweet string to Map type (key: hashTag, value: sentiment score)
  def streamToMap(stream: String): Map[String, Any] = {
    println("StartMapping!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    val res = stream.split(",").map(_ split ":").map{ case Array(k, v) => (k, v) }.toMap
    res.foreach{case (key, value) => println(key + "--->" + value)}
    res
  }

  def main(args: Array[String]) {
//    val outputDir = new File("./temp")
//    if (outputDir.exists()) {
//      System.err.println("ERROR - %s already exists: delete or specify another directory")
//      System.exit(1)
//    }
//    outputDir.mkdirs()

    println("Initializing Streaming Spark Context...")
    val conf = new SparkConf().setAppName(this.getClass.getSimpleName)
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(2))

    //set twitter authority to access streaming data
    val tweetStream = TwitterUtils.createStream(ssc, Some(new OAuthAuthorization(new ConfigurationBuilder().setOAuthAccessToken("2523499370-jKz9tm4RWh96HcNs1G6kN5wMsUeuT3eJXSGoiAV")
      .setOAuthAccessTokenSecret("Wy29SE0LZBL2xoHo3mAv17e4mSNYK18Hfh59dzDSUzW9i")
      .setOAuthConsumerKey("dvUkoBr8N3kePgtaNXgFqIW2E")
      .setOAuthConsumerSecret("6Yix2c6gn5oGbOcdDsIgLkLy4EoJvjdArl2wcVnJT2hkdJBeA0").build())))

    //processing tweets streaming by spark job
    tweetStream.foreachRDD((rdd, time) => {
      val count = rdd.count()
      val hashTagRdd = rdd.filter(status => !status.getHashtagEntities().isEmpty)
      //filter tweets from US.
//      val filterRdd = hashTagRdd.filter(status => status.getPlace().getCountryCode().toLowerCase.equals("us"))
      val textTagPair = hashTagRdd.map(status => (status.getText(), status.getHashtagEntities()(0).getText()))
      val tweetsSentimentPair = textTagPair.map(record => (record._2, mainSentiment(record._1)))
//      val tweetString = hashTagRdd.map(status => "hashTag: " + status.getHashtagEntities()(0).getText() + ", " + "score: " + mainSentiment(status.getText()) + ", eventTimestamp: " + new DateTime(status.getCreatedAt))
      val tweetsString = hashTagRdd.map(status => Map[String, Any]("hashTag" -> status.getHashtagEntities()(0).getText, "score" -> mainSentiment(status.getText()), "timestamp" -> new DateTime(status.getCreatedAt).withZone(DateTimeZone.UTC)))
      import com.metamx.tranquility.spark.BeamRDD._
      //pass data to druid
      tweetsString.propagate(new MapBeamFactory)
//      val tweetMap = tweetString.map(tweet => streamToMap(tweet)).propagate(new MapBeamFactory)
       textTagPair.foreach(pair => println(pair._1 + "~~~~~~~~" + pair._2))
      if (count > 0) {
        val outputRDD = tweetsSentimentPair.repartition(5)
//        outputRDD.saveAsTextFile("./temp" + "/tweets_" + time.milliseconds.toString)
        numTweetsCollected += count
        if (numTweetsCollected > args(0).toInt) {
          System.exit(0)
        }
      }
    })
    ssc.start()
    ssc.awaitTermination()
    sc.stop()
  }
}