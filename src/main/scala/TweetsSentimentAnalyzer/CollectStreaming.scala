package TweetsSentimentAnalyzer

import org.apache.spark.streaming.twitter.{TwitterInputDStream, TwitterUtils}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder
import java.util.Properties
import scala.collection.mutable.ArrayBuffer
import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations
import org.joda.time.{DateTime, DateTimeZone}
import twitter4j.{FilterQuery, HashtagEntity, Status}

import scala.collection.convert.wrapAll._


object CollectStreaming{

  // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and Sentiment.
  val props = new Properties()
  props.setProperty("annotators", "tokenize, ssplit, parse, sentiment")
  //build pipeline.
  val pipeline: StanfordCoreNLP = new StanfordCoreNLP(props)

  //cacluate tweets sentiment score using stanford nlp library to analysis sentence
  def mainSentiment(input: String): Int = Option(input) match {
    case Some(text) if !text.isEmpty => extractSentiment(text)
    case _ => throw new IllegalArgumentException("input can't be null or empty")
  }

  def extractSentiment(text: String): Int = {
    val (_, sentiment) = extractSentiments(text)
      .maxBy { case (sentence, _) => sentence.length }
    sentiment
  }

  def extractSentiments(text: String): List[(String, Int)] = {
    val annotation: Annotation = pipeline.process(text)
    val sentences = annotation.get(classOf[CoreAnnotations.SentencesAnnotation])
    sentences
      .map(sentence => (sentence, sentence.get(classOf[SentimentCoreAnnotations.AnnotatedTree])))
      .map { case (sentence, tree) => (sentence.toString, changeScoreRange(RNNCoreAnnotations.getPredictedClass(tree)))}
      .toList
  }

  //Change sentiment score range. Negative score represents negative tweet, positive score represents positive tweet.
  def changeScoreRange(origin: Int): Int = origin match {
    case 0 => -2
    case 1 => -1
    case 2 => 0
    case 3 => 1
    case 4 => 2
  }

  //If tweet has multiple hashTags, map each hashTag with the same tweet information, and return as list of Map.
  def multiTags(status: Status): ArrayBuffer[Map[String, Any]] = {
    val length:Int = status.getHashtagEntities.length
    val mapList = ArrayBuffer[Map[String, Any]]()
    val index = 0
    for (index <- 0 to (length - 1)) {
      println(status.getHashtagEntities()(index).getText() + "~~~~" + status.getText)
      mapList += Map[String, Any]("hashTag" -> status.getHashtagEntities()(index).getText(), "score" -> mainSentiment(status.getText()), "timestamp" -> new DateTime(status.getCreatedAt).withZone(DateTimeZone.UTC))
//      ,
//      "latitude" -> status.getGeoLocation().getLatitude(), "longitude" -> status.getGeoLocation().getLongitude(), "country" -> status.getPlace().getCountryCode())
    }
    mapList
  }

  def main(args: Array[String]) {
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
      //filter tweets with language, only keep English tweets.
      val enTweet = rdd.filter(_.getLang == "en")
      //Keep the tweets has hashTags
      val tagTweets = enTweet.filter(status => !status.getHashtagEntities().isEmpty)
      tagTweets.foreach(status => println(status.getText))
      //FlatMap hashTags
      val flatTags = tagTweets.flatMap(status =>  multiTags(status))
      //pass data to Tranquility
      import com.metamx.tranquility.spark.BeamRDD._
      flatTags.propagate(new MapBeamFactory)
    })
    ssc.start()
    ssc.awaitTermination()
    sc.stop()
  }
}