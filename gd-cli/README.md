# gd-cli

Collection of handy functions for interaction with GoodData API intended to be used from REPL. The single most useful function is ```setup-project``` which can be used to create "complete msf project" containing some graph, ruby and DATALOAD process as well as related schedules.

## Installation

You will need leiningen.

## Usage
       
 Start REPL in project directory:

```lein repl```

Run setup-project:

```(def project (setup-project "secure.gooddata.com" "user@gooddata.com" "password"))```

You can create your own GoodData instance for playing:

```(def gd (GoodData. "secure.gooddata.com" "user@gooddata.com" "password"))```

