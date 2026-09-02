Sales Processing with Apache Spark

概要
====
Scala + Apache Sparkを使用して、売上CSVを加工・集計する学習用プログラム。

入力CSVから以下の処理を行う。

1. CSVを読み込む
2. price × quantityからtotal_priceを計算
3. categoryごとに売上金額と数量を集計
4. 集計結果をCSVとして出力


環境
====

OS:
Ubuntu

Java:
17

Scala:
2.12.20

Apache Spark:
3.5.6

Build Tool:
sbt


ディレクトリ構成
================

sales-processing/
├── .gitignore
├── README.txt
├── build.sbt
├── project/
└── src/
    └── main/
        └── scala/
            └── com/
                └── example/
                    └── SalesProcessor.scala


入力・出力
==========

入力:
../../raw/input/sales.csv

出力:
../../processed/


実行手順
========

1. プロジェクトディレクトリへ移動

cd ~/projects/data-platform/spark/sales-processing


2. コンパイル

sbt compile


3. プログラムを実行

sbt "runMain com.example.SalesProcessor ../../raw/input/sales.csv ../../processed"


4. 出力を確認

find ../../processed -maxdepth 2 -type f -ls


5. CSVの内容を確認

cat ../../processed/part-*.csv


注意事項
========

Spark 3.5.6をJava 17で実行するため、
build.sbtに以下のJavaオプションを設定している。

Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)


処理の流れ
==========

sales.csv
    |
    v
Spark DataFrame
    |
    | price * quantity
    v
processedDF
    |
    | GROUP BY category
    v
summaryDF
    |
    | write.csv()
    v
../../processed/


出力例
======

category,total_sales,total_quantity
Food,1300,15
Office,1000,10


補足
====

SparkのCSV出力では、processedディレクトリの中に
part-xxxxx.csvや_SUCCESSなどのファイルが生成される。

これはSparkの標準的な出力形式である。