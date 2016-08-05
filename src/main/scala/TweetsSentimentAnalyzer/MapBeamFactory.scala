package TweetsSentimentAnalyzer

import com.metamx.common.Granularity
import com.metamx.tranquility.beam.{Beam, ClusteredBeamTuning}
import com.metamx.tranquility.druid.{DruidBeams, DruidLocation, DruidRollup, SpecificDruidDimensions}
import com.metamx.tranquility.spark.BeamFactory
import io.druid.granularity.QueryGranularity
import io.druid.query.aggregation.{CountAggregatorFactory, LongSumAggregatorFactory}
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.BoundedExponentialBackoffRetry
import org.joda.time.{DateTime, DateTimeZone, Period}
import org.apache.log4j.Logger

class MapBeamFactory extends BeamFactory[Map[String, Any]]
{
  // Return a singleton, so the same connection is shared across all tasks in the same JVM.
  def makeBeam: Beam[Map[String, Any]] = MapBeamFactory.BeamInstance
}

object MapBeamFactory
{
  val BeamInstance: Beam[Map[String, Any]] = {
    // Tranquility uses ZooKeeper (through Curator framework) for coordination.
    val logger = Logger.getLogger(classOf[MapBeamFactory])

    val curator = CuratorFrameworkFactory.newClient(
      "ec2-54-83-35-212.compute-1.amazonaws.com:2181",
      new BoundedExponentialBackoffRetry(100, 3000, 5)
    )
    curator.start()
    val indexService = "druid/overlord"
    val firehosePattern = "druid:firehose:%s"
    val discoveryPath = "/druid/discovery"
    val dataSource = "test1_hashTagRankByCountWithScore"
    val dimensions = IndexedSeq("hashTag")
    val aggregators = Seq(new LongSumAggregatorFactory("score", "score"))

    // Expects simpleEvent.timestamp to return a Joda DateTime object.
    DruidBeams
      .builder[Map[String, Any]]((eventMap: Map[String, Any]) => eventMap("timestamp").asInstanceOf[DateTime])
      .curator(curator)
      .discoveryPath(discoveryPath)
      .location(DruidLocation(indexService, firehosePattern, dataSource))
      .rollup(DruidRollup(SpecificDruidDimensions(dimensions), aggregators, QueryGranularity.fromString("MINUTE")))
      .tuning(
        ClusteredBeamTuning(
          segmentGranularity = Granularity.HOUR,
          windowPeriod = new Period("PT10M"),
          partitions = 1,
          replicants = 1
        )
      )
      .buildBeam()
  }
}

