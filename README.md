# Javac tool & Clojure AOT tool

1. Auto compile changed java files
2. AOT clojure file at first time to speed up bootstrap
3. if your want AOT force, just remove the target directory

## Installation

```clojure
{:aliases
 {:compile
  {:main-opts ["-m" "tools.deps.task.compile"]
              :extra-deps
              {tools.deps.task.compile
               {:git/url "https://github.com/gfZeng/tools.deps.task.compile.git"
                         :sha     "a2b2ea7456534a9c7da1b69cbf7112c2ac20a8c2"}}}}}
```

## Run

```sh
clj -A:compile -m app.ns
```
