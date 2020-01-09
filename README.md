# Code Challenge: Authorizer

An application that authorizes a transaction for a specific account following a set of predefined rules written in pure functional [Scala](https://www.scala-lang.org/).

## Architectural style and Code design choices

This project is bounded and heavily inspired by the hexagonal architecture -ports and adapters- and implementing 
[tagless final encoding](http://okmij.org/ftp/tagless-final/course/lecture.pdf) to achieved responsibility segregation,
inversion of control and emergent behavior; leading to maintainable, predictable, highly composable and orthogonal program.

In this project algebras are pure functions, with a single responsibility, that compose into repositories, services or validators and the last ones
compose into modules; modules compose into the main program. All the commitments in regards underlying effects, such as Streams or Futures,
are deferred to the main program allowing to constrain and inject dependency capabilities. 

As we can assume that parsing errors won't happen and the domain is quite anemic; the domain model contains only 
plain entities and values objects without any significant creation logic.

## Getting Started

Instructions to run the code on your local machine for development and testing purposes.

### Prerequisites

Please install [sbt: The interactive build tool](https://www.scala-sbt.org/) from [here](https://www.scala-sbt.org/download.html)
in order to build, compile, test and run the code:

For Linux (deb)
```
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```
For Linux (rpm)
```
curl https://bintray.com/sbt/rpm/rpm > bintray-sbt-rpm.repo
sudo mv bintray-sbt-rpm.repo /etc/yum.repos.d/
sudo yum install sbt
```

## Building

### From sources

Run `sbt stage`, this will create a launcher at `./console/target/universal/stage/bin/authorizer-console`.

### Docker image

Run `sbt docker:publishLocal`, this will pull `openjdk:jre-alpine` and build `authorizer-console:0.0.1` images.
You can check it out running `docker images`.

## Running

### From sbt

You can run the code directly from sbt-shell typing `sbt` and then `console/run`. 
This will start a streaming console where you can just paste operations as `json` and get the results right there.

```
{"account": {"active-card": true, "available-limit": 100}}
{"transaction": {"merchant": "Burger King", "amount": 20, "time": "2019-02-13T10:00:00.000Z"}}
{"transaction": {"merchant": "Habbib's", "amount": 90, "time": "2019-02-13T11:00:00.000Z"}}
{"account":{"active-card":true,"available-limit":100},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":["insufficient-limit"]}
```

You can gracefully shutdown the program entering `exit`.

Notice that empty or non-json inputs will be ignore, but malformed jsons or any other inputs that can lead to parsing errors will cause
an abruptly end of the program.

### From launcher

After running `sbt stage`, you can run the program as fallows:

```
./console/target/universal/stage/bin/authorizer-console < operations
```

Results will be prompt and the program will be automatically, gracefully shutdown at end-of-file (EOF).

```
{"account":{"active-card":true,"available-limit":100},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":["insufficient-limit"]}
Exiting...
```

### From a docker container

If you already created `authorizer-console:0.0.1` image using `sbt docker:publishLocal`, you can run it in the interactive mode:

```
cat operations | docker run -i authorizer-console:0.0.1
```

Results will be prompt and the container will be stopped at EOF.

```
{"account":{"active-card":true,"available-limit":100},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":[]}
{"account":{"active-card":true,"available-limit":80},"violations":["insufficient-limit"]}
Exiting...
```

## Running the tests

All testing data is generated by [ScalaCheck](https://www.scalacheck.org/), a library used for automated property-based testing of Scala or Java programs.

### Unit tests

Unit tests code is under `core/src/test`. To run them use `sbt test`.

### Integration tests

Integration tests code is under `console/src/it`. To run them use `sbt it`.

### Coverage report

Run `sbt coverageAgg` to generate an aggregated report for unit and integration tests.

The generated report will be at `target/scala-2.13/scoverage-report/index.html`

```
Statement coverage.: 98.30%
Branch coverage....: 100.00%
```

### Built With

This project is powered by the following libraries:

* [cats](https://typelevel.org/cats/): Lightweight, modular, and extensible library for functional programming
* [catsEffects](https://typelevel.org/cats-effect/): The IO monad for Scala
* [fs2](https://fs2.io/): Purely functional, effectful, resource-safe, concurrent streams for Scala
* [circe](https://circe.github.io/circe/): A JSON library for Scala powered by Cats
* [QuickLens](https://github.com/softwaremill/quicklens): Optics library to modify deeply nested case class fields
* [console](https://console4cats.profunktor.dev/): Effect-type agnostic Console I/O for Cats Effect
* [scalaCheck](https://www.scalacheck.org/): Property-based testing for Scala
* [scalaTest](http://www.scalatest.org/): Testing tool for Scala

## License

This project is licensed under the GNU License - see the [LICENSE](LICENSE) file for details.
