# Troubleshooting

このドキュメントでは、`data-platform` プロジェクトの Spark / Scala / ClickHouse 連携実装時に発生したエラーと、その原因・対処方法をまとめる。

---

## 1. sbt `clean compile` 実行時のエラー

### エラー

```text
[error] Expected whitespace character
[error] Expected '/'
[error] clean compile
[error]       ^
```

### 発生したコマンド

```bash
sbt clean compile
```

### 原因

使用している sbt のバージョン・実行環境では、

```bash
sbt clean compile
```

を1回のコマンドとして正しく解釈できず、`clean compile` の部分をsbtの構文として解析しようとしてエラーになった。

### 対処方法

`clean` と `compile` を分けて実行する。

```bash
sbt clean
sbt compile
```

### 確認結果

以下のように分けて実行することでコンパイルできた。

```bash
sbt clean
sbt compile
```

---

## 2. ClickHouse JDBC依存関係の `classifier` に関する問題

### 問題となった設定

当初、`build.sbt` に以下のような設定を入れていた。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier("all")
```

また、以下の書き方も使用していた。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier "all"
```

### 問題点

このプロジェクトでは ClickHouse JDBC ドライバを通常の依存関係として追加すればよく、`classifier("all")` は必要ない。

また、`classifier "all"` のような古いsbt記法を使う必要もない。

### 推奨設定

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
```

### 修正後

`build.sbt` では以下のように記述する。

```scala
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.6",
  "org.apache.spark" %% "spark-sql" % "3.5.6",
  "com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
)
```

---

## 3. `sbt run` で `no main class detected`

### エラー

```text
[error] java.lang.RuntimeException: no main class detected
```

### 発生したコマンド

```bash
sbt run
```

### 原因

プロジェクト内にScalaのmain classが複数存在していた。

確認すると、

```bash
find src -type f -name "*.scala" -print
```

で以下の2ファイルが存在していた。

```text
src/main/scala/com/example/SalesProcessor.scala
src/main/scala/com/example/CardTransactionProcessor.scala
```

さらに、

```bash
sbt 'show Compile / discoveredMainClasses'
```

を実行すると、

```text
com.example.CardTransactionProcessor
com.example.SalesProcessor
```

の2つがmain classとして検出された。

このため、単純に

```bash
sbt run
```

を実行した場合、sbtがどのmain classを起動すべきか決定できなかった。

### 対処方法

実行するmain classを明示する。

今回実行したいのは `SalesProcessor` なので、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用する。

### 確認方法

main classの一覧は以下で確認できる。

```bash
sbt 'show Compile / discoveredMainClasses'
```

### 補足

今回のプロジェクトでは、

```text
com.example.CardTransactionProcessor
com.example.SalesProcessor
```

の2つが存在するため、基本的には `runMain` を使用して実行対象を明示する。

---

## 4. Spark実行時の `PATH_NOT_FOUND`

### エラー

```text
org.apache.spark.sql.AnalysisException:
[PATH_NOT_FOUND] Path does not exist:
file:/home/hogeta/projects/raw/card_transactions
```

### 発生した設定

`SalesProcessor.scala` では当初、

```scala
val transactionPath =
  "../../../raw/card_transactions"

val customerPath =
  "../../../raw/customer_master/customer.csv"
```

としていた。

### 実行場所

Sparkプロジェクトのディレクトリは、

```text
/home/hogeta/projects/data-platform/spark/sales-processing
```

である。

### 原因

相対パスの基準は、Scalaソースファイルの場所ではなく、**プログラムを実行した現在のディレクトリ**になる。

実行場所：

```text
/home/hogeta/projects/data-platform/spark/sales-processing
```

ここから、

```text
../../../raw/card_transactions
```

とすると、

```text
/home/hogeta/projects/raw/card_transactions
```

を参照してしまう。

しかし実際の `raw` ディレクトリは、

```text
/home/hogeta/projects/data-platform/raw
```

にある。

### 正しい相対パス

以下のように修正する。

```scala
val transactionPath =
  "../../raw/card_transactions"

