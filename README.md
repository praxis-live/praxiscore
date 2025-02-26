# PraxisCORE v6

PraxisCORE is a modular JVM runtime for cyberphysical programming, supporting
real-time coding of real-time systems. It is the heart of [PraxisLIVE][praxislive].
With a distributed forest-of-actors architecture, runtime code changes and
comprehensive introspection, PraxisCORE brings aspects of Erlang, Smalltalk and
Extempore into the Java world ... a powerful platform for media processing, data
visualisation, sensors, robotics, IoT, and lots more!

For further information, help and support see https://www.praxislive.org and
https://www.praxislive.org/core/

PraxisCORE is an open-source project originally developed by
[Neil C Smith][neilcsmith], and supported by [Codelerity Ltd][codelerity].

## Requirements and build

PraxisCORE v6 requires Java 21 or above.

The build uses Maven via the Maven Wrapper. To build, execute `mvnw package`. The
fully built runtime will be found at `praxiscore-bin/target/praxiscore` and
`praxiscore-bin/target/praxiscore-bin-VERSION-bin.zip`.

To run the test suite, execute
`./praxiscore-bin/target/praxiscore/bin/praxis -f ./testsuite/`.

## License

PraxisCORE is licensed under the terms of the GNU Lesser General Public License v3.
This means that the core runtime, or a selection of its modules, may be used as a
library and included in your own projects without requiring you to share your own
code under the same license.

Some modules have different but compatible licenses.

[neilcsmith]: https://www.neilcsmith.net
[codelerity]: https://www.codelerity.com
[praxislive]: https://www.praxislive.org
