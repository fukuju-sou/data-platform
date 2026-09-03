# Data Platform

Python、Apache Spark、Scala、ClickHouseを使用して、カード取引データと顧客マスターを結合し、ClickHouseへ保存する学習用データ処理基盤です。

## 1. 概要

このプロジェクトでは、以下のデータ処理を実行します。

1. 顧客マスターに登録されているカード番号を読み込む
2. Pythonでランダムなカード取引データを生成する
3. 生成した取引データをCSVとして保存する
4. Apache Sparkで取引CSVと顧客マスターCSVを読み込む
5. `card_number` をキーとしてINNER JOINする
6. JOIN後のデータをClickHouseへJDBC経由でINSERTする

処理対象はカード取引データであり、現在のSparkプログラムは従来の売上集計処理ではなく、顧客マスターとのJOINおよびClickHouseへの保存を行います。

---

## 2. システム構成

```text
┌──────────────────────────────┐
│ Python                       │
│ card_transaction_generator.py│
│                              │
│ 顧客マスターからカード番号を │
│ 取得してランダム取引を生成   │
└──────────────┬───────────────┘
               │
               │ CSV
               ▼
┌──────────────────────────────┐
│ raw/card_transactions/       │
│ transactions.csv             │
└──────────────┬───────────────┘
               │
               │
               │        ┌──────────────────────────────┐
               │        │ raw/customer_master/         │
               │        │ customer.csv                 │
               │        └──────────────┬───────────────┘
               │                       │
               └───────────┬───────────┘
                           │
                           ▼
┌──────────────────────────────────────────┐
│ Apache Spark                             │
│ SalesProcessor.scala                     │
│                                          │
│ 1. transaction CSV読み込み               │
│ 2. customer master読み込み               │
│ 3. card_numberでINNER JOIN               │
│ 4. 必要な列をselect                      │
│ 5. 型変換                                │
└──────────────────────┬───────────────────┘
                       │
                       │ JDBC
                       ▼
┌──────────────────────────────────────────┐
│ ClickHouse                               │
│                                          │
│ Database: data_platform                  │
│ Table:    card_transactions              │
└──────────────────────────────────────────┘
```

---

## 3. データ処理の流れ

### 3.1 取引データ生成

`tools/card_transaction_generator.py` が顧客マスターの `card_number` を読み込み、ランダムな取引データを20件生成します。

生成される主な項目は以下です。

| 項目               | 内容                      |
| ---------------- | ----------------------- |
| `transaction_id` | 取引ID                    |
| `card_number`    | 顧客マスターからランダムに選択         |
| `purchase_date`  | 生成時刻                    |
| `category`       | 商品カテゴリ                  |
| `product`        | 商品名                     |
| `unit_price`     | 単価                      |
| `quantity`       | 数量                      |
| `amount`         | `unit_price × quantity` |

生成先は以下です。

```text
raw/card_transactions/transactions.csv
```

実装では、顧客マスターを以下から読み込みます。

```text
raw/customer_master/customer.csv
```

生成処理は `tools/card_transaction_generator.py` に実装されています。

---

## 4. Sparkによるデータ処理

Sparkのメインプログラムは以下です。

```text
spark/sales-processing/src/main/scala/com/example/SalesProcessor.scala
```

### 4.1 取引データの読み込み

以下のCSVを読み込みます。

```text
../../raw/card_transactions
```

SparkのCSV readerで `header=true`、`inferSchema=true` として読み込みます。

### 4.2 顧客マスターの読み込み

以下のCSVを読み込みます。

```text
../../raw/customer_master/customer.csv
```

顧客マスターは `header=true`、`inferSchema=false` で読み込みます。

### 4.3 JOIN

以下のキーでINNER JOINします。

```text
card_number
```

つまり、

```text
transaction.card_number
        =
customer_master.card_number
```

となるレコードのみがJOIN後のデータとして残ります。

### 4.4 JOIN後のデータ

JOIN後には以下の列を使用します。

```text
transaction_id
card_number
purchase_date
category
product
unit_price
quantity
amount
customer_id
name
email
address
```

`purchase_date` はTimestampへ変換し、

```text
yyyy-MM-dd HH:mm:ss
```

の形式として扱います。

また、

```text
unit_price
quantity
amount
```

は `long` 型へ変換します。

---

## 5. ClickHouse

ClickHouseはDocker Composeで起動します。

```text
docker-compose.yml
```

で使用しているイメージは、

```text
clickhouse/clickhouse-server:26.3
```

です。

ホストから利用するポートは以下です。

| ポート    | 用途                         |
| ------ | -------------------------- |
| `8123` | ClickHouse HTTP            |
| `9000` | ClickHouse Native protocol |

ClickHouseのデータベースは、

```text
data_platform
```

です。

Sparkからは以下のJDBC URLを使用します。

```text
jdbc:clickhouse://localhost:8123/data_platform
```

---

## 6. ClickHouseのテーブル

初期化SQLは以下です。

```text
clickhouse/init.sql
```

作成されるテーブルは、

```text
data_platform.card_transactions
```

です。

テーブル定義は以下の通りです。

| カラム              | ClickHouse型 |
| ---------------- | ----------- |
| `transaction_id` | `UInt32`    |
| `card_number`    | `String`    |
| `purchase_date`  | `DateTime`  |
| `category`       | `String`    |
| `product`        | `String`    |
| `unit_price`     | `UInt32`    |
| `quantity`       | `UInt32`    |
| `amount`         | `UInt32`    |
| `customer_id`    | `String`    |
| `name`           | `String`    |
| `email`          | `String`    |
| `address`        | `String`    |

テーブルエンジンには `MergeTree` を使用し、

```text
ORDER BY (purchase_date, customer_id)
```

としています。

---

## 7. ディレクトリ構成