val customerPath =
  "../../raw/customer_master/customer.csv"
```

ディレクトリ構造は以下。

```text
data-platform/
├── raw/
│   ├── card_transactions/
│   ├── customer_master/
│   │   └── customer.csv
│   └── input/
│
└── spark/
    └── sales-processing/
        ├── build.sbt
        └── src/
```

`data-platform/spark/sales-processing` から `data-platform/raw` へ移動するには、

```text
../      → spark
../../   → data-platform
```

となる。

したがって、

```text
../../raw/card_transactions
../../raw/customer_master/customer.csv
```

が正しい。

---

## 5. ANTLRバージョン不一致によるSpark SQLエラー

### エラー

Spark起動自体は成功したが、CSV読み込み時に以下のエラーが発生した。

```text
ANTLR Tool version 4.9.3 used for code generation does not match
the current runtime version 4.13.2
```

続いて、

```text
Exception in thread "main" java.lang.ExceptionInInitializerError
```

さらに、

```text
Caused by: java.lang.UnsupportedOperationException:
java.io.InvalidClassException:
org.antlr.v4.runtime.atn.ATN;
Could not deserialize ATN with version 3 (expected 4).
```

となった。

### 発生箇所

スタックトレースから、Spark SQLのCSV読み込み処理で発生していることが分かる。

```text
at org.apache.spark.sql.execution.datasources.csv.CSVFileFormat.inferSchema
```

最終的には、

```text
at com.example.SalesProcessor$.main(SalesProcessor.scala:63)
```

で発生している。

### 原因

ANTLRのバージョンが競合している。

ログには、

```text
ANTLR Tool version 4.9.3
```

と、

```text
current runtime version 4.13.2
```

が同時に表示されている。

つまり、

```text
Spark側が想定しているANTLR
        ↓
4.9.3

実際にクラスパスへ入っているANTLR Runtime
        ↓
4.13.2
```

となっており、バージョンが一致していない。

ANTLRが生成したパーサーと、実行時のANTLR Runtimeのバージョンが一致しないため、

```text
Could not deserialize ATN
```

が発生している。

### 対処方法

`build.sbt` にANTLR Runtimeのバージョンを明示的に固定する。

```scala
dependencyOverrides ++= Seq(
  "org.antlr" % "antlr4-runtime" % "4.9.3"
)
```

### 修正後の `build.sbt`

```scala
ThisBuild / scalaVersion := "2.12.20"

Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)

lazy val root = (project in file("."))
  .settings(
    name := "sales-processing",
    version := "1.0.0",

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "3.5.6",
      "org.apache.spark" %% "spark-sql" % "3.5.6",
      "com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
    ),

    dependencyOverrides ++= Seq(
      "org.antlr" % "antlr4-runtime" % "4.9.3"
    )
  )
