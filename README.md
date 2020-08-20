ER-Spark
---

EntityResolution run on SPARK

## Build
mvn clean package

or 

sbt clean package


## Test

sbt test

sbt "testOnly *DataTypeR*"

## Put it into local repo
sbt compile package publishLocal

or

mvn clean install
## Compatibility Setting

JDK : 1.8_251

Scala: 2.11.8

Spark: 2.0.2

sbt: 0.13

## Setup before run the program

Spark

# How-to

## Build
sbt clean package

## test
sbt clean test

Or using maven is also support :-P