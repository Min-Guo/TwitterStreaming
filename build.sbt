name := "Twitter_Streaming"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq("org.apache.spark" %% "spark-core" % "1.3.0" % "provided",
  "org.apache.spark" % "spark-streaming_2.10" % "1.6.2" % "provided",
  "org.apache.spark" % "spark-streaming-twitter_2.10" % "1.4.0",
  "com.google.code.gson" % "gson" % "2.2.4")

assemblyMergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf")          => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$")      => MergeStrategy.discard
  case "log4j.properties"                                  => MergeStrategy.discard
  case m if m.toLowerCase.startsWith("meta-inf/services/") => MergeStrategy.filterDistinctLines
  case "reference.conf"                                    => MergeStrategy.concat
  case _                                                   => MergeStrategy.first
}