```

### 依存関係の確認

修正後、

```bash
sbt clean
sbt compile
```

を実行する。

その後、

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

でANTLRの依存関係を確認する。

`antlr4-runtime-4.9.3.jar` が使用されていることを確認する。

### 再実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

---

# 6. Spark起動時に表示される警告について

今回の実行では、以下の警告も表示された。

```text
WARN Utils: Your hostname, hoge resolves to a loopback address:
127.0.1.1; using 192.168.3.25 instead
```

また、

```text
WARN Utils: Set SPARK_LOCAL_IP if you need to bind to another address
```

が表示された。

### 原因

ホスト名 `hoge` が、

```text
127.0.1.1
```

というループバックアドレスに解決されているため、Sparkが実際のネットワークインターフェースである、

```text
192.168.3.25
```

を使用している。

### 今回の対応

今回の処理自体はSparkが、

```text
SparkContext
SparkUI
Executor
BlockManager
```

まで正常に起動しているため、この警告は今回の処理停止原因ではない。

したがって、現時点では対応不要。

必要になった場合は `SPARK_LOCAL_IP` の設定を検討する。

---

# 7. SparkのNative Hadoop Library警告

### 警告

```text
WARN NativeCodeLoader:
Unable to load native-hadoop library for your platform...
using builtin-java classes where applicable
```

### 原因

環境にHadoopのネイティブライブラリが存在しないため、Sparkが組み込みのJava実装を使用している。

### 今回の対応

今回のSparkローカル実行では処理を進められているため、この警告は今回のエラー原因ではない。

現時点では対応不要。

---

# 8. 現在までに確認できていること

現時点で以下の問題は解決済み。

| 項目 | 状態 |
|---|---|
| sbt `clean compile` | `clean` / `compile`を分けて実行することで解決 |
| ClickHouse JDBC classifier | `classifier("all")`を削除 |
| Scala main class | `runMain`で明示することで解決 |
| rawディレクトリの相対パス | `../../raw/...`へ修正 |
| Spark起動 | 正常 |
| Spark 3.5.6 | 正常起動 |
| Java 17 | 正常 |
| CSVファイルの探索 | パス修正後にSparkがファイル探索まで進行 |
| ANTLR | 4.9.3へ固定して対応 |

---

# 9. 推奨する実行手順

現在のプロジェクトでは、以下の順番で実行する。

## 9.1 プロジェクトへ移動

```bash
cd ~/projects/data-platform
```

## 9.2 トランザクションCSVを生成

```bash
python3 tools/card_transaction_generator.py
```

生成されたファイルを確認する。

```bash
ls -l raw/card_transactions/
```

想定：

```text
raw/card_transactions/transactions.csv
```

## 9.3 顧客マスターを確認

```bash
ls -l raw/customer_master/customer.csv
```

## 9.4 ClickHouseを起動

```bash
docker compose up -d
```

コンテナを確認。

```bash
docker ps
```

ClickHouseが起動していることを確認する。

## 9.5 Sparkプロジェクトへ移動

```bash
cd ~/projects/data-platform/spark/sales-processing
```

## 9.6 コンパイル

```bash
sbt clean
sbt compile
```

## 9.7 SalesProcessorを実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

---

# 10. トラブルシューティング時の基本確認コマンド

## Scalaファイル確認

```bash
find src -type f -name "*.scala" -print
```

## main class確認

```bash
sbt 'show Compile / discoveredMainClasses'
```

## build.sbt確認

```bash
cat -n build.sbt
```

## transaction CSV確認

```bash
ls -lh ../../raw/card_transactions/
```

## customer master確認

```bash
ls -lh ../../raw/customer_master/customer.csv
```

## ANTLR依存関係確認

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

## ClickHouseコンテナ確認

```bash
docker ps
```

## ClickHouse HTTP接続確認

```bash
curl http://localhost:8123
```

---

# 11. 現在のシステム構成

```text
Python
  │
  │ ランダムなカード取引データ生成
  ▼
raw/card_transactions/
  └── transactions.csv
        │
        │
        │
raw/customer_master/
  └── customer.csv
        │
        │
        ▼
┌──────────────────────────────┐
│ Spark / SalesProcessor.scala │
│                              │
│ 1. transaction CSV読み込み   │
│ 2. customer master読み込み   │
│ 3. card_numberでJOIN         │
│ 4. merged DataFrame生成      │
└──────────────┬───────────────┘
               │
               │ JDBC
               ▼
┌──────────────────────────────┐
│ ClickHouse                   │
│                              │
│ data_platform                │
│   └── card_transactions      │
└──────────────────────────────┘
```

---

# 12. 今後発生する可能性がある問題

ANTLR問題を解決した後、次に確認すべきポイントは以下。

## 12.1 ClickHouseが起動していない

想定エラー：

```text
Connection refused
```

確認：

```bash
docker ps
```

起動：

```bash
docker compose up -d
```

---

## 12.2 ClickHouse JDBC接続エラー

Sparkから、

```text
jdbc:clickhouse://localhost:8123/data_platform
```

へ接続できる必要がある。

ClickHouseのHTTPポート：

```text
8123
```

がホストへ公開されていることを確認する。

---

## 12.3 ClickHouseのテーブルが存在しない

確認：

```bash
docker exec -it data-platform-clickhouse clickhouse-client
```

ClickHouse上で、

```sql
USE data_platform;

