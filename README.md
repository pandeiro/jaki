Jaki 0.0.1
==========
*Browser-based [CouchDB](http://couchdb.apache.org) applications with [ClojureScript](http://github.com/clojure/clojurescript)*

Jaki's principal components are

* `src/jaki/req.cljs`: a request client that automatically converts Clojure and JavaScript datatypes
* `src/jaki/couch.cljs`: a basic library covering most of the CouchDB API using Clojure idioms
* `couchapp-template` & `script/jaki`: a template and script for generating new projects (to be used with the [couchapp](https://github.com/couchapp/couchapp) upload tool).

Usage
-----

Just include the `src/jaki` directory in your ClojureScript project, and reference it:

    (ns myapp.core
	  (:require [jaki.couch :as couch]))

    (couch/get-all-dbs (fn [dbs] (js/alert (apply str (interpose ", " dbs)))))
	
Couch CRUD
----------

Jaki abstracts CRUD operations to three main functions: `get-docs`, `post-docs`, and `delete-docs`.

### `get-docs`

At its simplest, Jaki guesses the current database (or taps a default database if set), and requests all documents:

    (get-docs (fn [resp] (js/alert (str (-> resp :rows count) " documents found!"))))

There's also some sugar for limiting the number of results:

    (get-docs 10 (fn [resp] 
	               (js/alert (apply str (map #(str %2 ". " (:id %1) "\n") (:rows resp) (iterate inc 1))))))

And there's sugar for specifying just the document(s) you want by id, like so:

    (get-docs ["_design/app" "_design/test"]
	          (fn [docs] (js/alert (str (count (map #(->> % :views keys) docs)) " total views found"))))

For more granular control, specify a view-map with a database and (optionally) design document, view, and options:

    (get-docs {:db "articles" :design "blog" :view "most-recent" :descending true :limit 10}
	          (fn [resp] (js/alert (->> resp :rows first :title))))

### `post-docs`

You can save a document (map) or vector of documents, with or without a callback, and with or 
without specifying the database:

    (post-docs {:_id "b9725ae4542ce6252937" :_rev "3-a2362326892374879692"} (fn [resp] (js/alert "Updated!")))

    (post-docs "albums" [{:title "St. Louis Blues" :album "Sunshine of my Soul" :recorded -68508000000}
	            {:title "Parisian Thoroughfare" :album "The Jaki Byard Experience" :recorded -40683600000}])

### `delete-docs`

Likewise, documents can be deleted in the same way they are posted:

    (delete-docs {:_id "b9725ae4542ce6252937" :_rev "3-a2362326892374879692"})
	
	(delete-docs "albums" [{:_id "ce672987ad32919732523b6" :_rev "2-ab4452cd382236274346}
	                       {:_id "ce672987ad32919732527f9" :_rev "1-f32353a25bc544574232}]
	             (fn [resp] (js/alert "Deleted!")))

There's also sugar for when you don't have the rev handy, in which case you can just use the
id string, though this has a performance penalty of an extra request behind-the-scenes to retrieve
the _rev:

    (delete-docs ["ce672987ad32919732523b6" "ce672987ad32919732527f9"])

Listening for _changes
----------------------

Jaki's _changes API has two functions, `listen` and `unlisten`.

### `listen`

Again, you can simply attach a function to the current/default database's changes feed like so:

    (listen (fn [resp] (js/alert (str (->> resp :results count) " new change(s)"))))
	
Or you can specify a database, and optionally, a filter:

    (listen {:db "albums" :filter "artists/pianists"} (fn [resp] (js/alert "New rag!?")))

And if you want to be able to unlisten this specific feed later, use an id when registering the listener:

    (listen "messages" {:db "chatroom"} (fn [resp] (js/alert "New message!")))
	
### `unlisten`

If no identifier is used, `unlisten` stops all listeners to the current/default/specified database's changes feed:

    (unlisten)
	
	(unlisten {:db "albums"})
	
Or use an id to specify which listener to stop:

    (unlisten "messages")
	
You can also register a callback, in case you want to update the DOM or something after unlistening:

    (unlisten (fn [] (js/alert "Not listening anymore")))

Replication
-----------

Jaki has a shortcut function that simply uses the [_replicator database](https://gist.github.com/832610) (CouchDB >1.0.2) for replication.

### `replicate`

Again, callback and database are optional (Jaki will use default or current database if source or target key are missing). The _id
field is also optional; Jaki will supply a new uuid if none is present:

    (replicate {:target "http://yourcouch.iriscouch.com/albums"})
	
	(replicate {:_id "continuous-album-rep" :source "http://yourcouch.iriscouch.com/albums" :continuous true}
	           (fn [resp] (js/alert "Replicating albums")))

Complete API
------------

For now, see `couch.cljs` for the complete API. Eventually I will add some auto-documentation.

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

Contributing
------------

This is my first real attempt at writing a library and using ClojureScript. I am very grateful to anyone who wants to use, test,
or contribute to this project. You can get in touch with me here on github or on freenode's `#clojure` and `#couchdb` channels.


License
-------

Copyright (C) 2011 Murphy McMahon

Distributed under the Eclipse Public License, the same as Clojure.
