# Binding Tariff Classification

[![Build Status](https://travis-ci.org/hmrc/binding-tariff-classification.svg)](https://travis-ci.org/hmrc/binding-tariff-classification) [ ![Download](https://api.bintray.com/packages/hmrc/releases/binding-tariff-classification/images/download.svg) ](https://bintray.com/hmrc/releases/binding-tariff-classification/_latestVersion)

This is the Back End for the Binding Tariff Suite of applications e.g.

- [BTI Application Form](https://github.com/hmrc/binding-tariff-trader-frontend) on GOV.UK
- [BTI Operational Service](https://github.com/hmrc/tariff-classification-frontend) the service HMRC uses to assess BTI Applications

### Running

To run this service you will need:

1) A Local Mongo instance running
2) The [Bank Holidays](https://github.com/hmrc/bank-holidays) proxy service

    `sm --start BANK_HOLIDAYS -r`

##### Running with SBT

1) Run `sbt run` to run on port `9000` or instead run `sbt 'run 9580'` to run on a different port e.g. `9580`

Try `GET http://localhost:{port}/cases`

##### Running With Service Manager

This application runs on port 9580

1) Run `sm --start BINDING_TARIF_CLASSIFICATION`

Try `GET http://localhost:9580/cases`

### Testing

Run `./run_all_tests.sh`

or `sbt test it:test`

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