SHOW TABLES;
```

を実行する。

`card_transactions` が存在することを確認する。

---

## 12.4 ClickHouseへのINSERT時の型不一致

Spark DataFrameの型とClickHouseテーブルの型が一致していない場合、JDBC INSERT時にエラーになる可能性がある。

特に、

```text
transaction_id
unit_price
quantity
amount
```

などの整数型について確認する。

---

## 12.5 同じデータを複数回INSERTしてしまう

現在のSpark処理は、

```scala
.mode("append")
```

でClickHouseへ保存する設計になっている。

そのため、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を同じCSVに対して複数回実行すると、同じトランザクションが複数回INSERTされる可能性がある。

また、現在のランダムデータ生成処理では、実行するたびに、

```text
transaction_id = 1
transaction_id = 2
...
```

のようにIDが再生成される可能性がある。

今後、再実行を前提とするシステムにする場合は、

- transaction_idの一意性
- 重複排除
- INSERT方式
- DELETE / TRUNCATE
- MergeTreeの設計

などを検討する必要がある。

---

# 13. 重要なポイント

今回のトラブルでは、以下の3点が特に重要だった。

### ① `sbt run` ではmain classを明示できない場合がある

main classが複数存在する場合：

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用する。

### ② 相対パスは「実行ディレクトリ」を基準に考える

Scalaファイルの場所ではなく、

```bash
pwd
```

で確認できる現在のディレクトリを基準にする。

### ③ Spark / Scalaでは依存ライブラリのバージョン競合に注意する

今回のANTLRのように、

```text
コンパイル時のバージョン
```

と

```text
実行時のRuntimeバージョン
```

が異なると、Spark起動後の処理実行時にエラーになる場合がある。

依存関係に問題がある場合は、

```bash
sbt 'show Compile / dependencyClasspath'
```

などで実際に使用されているJARを確認する。# Troubleshooting

このドキュメントでは、`data-platform` プロジェクトの Spark / Scala / ClickHouse 連携実装時に発生したエラーと、その原因・対処方法をまとめる。

---

## 1. sbt `clean compile` 実行時のエラー

### エラー

```text
[error] Expected whitespace character
[error] Expected '/'
[error] clean compile
[error]       ^
```

### 発生したコマンド

```bash
sbt clean compile
```

### 原因

使用している sbt のバージョン・実行環境では、

```bash
sbt clean compile
```

を1回のコマンドとして正しく解釈できず、`clean compile` の部分をsbtの構文として解析しようとしてエラーになった。

### 対処方法

`clean` と `compile` を分けて実行する。

```bash
sbt clean
sbt compile
```

### 確認結果

以下のように分けて実行することでコンパイルできた。

```bash
sbt clean
sbt compile
```

---

## 2. ClickHouse JDBC依存関係の `classifier` に関する問題

### 問題となった設定

当初、`build.sbt` に以下のような設定を入れていた。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier("all")
```

また、以下の書き方も使用していた。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier "all"
```

### 問題点

このプロジェクトでは ClickHouse JDBC ドライバを通常の依存関係として追加すればよく、`classifier("all")` は必要ない。

また、`classifier "all"` のような古いsbt記法を使う必要もない。

### 推奨設定

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
```

### 修正後

`build.sbt` では以下のように記述する。

```scala
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.6",
  "org.apache.spark" %% "spark-sql" % "3.5.6",
  "com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
)
```

---

## 3. `sbt run` で `no main class detected`

### エラー

```text
[error] java.lang.RuntimeException: no main class detected
```

### 発生したコマンド

```bash
sbt run
```

### 原因

プロジェクト内にScalaのmain classが複数存在していた。

確認すると、

```bash
find src -type f -name "*.scala" -print
```

で以下の2ファイルが存在していた。

```text
src/main/scala/com/example/SalesProcessor.scala
src/main/scala/com/example/CardTransactionProcessor.scala
```

