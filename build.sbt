name := "Twitter_Streaming"

version := "1.0"


scalaVersion := "2.10.4"

mainClass in (Compile,run) := Some("TweetsSentimentAnalyzer.CollectStreaming")

libraryDependencies ++= Seq(
  "io.druid" % "tranquility-core_2.10" % "0.8.2",
  "io.druid" % "tranquility-spark_2.10" % "0.8.2",
"org.apache.spark" %% "spark-core" % "1.6.2" % "provided",
  "org.apache.spark" % "spark-streaming_2.10" % "1.6.2" % "provided",
  "org.apache.spark" % "spark-streaming-twitter_2.10" % "1.6.1",
  "com.google.code.gson" % "gson" % "2.2.4",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4.1" classifier "models",
  "org.slf4j" % "slf4j-api" % "1.7.5"
)

assemblyMergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf")          => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$")      => MergeStrategy.discard
  case "log4j.properties"                                  => MergeStrategy.discard
  case m if m.toLowerCase.startsWith("meta-inf/services/") => MergeStrategy.filterDistinctLines
  case "reference.conf"                                    => MergeStrategy.concat
  case _                                                   => MergeStrategy.first
}

//assemblyShadeRules in assembly := Seq(
//  ShadeRule.rename("com.fasterxml.jackson.core.**" -> "shadeio.@1").inAll
//)



