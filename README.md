Jaki 0.1.0
==========
*Browser-based [CouchDB](http://couchdb.apache.org) applications with [ClojureScript](http://github.com/clojure/clojurescript)*

Jaki's principal components are

* `src/jaki/req.cljs`: a request client that automatically converts Clojure and JavaScript datatypes
* `src/jaki/couch.cljs`: a basic library covering most of the CouchDB API using Clojure idioms
* `couchapp-template` & `script/jaki`: a template and script for generating new projects (to be used with the [couchapp](https://github.com/couchapp/couchapp) upload tool).

Usage
-----

With leiningen:

```clojure
[jaki "0.1.0"]
```

...or just copy the `src/jaki` directory to your ClojureScript project.

Use or require the library's namespace:

```clojure
(ns myapp.core
  (:require [jaki.couch :as couch]))

(couch/all-dbs (fn [dbs] (js/alert (apply str (interpose ", " dbs)))))
```

Setup
-----

Jaki will automatically try to use the URL path to determine the current database, although
you can always specify the database in each request as well.

You also have the option of setting a default database to use:

```clojure
(couch/set-default-db "jazz")
(couch/get-docs (fn [resp] (js/alert (str (count (:rows resp)) " docs found in 'jazz' database"))))
```

If you are able to do cross-domain XHR requests, as with browser extensions, you can set the host like so:

```clojure
(couch/set-host! "http://username.iriscouch.com")
```

If your endpoint is not at the root of the URL path, you can set a prefix like so:
```clojure
(couch/set-url-prefix "myapp")
```

Couch CRUD
----------

Jaki abstracts CRUD operations to three main functions: `get-docs`, `post-docs`, and `delete-docs`.

### `get-docs`

At its simplest, Jaki guesses the current database (or taps a default database if set), and requests all documents (with include_docs=true):

```clojure
(get-docs (fn [resp] (js/alert (str (-> resp :rows count) " documents found!"))))
```

There's also some sugar for limiting the number of results (also implies include_docs=true):

```clojure
(get-docs 10 (fn [resp] 
               (js/alert (apply str (map #(str %2 ". " (:id %1) "\n") (:rows resp) (iterate inc 1))))))
```

And there's sugar for specifying just the document(s) you want by id, like so (also implies include_docs=true):

```clojure
(get-docs ["_design/app" "_design/test"]
          (fn [docs] (js/alert (str (count (map #(-> % :views keys) docs)) " total views found"))))
```

For more granular control, specify a view-map with a database and/or design document, view, and options (no implict include_docs=true):

```clojure
(get-docs {:db "articles" :design "blog" :view "most-recent" :descending true :include_docs true :limit 10}
          (fn [resp] (js/alert (-> resp :rows first :doc :title))))
```

### `post-docs`

You can save a document (map) or vector of documents, with or without a callback, and with or 
without specifying the database:

```clojure
(post-docs {:_id "b9725ae4542ce6252937" :_rev "3-a2362326892374879692"} (fn [resp] (js/alert "Updated!")))

(post-docs "albums" [{:title "St. Louis Blues" :album "Sunshine of my Soul" :recorded -68508000000}
                     {:title "Parisian Thoroughfare" :album "The Jaki Byard Experience" :recorded -40683600000}])
```

### `delete-docs`

Likewise, documents can be deleted in the same way they are posted:

```clojure
(delete-docs {:_id "b9725ae4542ce6252937" :_rev "3-a2362326892374879692"})
	
(delete-docs "albums" [{:_id "ce672987ad32919732523b6" :_rev "2-ab4452cd382236274346"}
                       {:_id "ce672987ad32919732527f9" :_rev "1-f32353a25bc544574232"}]
             (fn [resp] (js/alert "Deleted!")))
```

There's also sugar for when you don't have the rev handy, in which case you can just use the
id string, though this has a performance penalty of an extra request behind-the-scenes to retrieve
the _rev:

```clojure
(delete-docs ["ce672987ad32919732523b6" "ce672987ad32919732527f9"])
```

App Generator Script
--------------------

To generate an application skeleton, edit `jaki/script` to use the appropriate template location, make sure it's executable,
place the script somewhere in your `$PATH`, and run it:

    jaki new myapp

This will create a new subdirectory `myapp` in the current directory, containing `app` and `src` subdirectories. Inside `app`
will be a basic couchapp structure with an `index.html` that already contains a reference to your app and the entrypoint invocation
`myapp.start()`.

From here, simply specify authentication credentials and target database in `.couchapprc` and compile the `src` subdirectory
and you are ready to push your couchapp.


API Todo
--------

- Listening for _changes

- Replication

Contributing
------------

This is my first real attempt at writing a library and using ClojureScript. I am very grateful to anyone who wants to use, test,
or contribute to this project. You can get in touch with me here on github or on freenode's `#clojure` and `#couchdb` channels.


License
-------

Copyright (C) 2011 Murphy McMahon

Distributed under the Eclipse Public License, the same as Clojure.
