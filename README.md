# Binding Tariff Classification

[![Build Status](https://travis-ci.org/hmrc/binding-tariff-classification.svg)](https://travis-ci.org/hmrc/binding-tariff-classification) [ ![Download](https://api.bintray.com/packages/hmrc/releases/binding-tariff-classification/images/download.svg) ](https://bintray.com/hmrc/releases/binding-tariff-classification/_latestVersion)

This is the Back End for the Binding Tariff Suite of applications e.g.

- [BTI Application Form](https://github.com/hmrc/tariff-classification-frontend) on GOV.UK
- [BTI Operational Service](https://github.com/hmrc/binding-tariff-trader-frontend) the service HMRC uses to assess BTI Applications

### Running

To run this service you will need:

1) A Local Mongo instance running
2) Run `sbt run` to run on port `9000` or instead run `sbt 'run 9090'` to run on a different port e.g. `9090`

Try `GET http://localhost:9000/cases`

Service Manager Set Up TBC

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")