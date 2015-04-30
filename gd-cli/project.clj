(defproject gd-cli "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;; TODO: use stable version once released - https://github.com/martiner/gooddata-java/pull/151
                 [cz.geek/gooddata-java "0.13.1-SNAPSHOT"]
                 [cheshire "5.4.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [clj-http "1.1.1"]]
  :main ^:skip-aot gd-cli.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
