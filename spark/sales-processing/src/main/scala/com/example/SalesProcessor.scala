package com.example

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object SalesProcessor {

  def main(args: Array[String]): Unit = {

    // --------------------------------
    // 1. Path
    // --------------------------------

    val transactionPath =
    "/opt/data/raw/card_transactions"

    val customerPath =
    "/opt/data/raw/customer_master/customer.csv"

    // --------------------------------
    // 2. ClickHouse settings
    // --------------------------------

    val clickhouseUrl =
    "jdbc:clickhouse://clickhouse:8123/data_platform"

    val clickhouseTable =
      "card_transactions"

    val clickhouseUser =
      "default"

    val clickhousePassword =
      ""

    println("====================================")
    println("Card Transaction Processing")
    println("====================================")

    println(s"Transaction : $transactionPath")
    println(s"Customer    : $customerPath")
    println(s"ClickHouse  : $clickhouseUrl")
    println(s"Table       : $clickhouseTable")

    // --------------------------------
    // 3. SparkSession
    // --------------------------------

    val spark = SparkSession.builder()
      .appName("CardTransactionProcessor")
      .master("spark://spark-master:7077")
      .getOrCreate()

    try {

      // --------------------------------
      // 4. Read transaction CSV
      // --------------------------------

      val transactionDF = spark.read
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(transactionPath)

      println()
      println("=== Transaction Data ===")

      transactionDF.show(false)

      println()
      println("=== Transaction Schema ===")

      transactionDF.printSchema()

      // --------------------------------
      // 5. Read customer master
      // --------------------------------

      val customerDF = spark.read
        .option("header", "true")
        .option("inferSchema", "false")
        .csv(customerPath)

      println()
      println("=== Customer Master ===")

      customerDF.show(false)

      println()
      println("=== Customer Schema ===")

      customerDF.printSchema()

      // --------------------------------
      // 6. Join
      // --------------------------------

      val mergedDF = transactionDF
        .join(
          customerDF,
          Seq("card_number"),
          "inner"
        )
        .select(
          col("transaction_id"),
          col("card_number"),
          to_timestamp(
            col("purchase_date"),
            "yyyy-MM-dd HH:mm:ss"
          ).alias("purchase_date"),
          col("category"),
          col("product"),
          col("unit_price").cast("long"),
          col("quantity").cast("long"),
          col("amount").cast("long"),
          col("customer_id"),
          col("name"),
          col("email"),
          col("address")
        )

      // --------------------------------
      // 7. Display merged data
      // --------------------------------

      println()
      println("=== Merged Data ===")

      mergedDF.show(false)

      println()
      println("=== Merged Schema ===")

      mergedDF.printSchema()

      // --------------------------------
      // 8. Record count
      // --------------------------------

      val count = mergedDF.count()

      println()
      println(s"Merged records: $count")

      // --------------------------------
      // 9. Write to ClickHouse
      // --------------------------------

      println()
      println("=== Writing to ClickHouse ===")

      mergedDF.write
        .format("jdbc")
        .option("url", clickhouseUrl)
        .option("dbtable", clickhouseTable)
        .option("user", clickhouseUser)
        .option("password", clickhousePassword)
        .option("driver", "com.clickhouse.jdbc.ClickHouseDriver")
        .option("batchsize", "1000")
        .mode("append")
        .save()

      println()
      println("====================================")
      println("Completed successfully")
      println("====================================")

    } finally {

      spark.stop()

    }
  }
}