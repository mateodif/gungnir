(ns gungnir.factory-test
  (:require
   [gungnir.test.util.database :refer [datasource-opts-2]]
   [clojure.test :refer :all]
   [gungnir.query :as q]
   [gungnir.database :refer [*database*]]
   [gungnir.factory]
   [gungnir.test.util :as util]
   [gungnir.changeset :refer [changeset]]
   [gungnir.test.util.migrations :as migrations]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def user-1-email "user@test.com")

(def user-1-password "123456")

(def user-1
  {:user/email user-1-email
   :user/password user-1-password})

(deftest local-datasource
  (let [{:keys [datasource find!-fn save!-fn]} (gungnir.factory/make-datasource-map! datasource-opts-2)]
    (migrations/init! datasource)
    (testing "creating datasource map"
      (is (not= datasource *database*) )
      (is (instance? javax.sql.DataSource datasource))
      (is (instance? javax.sql.DataSource *database*)))

    (testing "create user with local datasource map"
      (let [user (-> user-1 changeset save!-fn)]
        (is (nil? (:changeset/errors user)))
        (is (uuid? (:user/id user)))
        (is (some? (find!-fn :user (:user/id user))))
        ;; Should not be findable in global datasource
        (is (nil? (q/find! :user (:user/id user))))))))