さらに、

```bash
sbt 'show Compile / discoveredMainClasses'
```

を実行すると、

```text
com.example.CardTransactionProcessor
com.example.SalesProcessor
```

の2つがmain classとして検出された。

このため、単純に

```bash
sbt run
```

を実行した場合、sbtがどのmain classを起動すべきか決定できなかった。

### 対処方法

実行するmain classを明示する。

今回実行したいのは `SalesProcessor` なので、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用する。

### 確認方法

main classの一覧は以下で確認できる。

```bash
sbt 'show Compile / discoveredMainClasses'
```

### 補足

今回のプロジェクトでは、

```text
com.example.CardTransactionProcessor
com.example.SalesProcessor
```

の2つが存在するため、基本的には `runMain` を使用して実行対象を明示する。

---

## 4. Spark実行時の `PATH_NOT_FOUND`

### エラー

```text
org.apache.spark.sql.AnalysisException:
[PATH_NOT_FOUND] Path does not exist:
file:/home/hogeta/projects/raw/card_transactions
```

### 発生した設定

`SalesProcessor.scala` では当初、

```scala
val transactionPath =
  "../../../raw/card_transactions"

val customerPath =
  "../../../raw/customer_master/customer.csv"
```

としていた。

### 実行場所

Sparkプロジェクトのディレクトリは、

```text
/home/hogeta/projects/data-platform/spark/sales-processing
```

である。

### 原因

相対パスの基準は、Scalaソースファイルの場所ではなく、**プログラムを実行した現在のディレクトリ**になる。

実行場所：

```text
/home/hogeta/projects/data-platform/spark/sales-processing
```

ここから、

```text
../../../raw/card_transactions
```

とすると、

```text
/home/hogeta/projects/raw/card_transactions
```

を参照してしまう。

しかし実際の `raw` ディレクトリは、

```text
/home/hogeta/projects/data-platform/raw
```

にある。

### 正しい相対パス

以下のように修正する。

```scala
val transactionPath =
  "../../raw/card_transactions"

val customerPath =
  "../../raw/customer_master/customer.csv"
```

ディレクトリ構造は以下。

```text
data-platform/
├── raw/
│   ├── card_transactions/
│   ├── customer_master/
│   │   └── customer.csv
│   └── input/
│
└── spark/
    └── sales-processing/
        ├── build.sbt
        └── src/
```

`data-platform/spark/sales-processing` から `data-platform/raw` へ移動するには、

```text
../      → spark
../../   → data-platform
```

となる。

したがって、

```text
../../raw/card_transactions
../../raw/customer_master/customer.csv
```

が正しい。

---

## 5. ANTLRバージョン不一致によるSpark SQLエラー

### エラー

Spark起動自体は成功したが、CSV読み込み時に以下のエラーが発生した。

```text
ANTLR Tool version 4.9.3 used for code generation does not match
the current runtime version 4.13.2
```

続いて、

```text
Exception in thread "main" java.lang.ExceptionInInitializerError
```

さらに、

```text
Caused by: java.lang.UnsupportedOperationException:
java.io.InvalidClassException:
org.antlr.v4.runtime.atn.ATN;
Could not deserialize ATN with version 3 (expected 4).
```

となった。

### 発生箇所

スタックトレースから、Spark SQLのCSV読み込み処理で発生していることが分かる。

```text
at org.apache.spark.sql.execution.datasources.csv.CSVFileFormat.inferSchema
```

最終的には、

```text
at com.example.SalesProcessor$.main(SalesProcessor.scala:63)
```

で発生している。

### 原因

ANTLRのバージョンが競合している。

ログには、

```text
ANTLR Tool version 4.9.3
```

と、

```text
current runtime version 4.13.2
```

が同時に表示されている。

つまり、

```text
Spark側が想定しているANTLR
        ↓
4.9.3

実際にクラスパスへ入っているANTLR Runtime
        ↓
4.13.2
```

となっており、バージョンが一致していない。

