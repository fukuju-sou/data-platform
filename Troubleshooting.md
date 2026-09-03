# Troubleshooting

このドキュメントでは、`data-platform` の実行・ビルド・Spark・ClickHouse連携で発生する問題について、エラー、原因、対処方法をまとめます。

システム構成、ディレクトリ構成、通常の実行手順については `README.md` を参照してください。

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

使用しているsbtの実行環境では、

```bash
sbt clean compile
```

を1回のコマンドとして正しく解釈できず、`clean compile` をsbtの構文として解析しようとしてエラーになりました。

### 対処方法

`clean` と `compile` を分けて実行します。

```bash
sbt clean
sbt compile
```

### 確認

以下でコンパイルできることを確認します。

```bash
sbt clean
sbt compile
```

---

## 2. ClickHouse JDBC依存関係の `classifier` に関する問題

### 問題となった設定

当初、`build.sbt` に以下の設定を使用していました。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier("all")
```

また、以下の書き方も使用していました。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8" classifier "all"
```

### 原因

このプロジェクトではClickHouse JDBCドライバを通常の依存関係として追加すればよく、`classifier("all")` は必要ありません。

### 対処方法

以下のようにします。

```scala
"com.clickhouse" % "clickhouse-jdbc" % "0.9.8"
```

現在の `build.sbt` ではこの形式になっています。

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

Scalaプロジェクト内に複数のmain classが存在する場合、`sbt run` だけでは実行対象を決定できないことがあります。

### 確認方法

以下でmain classを確認します。

```bash
find src -type f -name "*.scala" -print
```

また、

```bash
sbt 'show Compile / discoveredMainClasses'
```

でsbtが検出しているmain classを確認できます。

### 対処方法

実行対象のmain classを明示します。

```bash
sbt 'runMain com.example.SalesProcessor'
```

### 現在の実行対象

現在の主要なSpark処理は、

```text
com.example.SalesProcessor
```

です。

---

## 4. Spark実行時の `PATH_NOT_FOUND`

### エラー

```text
org.apache.spark.sql.AnalysisException:
[PATH_NOT_FOUND] Path does not exist:
file:/home/hogeta/projects/raw/card_transactions
```

### 原因

相対パスはScalaソースファイルの場所ではなく、**プログラムを実行した現在のディレクトリ**を基準として解決されます。

例えばSparkプロジェクトが、

```text
data-platform/spark/sales-processing
```

にある場合、

```text
../../../raw/card_transactions
```

とすると、`data-platform` の外側を参照してしまいます。

### 正しい相対パス

現在の `SalesProcessor.scala` では以下を使用しています。

```scala
val transactionPath =
  "../../raw/card_transactions"

val customerPath =
  "../../raw/customer_master/customer.csv"
```

### 実行時の確認

Sparkプロジェクトへ移動します。

```bash
cd spark/sales-processing
```

現在のディレクトリを確認します。

```bash
pwd
```

入力データの存在を確認します。

```bash
ls -lh ../../raw/card_transactions/
ls -lh ../../raw/customer_master/customer.csv
```

### 対処方法

基本的にはSparkプロジェクトディレクトリから実行します。

```bash
cd spark/sales-processing
sbt 'runMain com.example.SalesProcessor'
```

---

## 5. ANTLRバージョン不一致によるSpark SQLエラー

### エラー

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

### 発生箇所

Spark SQLのCSV読み込み処理で発生します。

```text
org.apache.spark.sql.execution.datasources.csv.CSVFileFormat.inferSchema
```

### 原因

ANTLRのバージョンが競合しています。

想定されるANTLR：

```text
4.9.3
```

実際にクラスパスへ入っているRuntime：

```text
4.13.2
```

ANTLRが生成したパーサーと実行時Runtimeのバージョンが一致しないため、

```text
Could not deserialize ATN
```

が発生します。

### 対処方法

`build.sbt` でANTLR Runtimeを明示的に固定します。

```scala
dependencyOverrides ++= Seq(
  "org.antlr" % "antlr4-runtime" % "4.9.3"
)
```

### 依存関係を確認

```bash
sbt clean
sbt compile
```

続いて、

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

を実行します。

`antlr4-runtime-4.9.3.jar` が使用されていることを確認します。

### 再実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

---

## 6. Spark起動時のhostname / `SPARK_LOCAL_IP` 警告

### 警告

```text
WARN Utils: Your hostname, hoge resolves to a loopback address:
127.0.1.1; using 192.168.3.25 instead
```

また、

```text
WARN Utils: Set SPARK_LOCAL_IP if you need to bind to another address
```

が表示される場合があります。

### 原因

ホスト名がループバックアドレスに解決されているため、Sparkが別のネットワークアドレスを使用している状態です。

### 判断

Sparkの以下のコンポーネントが正常に起動し、その後の処理も継続している場合、この警告自体は処理停止原因ではありません。

```text
SparkContext
SparkUI
Executor
BlockManager
```

### 対処

ローカル実行で問題なく処理できている場合、現時点では対応不要です。

