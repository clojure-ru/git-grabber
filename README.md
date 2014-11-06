# git-grabber

get clojure repositories from github with uses github API

## Installation

_add in project.clj_

1. plugin *[lein-environ "1.0.0"]*
2. *:env {:token [%token-string%]}*

## Usage

FIXME: explanation

    $ java -jar git-grabber-0.1.0-standalone.jar

## Options

## Examples

_in repl_

```clojure
(require 'git-grabber.core)
(git-grabber.core/search) ;; search best matches for clojure lang
```

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