ANTLRが生成したパーサーと、実行時のANTLR Runtimeのバージョンが一致しないため、

```text
Could not deserialize ATN
```

が発生している。

### 対処方法

`build.sbt` にANTLR Runtimeのバージョンを明示的に固定する。

```scala
dependencyOverrides ++= Seq(
  "org.antlr" % "antlr4-runtime" % "4.9.3"
)
```

### 修正後の `build.sbt`

```scala
ThisBuild / scalaVersion := "2.12.20"

Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
)

lazy val root = (project in file("."))
  .settings(
    name := "sales-processing",
    version := "1.0.0",

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "3.5.6",
      "org.apache.spark" %% "spark-sql" % "3.5.6",
      "com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
    ),

    dependencyOverrides ++= Seq(
      "org.antlr" % "antlr4-runtime" % "4.9.3"
    )
  )
```

### 依存関係の確認

修正後、

```bash
sbt clean
sbt compile
```

を実行する。

その後、

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

でANTLRの依存関係を確認する。

`antlr4-runtime-4.9.3.jar` が使用されていることを確認する。

### 再実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

---

# 6. Spark起動時に表示される警告について

今回の実行では、以下の警告も表示された。

```text
WARN Utils: Your hostname, hoge resolves to a loopback address:
127.0.1.1; using 192.168.3.25 instead
```

また、

```text
WARN Utils: Set SPARK_LOCAL_IP if you need to bind to another address
```

が表示された。

### 原因

ホスト名 `hoge` が、

```text
127.0.1.1
```

というループバックアドレスに解決されているため、Sparkが実際のネットワークインターフェースである、

```text
192.168.3.25
```

を使用している。

### 今回の対応

今回の処理自体はSparkが、

```text
SparkContext
SparkUI
Executor
BlockManager
```

まで正常に起動しているため、この警告は今回の処理停止原因ではない。

したがって、現時点では対応不要。

必要になった場合は `SPARK_LOCAL_IP` の設定を検討する。

---

# 7. SparkのNative Hadoop Library警告

### 警告

```text
WARN NativeCodeLoader:
Unable to load native-hadoop library for your platform...
using builtin-java classes where applicable
```

### 原因

環境にHadoopのネイティブライブラリが存在しないため、Sparkが組み込みのJava実装を使用している。

### 今回の対応

今回のSparkローカル実行では処理を進められているため、この警告は今回のエラー原因ではない。

現時点では対応不要。

---

# 8. 現在までに確認できていること

現時点で以下の問題は解決済み。

| 項目 | 状態 |
|---|---|
| sbt `clean compile` | `clean` / `compile`を分けて実行することで解決 |
| ClickHouse JDBC classifier | `classifier("all")`を削除 |
| Scala main class | `runMain`で明示することで解決 |
| rawディレクトリの相対パス | `../../raw/...`へ修正 |
| Spark起動 | 正常 |
| Spark 3.5.6 | 正常起動 |
| Java 17 | 正常 |
| CSVファイルの探索 | パス修正後にSparkがファイル探索まで進行 |
| ANTLR | 4.9.3へ固定して対応 |

---

# 9. 推奨する実行手順

現在のプロジェクトでは、以下の順番で実行する。

## 9.1 プロジェクトへ移動

```bash
cd ~/projects/data-platform
```

## 9.2 トランザクションCSVを生成

```bash
python3 tools/card_transaction_generator.py
```

生成されたファイルを確認する。

```bash
ls -l raw/card_transactions/
```

想定：

```text
raw/card_transactions/transactions.csv
```

## 9.3 顧客マスターを確認

```bash
ls -l raw/customer_master/customer.csv
```

## 9.4 ClickHouseを起動

```bash
docker compose up -d
```

コンテナを確認。

```bash
docker ps
```

ClickHouseが起動していることを確認する。

## 9.5 Sparkプロジェクトへ移動

```bash
cd ~/projects/data-platform/spark/sales-processing
```

## 9.6 コンパイル

```bash
sbt clean
sbt compile
```

## 9.7 SalesProcessorを実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

---

