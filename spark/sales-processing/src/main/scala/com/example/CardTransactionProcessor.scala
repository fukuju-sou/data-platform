package com.example

import org.apache.spark.sql.SparkSession

object CardTransactionProcessor {

  def main(args: Array[String]): Unit = {

    if (args.length != 3) {
      System.err.println(
        "Usage: CardTransactionProcessor " +
        "<transaction_path> " +
        "<customer_master_path> " +
        "<processed_output_path>"
      )

      System.exit(1)
    }

    val transactionPath = args(0)
    val customerMasterPath = args(1)
    val processedOutputPath = args(2)

    val spark = SparkSession.builder()
      .appName("CardTransactionProcessor")
      .master("local[*]")
      .config(
        "spark.sql.catalog.clickhouse",
        "com.clickhouse.spark.ClickHouseCatalog"
      )
      .config(
        "spark.sql.catalog.clickhouse.host",
        "localhost"
      )
      .config(
        "spark.sql.catalog.clickhouse.protocol",
        "http"
      )
      .config(
        "spark.sql.catalog.clickhouse.http_port",
        "8123"
      )
      .config(
        "spark.sql.catalog.clickhouse.database",
        "data_platform"
      )
      .config(
        "spark.sql.catalog.clickhouse.user",
        "default"
      )
      .config(
        "spark.sql.catalog.clickhouse.password",
        ""
      )
      .getOrCreate()

    try {

      println("========================================")
      println("Card Transaction Processing")
      println("========================================")

      // --------------------------------
      // 1. カード利用データ読み込み
      // --------------------------------

      val transactionDF = spark.read
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(transactionPath)

      println()
      println("=== Transaction Data ===")
      transactionDF.show(false)


      // --------------------------------
      // 2. 顧客マスタ読み込み
      // --------------------------------

      val customerDF = spark.read
        .option("header", "true")
        .option("inferSchema", "false")
        .csv(customerMasterPath)

      println()
      println("=== Customer Master ===")
      customerDF.show(false)


      // --------------------------------
      // 3. カード番号でJOIN
      // --------------------------------

      val mergedDF = transactionDF
        .join(
          customerDF,
          transactionDF("card_number") === customerDF("card_number"),
          "inner"
        )
        .select(
          transactionDF("transaction_id"),
          transactionDF("card_number"),
          transactionDF("purchase_date"),
          transactionDF("category"),
          transactionDF("product"),
          transactionDF("unit_price"),
          transactionDF("quantity"),
          transactionDF("amount"),
          customerDF("customer_id"),
          customerDF("name"),
          customerDF("email"),
          customerDF("address")
        )
        .orderBy("transaction_id")

      println()
      println("=== Merged Data ===")
      mergedDF.show(false)


      // --------------------------------
      // 4. CSVへ保存
      // --------------------------------

      mergedDF
        .coalesce(1)
        .write
        .mode("overwrite")
        .option("header", "true")
        .csv(processedOutputPath)

      println()
      println(s"CSV output: $processedOutputPath")


      // --------------------------------
      // 5. ClickHouseへ保存
      // --------------------------------

      println()
      println("=== Writing to ClickHouse ===")

      mergedDF.writeTo(
        "clickhouse.data_platform.card_transactions"
      ).append()

      println()
      println("=== ClickHouse Output completed ===")

    } finally {
      spark.stop()
    }
  }
}
