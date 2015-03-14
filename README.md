# Crawler as yet without a Name

The intent of this projet is to create a fully asyncronous, production
quality, customisable webcrawler in Clojure. Think nutch without XML
config files. Customisation should be the plugging in of your own
functions at key points, not a verbose DSL.

## Goals

* Web crawling should produce a stream of events (retrieved pages) to
  be dealt with as you like. The retrieved pages shouldn't be tied to
  the retrieval logic (think nutch's crawl then index workflow with
  everything encoded away in a hadoop filesystem; that's what I'm
  trying to avoid).
* If you want to dump your crawl results to a JSON file, you should be
  able to, but for non-trivial applications you need a way to deal
  with pages individually.
* There are many different uses for web crawlers: archiving, periodic
  scanning for indexing, continuous monitoring for alerts, etc.. These
  different behaviours should be acheivable by plugging policies
  (functions) into a common framework.
* High-level tools: authors of crawler policies shouldn't have to
  write logic to read cache headers, or handle robot policies unless
  they want to do something new / arcane.
* Orthogonality: e.g. you should be able to change a crawler's robots
  policy from one interpretation of the "standard" to another without
  changing anything else.

## Requirements

* Full robots.txt compliance (possibly with choice from multiple
  interpretations).
* Designed for distribution across many machines.
* Simple clojuresque configuration.
* Pluggable logic for different applications / styles of web crawling.

## Usage

FIXME

## Credit

Code and ideas have been taken from the following libraries. More or
less copied code is cited in the source files themselves.

* (Itsy) [https://gihub.com/dakrone/itsy]
* (Crawlista) [https://github.com/michaelklishin/crawlista]

## License

Copyright © 2015 CSTS Semion

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