別のネットワークインターフェースへバインドする必要がある場合は、`SPARK_LOCAL_IP` の設定を検討します。

---

## 7. SparkのNative Hadoop Library警告

### 警告

```text
WARN NativeCodeLoader:
Unable to load native-hadoop library for your platform...
using builtin-java classes where applicable
```

### 原因

環境にHadoopのネイティブライブラリが存在しないため、Sparkが組み込みのJava実装を使用しています。

### 対処

今回のようなSparkローカル実行で処理が正常に進んでいる場合、この警告は処理停止原因ではありません。

現時点では対応不要です。

---

## 8. ClickHouseが起動していない

### 症状

SparkからClickHouseへ接続する際に、

```text
Connection refused
```

などのエラーが発生します。

### 確認

```bash
docker ps
```

ClickHouseコンテナが起動していることを確認します。

### 起動

リポジトリのルートディレクトリで、

```bash
docker compose up -d
```

を実行します。

再度確認します。

```bash
docker ps
```

---

## 9. ClickHouse JDBC接続エラー

### 接続先

Sparkは以下のJDBC URLへ接続します。

```text
jdbc:clickhouse://localhost:8123/data_platform
```

### 確認事項

ClickHouseのHTTPポートである、

```text
8123
```

がホストへ公開されていることを確認します。

```bash
docker ps
```

必要に応じて、

```bash
curl http://localhost:8123
```

でHTTP接続を確認します。

### ClickHouseコンテナのログ確認

```bash
docker logs data-platform-clickhouse
```

---

## 10. ClickHouseのテーブルが存在しない

### 症状

SparkからINSERTするときに、対象テーブルが存在しないというエラーが発生します。

### ClickHouseへ接続

```bash
docker exec -it data-platform-clickhouse clickhouse-client
```

### データベースを選択

```sql
USE data_platform;
```

### テーブル確認

```sql
SHOW TABLES;
```

以下が存在することを確認します。

```text
card_transactions
```

### テーブル定義確認

```sql
DESCRIBE TABLE card_transactions;
```

---

## 11. ClickHouseへのINSERT時の型不一致

### 症状

Spark DataFrameからClickHouseへJDBC INSERTする際に、型に関するエラーが発生します。

### 原因

Spark DataFrameの型とClickHouseテーブルの型が一致していない可能性があります。

特に以下の列を確認します。

```text
transaction_id
unit_price
quantity
amount
```

### Spark側

現在の `SalesProcessor.scala` では以下を `long` 型へ変換しています。

```scala
col("unit_price").cast("long"),
col("quantity").cast("long"),
col("amount").cast("long")
```

### ClickHouse側

ClickHouseでは以下が `UInt32` です。

```text
transaction_id
unit_price
quantity
amount
```

型エラーが発生した場合は、Spark側のDataFrame schemaとClickHouse側のテーブル定義を比較してください。

Spark側：

```text
=== Merged Schema ===
```

ClickHouse側：

```sql
DESCRIBE TABLE data_platform.card_transactions;
```

---

## 12. 同じデータを複数回INSERTしてしまう

### 原因

現在のSpark処理はClickHouseへの保存に、

```scala
.mode("append")
```

を使用しています。

そのため、同じ入力データに対して、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を複数回実行すると、同じレコードが複数回INSERTされる可能性があります。

### 確認

```sql
SELECT count()
FROM data_platform.card_transactions;
```

同一の `transaction_id` が重複しているか確認する場合：

```sql
SELECT
    transaction_id,
    count()
FROM data_platform.card_transactions
GROUP BY transaction_id
HAVING count() > 1
ORDER BY transaction_id;
```

### データ生成時の注意

`card_transaction_generator.py` では、

```python
for transaction_id in range(1, count + 1):
```

としているため、生成処理を再実行すると、再び

```text
transaction_id = 1
transaction_id = 2
...
```

から生成されます。

そのため、同じテーブルへ追加INSERTするとIDが重複する可能性があります。

### 今後の改善ポイント

再実行を前提としたシステムにする場合は、以下を検討する必要があります。

* `transaction_id` の一意性
* 重複排除
* INSERT方式
* DELETE / TRUNCATE
* 冪等性
* MergeTreeのテーブル設計

---

## 13. 取引CSVが存在しない

### 症状

以下のようなエラーが発生します。

```text
[PATH_NOT_FOUND] Path does not exist
```

### 確認

```bash
ls -lh raw/card_transactions/
```

以下が存在することを確認します。

```text
transactions.csv
```

### 生成

存在しない場合はリポジトリのルートで、

```bash
python3 tools/card_transaction_generator.py
```

を実行します。

その後、

```bash
ls -lh raw/card_transactions/
```

で確認します。

---

## 14. 顧客マスターが存在しない

### 症状

Sparkが以下のファイルを読み込めません。

```text
raw/customer_master/customer.csv
```

### 確認

リポジトリのルートから、

```bash
ls -lh raw/customer_master/customer.csv
```

を実行します。

### 対処

顧客マスターが存在しない場合、取引データ生成処理もカード番号を取得できないため実行できません。

まず `customer.csv` が正しい場所に存在することを確認してください。

