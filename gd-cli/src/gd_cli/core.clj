(ns gd-cli.core
  (:gen-class)
  (:require [cheshire.core :as ch]
            [clj-http.client :as client]
            [clojure.java.shell :as csh])
  (:import (com.gooddata GoodData)
           (com.gooddata.dataload.processes Schedule DataloadProcess))
  )

;;; General methods
;;;
(defn show-class-methods [class] (doseq [m (.getMethods class)] (println m)))

(defn show-methods [object] (show-class-methods (.getClass object)))

(defn find-methods [my-class method-pattern]
  "Find all methods on given class matching given pattern (case insensitive matching).
   Parameter types (if any) are show as well."
  (for [meth (.getMethods my-class)
      :let [name (.getName meth)]
      :when (re-find (re-pattern (str "(?i)" method-pattern)) name)]
    [name
     (map #(.getName %) (.getParameterTypes meth)) ] )
  )


;;; GoodData stuff
;;; - you can define GoodData client in your repl as follows
;(def gd (GoodData. "secure.gooddata.com" "user@gooddata.com" "password")

(defn get-project [gd project-id]
  (.getProjectById (.getProjectService gd) project-id))

(defn delete-project [gd project-id]
  (.removeProject (.getProjectService gd) (get-project gd project-id)))

(defn parse-project-id [dl-tool-output]
  (->> (re-matcher #"project=/gdc/projects/([a-zA-Z0-9]+)" dl-tool-output)
       (re-find)
       (second)))

;; TODO: consider using macro to avoid code repetition in client-post and client-put

(defn client-post [host user password relative-uri body]
  (let [full-uri (str "https://" host relative-uri)]
    (client/post  full-uri  {:basic-auth [user password] :body body  :content-type :json :accept "application/json;version=1"})
    )
  )

(defn client-put [host user password relative-uri body]
  (let [full-uri (str "https://" host relative-uri)]
    (client/put  full-uri  {:basic-auth [user password] :body body  :content-type :json :accept "application/json;version=1"})
    )
  )

(defn set-output-stage-metadata [host user password project-id]
  (let [output-stage-uri (str  "/gdc/dataload/projects/" project-id "/outputStage/metadata")
        output-stage-metadata "{ \"outputStageMetadata\" : { \"tableMeta\" : [ { \"tableMetadata\" : { \"table\" : \"country\", \"defaultSource\" : \"facebook\", \"columnMeta\" : [ ] } }] }}"]
    (client-put host user password output-stage-uri output-stage-metadata)
    )

  )
(defn setup-dl-project [host user password]
  "Creates new project with complete setup for DATALOAD process via dl-tool.
   Returns project ID."
  (println "Creating new project...")
  (let [project-id  (->>   (csh/sh "sh" "-c" (str "./dl-tool" " -h " host " -u " user " -p " password " -pt PROJECT_TITLE")  :dir "dl-tool")
         (:out)
         (parse-project-id))]
    (println (str "Project " project-id " created"))
    (set-output-stage-metadata host user password project-id)
    project-id
    ))

(defn find-dl-process [gd project]
  "Finds process of type DATALOAD in given project if any is present."
  (first
   (filter (fn [process] (= "DATALOAD" (.getType process)))
    (.listProcesses (.getProcessService gd) project)))
)

(defn create-schedule
  ([gd project process executable custom-params]
   (let [schedule (Schedule. process executable "0 0 * * *")]
     (doseq [[k v] custom-params]
       (.addParam schedule k v))
    (.createSchedule (.getProcessService gd) project schedule)))
)



(defn create-dataload-schedule [gd project]
    (let [dataload-params {"GDC_DATALOAD_DATASETS" "[{\"dataset\":\"dataset.invoice\",\"uploadMode\":\"FULL\"},{\"dataset\":\"dataset.invoiceitem\",\"uploadMode\":\"FULL\"},{\"dataset\":\"dataset.customer\",\"uploadMode\":\"FULL\"},{\"dataset\":\"dataset.product\",\"uploadMode\":\"FULL\"},{\"dataset\":\"dataset.productrelease\",\"uploadMode\":\"FULL\"},{\"dataset\":\"dataset.country\",\"uploadMode\":\"FULL\"}]"}
          schedule (create-schedule gd project (find-dl-process gd project) nil dataload-params) ]
      schedule
    )
  )

(defn create-process [gd project
                      process-name process-type process-data-file executable]
  (let [new-process  (DataloadProcess. process-name process-type)
        process-service (.getProcessService gd)
        created-process (.createProcess process-service  project new-process (clojure.java.io/file process-data-file))]
        (create-schedule gd project created-process executable {})
        created-process
    ))

(defn create-graph-process [gd project]
  (create-process gd project
                  "simple-graph-process" "GRAPH" "data/simple-clover-graph.zip" "simpleCloverGraph.grf"))

(defn create-ruby-process [gd project]
    (create-process gd project
                    "ruby script" "RUBY" "data/ruby-script.zip" "r_script_test.rb"))

(defn execute-schedule [host user password schedule]
  "Executes single schedule.
   Notice that the schedule execution is trigerred but no explicit blocking is involved.
   That means the schedule execution can still be in progress by the time this function is completed."
  (let [schedule-uri (.getUri schedule)]
      (println (str "Executing schedule: " schedule-uri) )
      (client-post host user password
                   (str schedule-uri "/executions")
                   "{\"execution\": {}}")
      (println "Schedule executed."))
  )

(defn execute-schedules [gd host user password project]
     (doseq [schedule (.listSchedules (.getProcessService gd) project)]
        (execute-schedule host user password schedule) )
     )

(defn create-eventstore [host user password project-id]
  (println "Creating evenstore...")
  (let [evenstores-uri (str "/gdc/projects/" project-id "/dataload/eventstore/stores")
        eventstore-data "{\"store\":{\"storeId\":\"testId\"}}"]
    (client-post host user password evenstores-uri eventstore-data))
  (println "Evenstore created."))


(defn create-metadata [host user password project-id]
  (println "Creating metadata...")
  (let [metadata-uri (str "/gdc/projects/" project-id "/dataload/metadata")
        metadata "{ \"metadataItem\" : { \"key\" : \"metadata-key\", \"value\" : \"metadata-value\"}}"]
    (client-post host user password metadata-uri metadata))
  (println "Metadata created."))


(defn create-facebook-token [host user password project-id]
  (println "Creating facebook token...")
  (let [tokens-uri (str "/gdc/projects/" project-id "/dataload/download/facebook/tokens")
        facebook-token "{ \"facebookToken\" : { \"email\" : \"user@gooddata.com\", \"label\" : \"testing-token", \"applicationId\" : \"123\", \"applicationSecret\" : \"abc\", \"notificationsEnabled\" : false, \"scope\" : [ ] } }"]
    (client-post host user password tokens-uri facebook-token))
  (println "Facebook token created."))


(defn setup-project [host user password]
  "Creates new project with MSF stuff like Graph process, Ruby script process, DATALOAD process and associated schedules.
   Returns project ID of new project."
  (let [gd (GoodData. host user password)
        project-id  (setup-dl-project host user password)
        project (.. gd getProjectService (getProjectById project-id))]
    (println "Creating new processes and schedules...")
    (create-dataload-schedule gd project)
    (create-graph-process gd project)
    (create-ruby-process gd project)
    (println "Processes and schedules created.")
    (println "Executing schedules...")
    (execute-schedules gd host user password project)
    (println "Schedules executed.")

    (create-eventstore host user password project-id)
    (create-metadata host user password project-id)
    (create-facebook-token host user password project-id)

    project-id
    ))
