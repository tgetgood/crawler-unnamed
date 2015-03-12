# Crawler as yet without a Name

The intent of this projet is to create a fully asyncronous,
customisable webcrawler. Think nutch without XML config
files. ustomisation should be the plugging in of your own functions at
key points, not a verbose DSL.

## Requirements

* Full robots.txt compliance (possibly with choice from multiple
  interpretations).
* Designed for distribution across many machines.
* Crash-only software design.
* Logical sandboxing of domains being crawled so as to
  1. Simplify robots.txt compliance.
  2. Give a meaningful answer to "How up to date is our model of
     example.com?"
* Pluggable logic for:
  1. Filtering urls to crawl.
  2. Gathering links out of html.
  3. Handling scraped pages. 

## Usage

FIXME

## Credit

This project will make use of code from a number of open source
projects, sometimes copying the code rather than importing it along
with a lot of other unneeded functionality.

List:

* (Itsy)[https://gihub.com/dakrone/itsy]
* (Crawlista) [https://github.com/michaelklishin/crawlista]

## License

Copyright © 2015 CSTS Semion

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
