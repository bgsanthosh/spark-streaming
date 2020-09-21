package org.cloudera.qe.e2es

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming._

import sys.process._
import scala.io.Source
import java.io.File
import java.io.PrintWriter

import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._
import org.apache.spark.sql._

object TwitterStructuredStreaming {

        def main(args: Array[String]): Unit = {

        val ksourcetopic = "twitter"
        val ktargettopic = "tweet"
        val kborkers = args(0)

        val conf = new SparkConf()
        .setAppName("DEX Streaming")
        .set("spark.kafka.bootstrap.servers", kborkers)
        .set("spark.kafka.sasl.kerberos.service.name", "kafka")
        .set("spark.kafka.security.protocol", "SASL_SSL")
        .set("spark.kafka.ssl.truststore.location", "/usr/lib/jvm/java-1.8.0/jre/lib/security/cacerts")
        .set("spark.kafka.ssl.truststore.password", "changeit")
        .set("spark.sql.streaming.checkpointLocation", s"/app/mount/checkpoint-temp-1")

        val spark = SparkSession.builder.config(conf).getOrCreate()

import spark.implicits._


    println("source topic: "+ksourcetopic)
            println("target topic: "+ktargettopic)
            println("brokers: "+kborkers)


            val jsonStr = Source.fromURL("https://sunileman.s3.amazonaws.com/twitter/tweet1.json").mkString
            val twitterDataScheme = spark.read.json(Seq(jsonStr).toDS).toDF().schema



            val broadcastSchema = spark.sparkContext.broadcast(twitterDataScheme)


            //read twitter stream
            val df = spark.readStream.format("kafka")
            .option("kafka.bootstrap.servers", kborkers)
            .option("startingOffsets", "latest")
            .option("kafka.sasl.kerberos.service.name", "kafka")
            .option("kafka.security.protocol", "SASL_SSL")
            .option("kafka.ssl.truststore.location", "/usr/lib/jvm/java-1.8.0/jre/lib/security/cacerts")
            .option("kafka.ssl.truststore.password", "changeit")
            .option("subscribe", ksourcetopic).load()

            //extract only the text field from the tweet and write to a kafka topic
            val ds = df.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
            .filter($"value".contains("created_at"))
            .select(from_json($"value",schema = broadcastSchema.value).as("data")).select($"data".getItem("text").alias("value"))
            .writeStream.format("kafka")
            .option("kafka.bootstrap.servers", kborkers)
            .option("kafka.sasl.kerberos.service.name", "kafka")
            .option("kafka.security.protocol", "SASL_SSL")
            .option("kafka.ssl.truststore.location", "/usr/lib/jvm/java-1.8.0/jre/lib/security/cacerts")
            .option("kafka.ssl.truststore.password", "changeit")
            .option("topic", ktargettopic).option("checkpointLocation", "/app/mount/checkpoint-temp-1")
            .start().awaitTermination()

            }
            }