---

## 15. JOIN後のレコード数が期待より少ない

### 症状

Sparkで、

```text
Merged records: ...
```

と表示された件数が、取引CSVの件数より少なくなります。

### 原因

現在のJOINは、

```scala
.join(
  customerDF,
  Seq("card_number"),
  "inner"
)
```

というINNER JOINです。

そのため、取引データの `card_number` が顧客マスターに存在しない場合、その取引レコードはJOIN後のDataFrameに残りません。

### 確認

取引CSV：

```bash
head raw/card_transactions/transactions.csv
```

顧客マスター：

```bash
head raw/customer_master/customer.csv
```

それぞれの、

```text
card_number
```

を確認します。

---

## 16. `purchase_date` の変換エラー

### 処理内容

`purchase_date` は以下の形式からTimestampへ変換しています。

```text
yyyy-MM-dd HH:mm:ss
```

Sparkでは、

```scala
to_timestamp(
  col("purchase_date"),
  "yyyy-MM-dd HH:mm:ss"
)
```

を使用しています。

### 確認

入力CSVの値が以下の形式になっているか確認します。

```text
2026-09-03 08:02:53
```

異なる形式の場合、Timestampへの変換結果がNULLになる可能性があります。

---

## 17. ClickHouseへのINSERT後にデータを確認できない

### 確認手順

まずClickHouseが起動していることを確認します。

```bash
docker ps
```

次にClickHouseへ接続します。

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

データ件数を確認します。

```sql
SELECT count()
FROM card_transactions;
```

データを確認します。

```sql
SELECT *
FROM card_transactions
LIMIT 20;
```

---

## 18. トラブルシューティング時の基本確認コマンド

### 現在のディレクトリ

```bash
pwd
```

### Scalaファイル

```bash
find src -type f -name "*.scala" -print
```

### main class

```bash
sbt 'show Compile / discoveredMainClasses'
```

### build.sbt

```bash
cat -n build.sbt
```

### transaction CSV

```bash
ls -lh ../../raw/card_transactions/
```

### customer master

```bash
ls -lh ../../raw/customer_master/customer.csv
```

### ANTLR依存関係

```bash
sbt 'show Compile / dependencyClasspath' | grep -i antlr
```

### ClickHouseコンテナ

```bash
docker ps
```

### ClickHouseログ

```bash
docker logs data-platform-clickhouse
```

### ClickHouse HTTP接続

```bash
curl http://localhost:8123
```

---

## 19. 推奨する切り分け順序

エラーが発生した場合は、以下の順番で確認します。

### 1. 入力データ

```bash
ls -lh raw/customer_master/customer.csv
ls -lh raw/card_transactions/transactions.csv
```

### 2. ClickHouse

```bash
docker ps
```

### 3. ClickHouse HTTP

```bash
curl http://localhost:8123
```

### 4. Sparkプロジェクト

```bash
cd spark/sales-processing
```

### 5. コンパイル

```bash
sbt clean
sbt compile
```

### 6. main class

```bash
sbt 'show Compile / discoveredMainClasses'
```

### 7. Spark実行

```bash
sbt 'runMain com.example.SalesProcessor'
```

### 8. ClickHouse確認

```bash
docker exec -it data-platform-clickhouse clickhouse-client
```

```sql
SELECT count()
FROM data_platform.card_transactions;
```

---

## 20. 解決済みの主な問題

現在のコードでは、以下の問題への対処が反映されています。

| 問題                              | 対処                             |
| ------------------------------- | ------------------------------ |
| sbt `clean compile` の実行エラー      | `clean` と `compile` を分けて実行     |
| ClickHouse JDBC `classifier` 問題 | `classifier("all")` を使用しない     |
| main classの選択問題                 | `runMain` で実行対象を明示             |
| Sparkの相対パス問題                    | `../../raw/...` を使用            |
| ANTLRバージョン競合                    | `antlr4-runtime` を `4.9.3` に固定 |
| Spark hostname警告                | ローカル実行に影響しない場合は対応不要            |
| Native Hadoop Library警告         | ローカル実行に影響しない場合は対応不要            |

---

## 21. 重要なポイント

### ① Sparkは実行ディレクトリを基準に相対パスを解決する

Scalaファイルの場所ではなく、実行時の現在ディレクトリを基準に考えます。

```bash
pwd
```

で必ず確認してください。

### ② main classを明示する

複数のmain classが存在する場合は、

```bash
sbt 'runMain com.example.SalesProcessor'
```

を使用します。

### ③ Spark / Scalaの依存関係に注意する

Sparkでは複数のライブラリが依存関係として読み込まれるため、ANTLRのようにバージョン競合が発生する場合があります。

依存関係を確認する場合は、

```bash
sbt 'show Compile / dependencyClasspath'
```

を使用してください。

### ④ ClickHouseへのINSERT方式に注意する

現在は、

```scala
.mode("append")
```

でINSERTしています。

同じデータを再処理すると重複する可能性があるため、再実行・再処理を考慮した設計にする場合は冪等性や重複排除を検討する必要があります。
