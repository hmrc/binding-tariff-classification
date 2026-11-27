# binding-tariff-classification

The backend service which manages case data for the Advance Tariff Rulings services.

### Running

##### To run this Service you will need:

1) [Service Manager 2](https://github.com/hmrc/sm2) installed
2) [SBT](https://www.scala-sbt.org) Version `>=1.x` installed
3) [MongoDB](https://www.mongodb.com/) version `>=6.0` installed and running on port 27017
4) [Moto](https://github.com/getmoto/moto) (MotoServer) running on port 4566
5) An S3 bucket called `digital-tariffs-local` created in Moto

The easiest way to run MongoDB and Moto for local development is to use [Docker](https://docs.docker.com/get-docker/).

##### To run MongoDB

```
> docker run --restart unless-stopped -d -p 27017-27019:27017-27019 --name mongodb mongo:6.0
```

##### To run Moto and create the S3 bucket

```
> docker run -d --restart unless-stopped --name moto -p 4566:5000 motoserver/moto:5.1.18
> curl -X PUT http://localhost:4566/digital-tariffs-local
```

#### Starting the application:

Launch dependencies using `sm2 --start DIGITAL_TARIFFS`

If you want to run it locally:

- `sm2 --stop BINDING_TARIFF_CLASSIFICATION`
- `sbt run`

This application runs on port 9580.

### Testing

Run `./run_all_tests.sh`. This also performs coverage testing if all tests pass.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