# 10. トラブルシューティング時の基本確認コマンド

## Scalaファイル確認

```bash
find src -type f -name "*.scala" -print
```

## main class確認

```bash
sbt 'show Compile / discoveredMainClasses'
```

## build.sbt確認

```bash
cat -n build.sbt
```

## transaction CSV確認

```bash
ls -lh ../../raw/card_transactions/
```

## customer master確認

```bash
ls -lh ../../raw/customer_master/customer.csv
```

## ANTLR依存関係確認

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

## ClickHouseコンテナ確認

```bash
docker ps
```

## ClickHouse HTTP接続確認

```bash
curl http://localhost:8123
```

---

# 11. 現在のシステム構成

```text
Python
  │
  │ ランダムなカード取引データ生成
  ▼
raw/card_transactions/
  └── transactions.csv
        │
        │
        │
raw/customer_master/
  └── customer.csv
        │
        │
        ▼
┌──────────────────────────────┐
│ Spark / SalesProcessor.scala │
│                              │
│ 1. transaction CSV読み込み   │
│ 2. customer master読み込み   │
│ 3. card_numberでJOIN         │
│ 4. merged DataFrame生成      │
└──────────────┬───────────────┘
               │
               │ JDBC
               ▼
┌──────────────────────────────┐
│ ClickHouse                   │
│                              │
│ data_platform                │
│   └── card_transactions      │
└──────────────────────────────┘
```

---

# 12. 今後発生する可能性がある問題

ANTLR問題を解決した後、次に確認すべきポイントは以下。

## 12.1 ClickHouseが起動していない

想定エラー：

```text
Connection refused
```

確認：

```bash
docker ps
```

起動：

```bash
docker compose up -d
```

---

## 12.2 ClickHouse JDBC接続エラー

Sparkから、

```text
jdbc:clickhouse://localhost:8123/data_platform
```

へ接続できる必要がある。

ClickHouseのHTTPポート：

```text
8123
```

がホストへ公開されていることを確認する。

---

## 12.3 ClickHouseのテーブルが存在しない

確認：

```bash
docker exec -it data-platform-clickhouse clickhouse-client
```

ClickHouse上で、

```sql
USE data_platform;

SHOW TABLES;
```

を実行する。

`card_transactions` が存在することを確認する。

---

## 12.4 ClickHouseへのINSERT時の型不一致

Spark DataFrameの型とClickHouseテーブルの型が一致していない場合、JDBC INSERT時にエラーになる可能性がある。

特に、

```text
transaction_id
unit_price
quantity
amount
```

などの整数型について確認する。

---

## 12.5 同じデータを複数回INSERTしてしまう

現在のSpark処理は、

```scala
.mode("append")
```

でClickHouseへ保存する設計になっている。

そのため、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を同じCSVに対して複数回実行すると、同じトランザクションが複数回INSERTされる可能性がある。

また、現在のランダムデータ生成処理では、実行するたびに、

```text
transaction_id = 1
transaction_id = 2
...
```

のようにIDが再生成される可能性がある。

今後、再実行を前提とするシステムにする場合は、

- transaction_idの一意性
- 重複排除
- INSERT方式
- DELETE / TRUNCATE
- MergeTreeの設計

などを検討する必要がある。

---

# 13. 重要なポイント

今回のトラブルでは、以下の3点が特に重要だった。

### ① `sbt run` ではmain classを明示できない場合がある

main classが複数存在する場合：

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用する。

### ② 相対パスは「実行ディレクトリ」を基準に考える

Scalaファイルの場所ではなく、

```bash
pwd
```

で確認できる現在のディレクトリを基準にする。

### ③ Spark / Scalaでは依存ライブラリのバージョン競合に注意する

今回のANTLRのように、

```text
コンパイル時のバージョン
```

と

```text
実行時のRuntimeバージョン
```

が異なると、Spark起動後の処理実行時にエラーになる場合がある。

依存関係に問題がある場合は、

```bash
sbt 'show Compile / dependencyClasspath'
```

などで実際に使用されているJARを確認する。