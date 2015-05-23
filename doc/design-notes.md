## API

Below, a page is a map with keys [status header body] and represents
the result of an http request.

### Robots

* (crawlable? agent url) - Is it permissible to crawl this page at
  all?
* (crawl-delay agent url) - Crawl delay (in seconds)
* (meta-index? agent page) - Is it okay to index this page?
* (meta-follow? agent page) - Is it okay to enqueue the links in this
  page?

### Crawling

* (acceptable? url) - Might we ever want to crawl this url?
* (crawl? url) - Do we want to crawl this url right now?
* (get-links body) - Return links from page that should be crawled in
  the future
* (crawled! page) - Notifies the callee that the page has been crawled.

### Sitemap

I'd like to be able to read a sitemap and use that to crawl a
site. This should be toggleable. I don't see customisability bringing
much value.


## Dataflow

urls -> filter -> split into one queue per domain.

Start up one go-loop per domain which pulls every crawl-delay and
crawls the first URL to pass the filter.

This could be a lot of go-loops. Is that a problem? It seems like a
go-loop is small overhead compared to the urls that will be enqueued
for a domain.

The idea eventually is to have the crawlers communicate and once a
crawler has too many domains it will query around for another crawler
that either wants that domain or is willing to take a new one and then
start passing off to them. I.E. each crawler is intended to work on a
bounded number of domains.

How about a domain with too many pages for one crawler?

An interesting question is how do we split a queue full of urls into
one queue per domain in a fashion that 1) doesn't block fast domains
waiting for slow ones, and 2) doesn't unnecessarily drop URLs into the
aether.

The best way I can think of as of yet is to have a bounded blocking
queue for urls which then split by a transducer into N dropping
queues (one per domain). This passes #1, but slow domains will drop
urls despite there being leeway which could be stolen from faster
domains.

I don't want to write my own buffer just yet. Let them drop initially.
