# Sales Processing with Apache Spark

Apache Spark（Scala）を使用して、売上CSVデータを加工・集計する学習用プロジェクトです。

## 概要

このプロジェクトでは、以下の処理をApache Sparkで実行します。

1. CSV形式の売上データを読み込む
2. `price × quantity` から売上金額（`total_price`）を計算する
3. `category` ごとに売上金額と数量を集計する
4. 集計結果をCSVとして出力する

---

## 環境

- OS: Ubuntu
- Java: 17.0.20
- Scala: 2.12.20
- Apache Spark: 3.5.6
- sbt

---

## ディレクトリ構成

```text
data-platform/
├── logs/
│
├── processed/
│   └── # Sparkによる処理結果が出力される
│
├── raw/
│   └── input/
│       └── sales.csv
│
├── spark/
│   └── sales-processing/
│       ├── project/
│       │   └── build.properties
│       │
│       ├── src/
│       │   └── main/
│       │       └── scala/
│       │           └── com/
│       │               └── example/
│       │                   └── SalesProcessor.scala
│       │
│       ├── build.sbt
│       └── README.md
│
├── .gitignore
└── README.md
```

### 各ディレクトリの役割

| ディレクトリ | 役割 |
|---|---|
| `raw/` | 加工前のデータ |
| `raw/input/` | Sparkが読み込む入力CSV |
| `processed/` | Sparkによる加工・集計後のデータ |
| `logs/` | ログを保存するためのディレクトリ |
| `spark/` | Spark関連プロジェクト |
| `spark/sales-processing/` | Scala + Sparkによる売上処理プロジェクト |

---

# 処理の流れ

処理全体の流れは以下のようになります。

```text
┌──────────────────────────────┐
│ raw/input/sales.csv          │
│                              │
│ 元となる売上データ            │
└──────────────┬───────────────┘
               │
               │ CSV読み込み
               ▼
┌──────────────────────────────┐
│ SalesProcessor.scala         │
│                              │
│ Apache Spark                 │
│                              │
│ ① CSVをDataFrameとして読み込む │
│                              │
│ ② total_priceを計算する       │
│    price × quantity           │
│                              │
│ ③ categoryごとに集計する      │
│    ・total_sales              │
│    ・total_quantity            │
└──────────────┬───────────────┘
               │
               │ CSV出力
               ▼
┌──────────────────────────────┐
│ processed/                   │
│                              │
│ part-xxxxx.csv               │
│ _SUCCESS                     │
└──────────────────────────────┘
```

---

## 処理例

例えば、入力データが以下の場合、

```csv
id,product,category,price,quantity
1,Book A,Book,1000,2
2,Book B,Book,1500,3
3,Pen A,Stationery,200,5
4,Pen B,Stationery,300,2
```

### 1. CSVを読み込む

SparkがCSVをDataFrameとして読み込みます。

```text
id  product  category     price  quantity
1   Book A   Book         1000   2
2   Book B   Book         1500   3
3   Pen A    Stationery   200    5
4   Pen B    Stationery   300    2
```

---

### 2. `total_price` を計算する

各商品の

```text
price × quantity
```

を計算します。

```text
Book A
1000 × 2 = 2000

Book B
1500 × 3 = 4500

Pen A
200 × 5 = 1000

Pen B
300 × 2 = 600
```

その結果、DataFrameは以下のようになります。

```text
id  product  category     price  quantity  total_price
1   Book A   Book         1000   2         2000
2   Book B   Book         1500   3         4500
3   Pen A    Stationery   200    5         1000
4   Pen B    Stationery   300    2         600
```

---

### 3. `category` ごとに集計する

`category` をキーとして、

- `total_sales`
- `total_quantity`

を集計します。

```text
Book
total_sales    = 2000 + 4500 = 6500
total_quantity = 2 + 3       = 5

Stationery
total_sales    = 1000 + 600 = 1600
total_quantity = 5 + 2      = 7
```

最終的なDataFrameは、

```text
category     total_sales  total_quantity
Book         6500         5
Stationery   1600         7
```

となります。

---

### 4. CSVとして出力する

