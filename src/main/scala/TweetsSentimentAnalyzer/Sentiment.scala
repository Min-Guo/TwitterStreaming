package TweetsSentimentAnalyzer

object Sentiment extends Enumeration {
  type Sentiment = Value
  val VERYPOSITIVE, POSITIVE, VERYNEGATIVE, NEGATIVE, NEUTRAL = Value

  def toSentiment(sentiment: Int): Sentiment = sentiment match {
    case 0  => Sentiment.VERYNEGATIVE
    case 1  => Sentiment.NEGATIVE
    case 2 => Sentiment.NEUTRAL
    case 3 => Sentiment.POSITIVE
    case 4 => Sentiment.VERYPOSITIVE
  }
}