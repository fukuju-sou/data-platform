package com.example

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object SalesProcessor {

  def main(args: Array[String]): Unit = {

    // --------------------------------
    // 1. 引数チェック
    // --------------------------------
    if (args.length != 2) {
      System.err.println(
        "Usage: SalesProcessor <input_path> <output_path>"
      )
      System.exit(1)
    }

    val inputPath = args(0)
    val outputPath = args(1)

    println(s"Input : $inputPath")
    println(s"Output: $outputPath")

    // --------------------------------
    // 2. SparkSession作成
    // --------------------------------
    val spark = SparkSession.builder()
      .appName("SalesProcessor")
      .master("local[*]")
      .getOrCreate()

    try {

      // --------------------------------
      // 3. CSV読み込み
      // --------------------------------
      val salesDF = spark.read
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(inputPath)

      println()
      println("=== Input Data ===")
      salesDF.show(false)

      println()
      println("=== Input Schema ===")
      salesDF.printSchema()

      // --------------------------------
      // 4. 売上金額を計算
      // --------------------------------
      val processedDF = salesDF
        .withColumn(
          "total_price",
          col("price") * col("quantity")
        )

      println()
      println("=== Processed Data ===")
      processedDF.show(false)

      // --------------------------------
      // 5. categoryごとに集計
      // --------------------------------
      val summaryDF = processedDF
        .groupBy("category")
        .agg(
          sum("total_price").alias("total_sales"),
          sum("quantity").alias("total_quantity")
        )
        .orderBy("category")

      println()
      println("=== Summary ===")
      summaryDF.show(false)

      // --------------------------------
      // 6. CSVとして出力
      // --------------------------------
      summaryDF
        .coalesce(1)
        .write
        .mode("overwrite")
        .option("header", "true")
        .csv(outputPath)

      println()
      println(s"=== Output completed ===")
      println(s"Output written to: $outputPath")

    } finally {

      // --------------------------------
      // 7. Spark終了
      // --------------------------------
      spark.stop()
    }
  }
}