集計結果を以下のディレクトリに出力します。

```text
processed/
```

Sparkは通常、1つのCSVファイルではなく、ディレクトリとして結果を出力します。

```text
processed/
├── part-00000-xxxxxxxx.csv
└── _SUCCESS
```

`part-*.csv` が実際のデータです。

`_SUCCESS` はSparkの処理が正常に完了したことを示すファイルです。

---

# ソースコード

主要な処理は以下のScalaプログラムで実装しています。

```text
spark/sales-processing/src/main/scala/com/example/SalesProcessor.scala
```

処理の概要は以下の通りです。

```scala
// CSVを読み込む
val salesDF = spark.read
  .option("header", "true")
  .option("inferSchema", "true")
  .csv(inputPath)

// 売上金額を計算
val processedDF = salesDF
  .withColumn(
    "total_price",
    col("price") * col("quantity")
  )

// categoryごとの集計
val summaryDF = processedDF
  .groupBy("category")
  .agg(
    sum("total_price").alias("total_sales"),
    sum("quantity").alias("total_quantity")
  )
  .orderBy("category")

// CSVとして出力
summaryDF.write
  .mode("overwrite")
  .option("header", "true")
  .csv(outputPath)
```

---

# sbt と Spark

このプロジェクトでは、`sbt` を使ってScalaプログラムをコンパイル・実行します。

処理の関係は以下のようになります。

```text
┌──────────────────┐
│    build.sbt     │
│                  │
│ Scala / Spark    │
│ 依存関係・設定    │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│       sbt        │
└────────┬─────────┘
         │
         ├── sbt compile
         │       │
         │       ▼
         │   Scalaをコンパイル
         │
         └── sbt runMain
                 │
                 ▼
       ┌──────────────────┐
       │ SalesProcessor    │
       │     Scala         │
       └────────┬─────────┘
                │
                ▼
       ┌──────────────────┐
       │ Apache Spark     │
       │                  │
       │ DataFrame処理     │
       │ 集計・変換        │
       └────────┬─────────┘
                │
                ▼
       ┌──────────────────┐
       │   processed/     │
       │   CSV output     │
       └──────────────────┘
```

---

# ビルド

`build.sbt` ではScalaのバージョンやSparkの依存関係などを定義しています。

Java 17でSparkを実行するため、以下の設定も追加しています。

```scala
Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)
```

この設定により、Java 17のモジュールアクセス制限によってSpark起動時に発生する問題を回避します。

---

# 実行方法

## 1. プロジェクトディレクトリへ移動

```bash
cd ~/projects/data-platform/spark/sales-processing
```

---

## 2. コンパイル

```bash
sbt compile
```

正常に終了すれば、Scalaプログラムのコンパイルが完了しています。

---

## 3. Sparkプログラムを実行

```bash
sbt "runMain com.example.SalesProcessor ../../raw/input/sales.csv ../../processed"
```

引数は以下の意味です。

```text
SalesProcessor
    │
    ├── 第1引数
    │      ../../raw/input/sales.csv
    │      ↓
    │      入力CSV
    │
    └── 第2引数
           ../../processed
           ↓
           出力ディレクトリ
```

---

## 4. 出力結果を確認

```bash
find ../../processed -maxdepth 2 -type f -ls
```

例えば以下のようなファイルが確認できます。

```text
processed/
├── part-00000-xxxxxxxx.csv
└── _SUCCESS
```

CSVの内容を確認する場合は、

```bash
cat ../../processed/part-*.csv
```

とします。

---

# 出力例

例えば以下のような結果になります。

```csv
category,total_sales,total_quantity
Book,6500,5
Stationery,1600,7
```
---

## Sparkの出力について

SparkのDataFrameをCSVとして出力すると、通常は単一のCSVファイルではなく、出力ディレクトリが作成されます。

```text
processed/
├── part-00000-xxxxxxxx.csv
└── _SUCCESS
```

これはSparkが分散処理を前提としてデータを書き出すためです。

今回のプログラムはローカル環境で実行していますが、Sparkの基本的なデータ処理・入出力の仕組みを学習することを目的としています。