現在のリポジトリ構成は以下です。

```text
data-platform/
├── clickhouse/
│   └── init.sql
│
├── logs/
│
├── processed/
│
├── raw/
│   ├── card_transactions/
│   │   └── transactions.csv
│   │
│   └── customer_master/
│       └── customer.csv
│
├── spark/
│   └── sales-processing/
│       ├── project/
│       │
│       ├── src/
│       │   └── main/
│       │       └── scala/
│       │           └── com/
│       │               └── example/
│       │                   └── SalesProcessor.scala
│       │
│       └── build.sbt
│
├── tools/
│   └── card_transaction_generator.py
│
├── .gitignore
├── docker-compose.yml
└── README.md
```

### 主なファイル・ディレクトリ

| パス                                       | 役割                              |
| ---------------------------------------- | ------------------------------- |
| `tools/card_transaction_generator.py`    | ランダム取引データ生成                     |
| `raw/customer_master/customer.csv`       | 顧客マスター                          |
| `raw/card_transactions/transactions.csv` | 取引データ                           |
| `spark/sales-processing/`                | Scala + Sparkプロジェクト             |
| `SalesProcessor.scala`                   | SparkによるJOIN・ClickHouse保存       |
| `build.sbt`                              | Scala/Spark/ClickHouse JDBC依存関係 |
| `clickhouse/init.sql`                    | ClickHouse DB・テーブル初期化           |
| `docker-compose.yml`                     | ClickHouseコンテナ定義                |
| `processed/`                             | リポジトリ上に存在するディレクトリ               |
| `logs/`                                  | ログ用ディレクトリ                       |

---

## 8. 使用技術

### Python

ランダムなカード取引データを生成します。

```text
Python 3
```

標準ライブラリのみを使用しており、`card_transaction_generator.py` では追加のPythonパッケージを使用していません。

### Scala / Spark

`build.sbt` では以下を使用しています。

```text
Scala 2.12.20
Apache Spark 3.5.6
```

### ClickHouse JDBC

SparkからClickHouseへ接続するため、

```text
com.clickhouse:clickhouse-jdbc:0.9.8
```

を使用します。

### ClickHouse

Docker Composeでは、

```text
clickhouse/clickhouse-server:26.3
```

を使用します。

---

## 9. 前提環境

以下の環境を前提とします。

* Ubuntu
* Java 17
* Scala 2.12
* Apache Spark 3.5.6
* sbt
* Python 3
* Docker
* Docker Compose

Sparkプロジェクトの依存関係は `build.sbt` に定義されています。

```scala
ThisBuild / scalaVersion := "2.12.20"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.6",
  "org.apache.spark" %% "spark-sql" % "3.5.6",
  "com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
)
```

Java 17でSparkを実行するため、以下の設定も使用しています。

```scala
Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)
```

ANTLRについては、Sparkとの依存関係上の競合を避けるため、

```scala
dependencyOverrides ++= Seq(
  "org.antlr" % "antlr4-runtime" % "4.9.3"
)
```

としています。

---

## 10. クローン後の実行方法

### 10.1 リポジトリをクローン

```bash
git clone https://github.com/fukuju-sou/data-platform.git
cd data-platform
```

### 10.2 顧客マスターを確認

```bash
ls -lh raw/customer_master/customer.csv
```

以下のファイルが存在することを確認します。

```text
raw/customer_master/customer.csv
```

### 10.3 ランダム取引データを生成

リポジトリのルートで実行します。

```bash
python3 tools/card_transaction_generator.py
```

正常に終了すると、

```text
raw/card_transactions/transactions.csv
```

が生成されます。

確認：

```bash
ls -lh raw/card_transactions/
```

### 10.4 ClickHouseを起動

リポジトリのルートで実行します。

```bash
docker compose up -d
```

コンテナを確認します。

```bash
docker ps
```

`data-platform-clickhouse` が起動していることを確認します。

### 10.5 Sparkプロジェクトへ移動

```bash
cd spark/sales-processing
```

### 10.6 コンパイル

```bash
sbt clean
sbt compile
```

### 10.7 Sparkプログラムを実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

処理が成功すると、最後に以下が表示されます。

```text
====================================
Completed successfully
====================================
```

---

## 11. ClickHouseの結果確認

ClickHouseコンテナへ接続します。

```bash
docker exec -it data-platform-clickhouse clickhouse-client
```

データベースを選択します。

```sql
USE data_platform;
```

テーブルを確認します。

```sql
SHOW TABLES;
```

以下が表示されます。

```text
card_transactions
```

レコードを確認します。

```sql
SELECT *
FROM card_transactions
LIMIT 20;
```

件数を確認する場合：

```sql
SELECT count()
FROM card_transactions;
```

---

## 12. 実行時の重要事項

### 相対パス

`SalesProcessor.scala` の入力パスは、

```text
../../raw/card_transactions
../../raw/customer_master/customer.csv
```

となっています。

そのため、Sparkプロジェクトのディレクトリから実行する必要があります。

```bash
cd spark/sales-processing
sbt 'runMain com.example.SalesProcessor'
```

### main class

Sparkプロジェクトでは実行対象を明示して、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用します。

### ClickHouse

Spark実行前にClickHouseが起動している必要があります。

```bash
docker ps
```

でコンテナの状態を確認してください。

---

## 13. トラブルシューティング

実行時にエラーが発生した場合は、以下を参照してください。

```text
Troubleshooting.md
```

主に以下の問題について記載しています。

* sbtの実行エラー
* ClickHouse JDBC依存関係
* main classの検出
* Sparkの相対パス
* ANTLR依存関係
* Spark起動時の警告
* Hadoop Native Library警告
* ClickHouse接続
* ClickHouseテーブル
* JDBC INSERT時の型
* 重複INSERT
