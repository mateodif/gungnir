(ns gungnir.query-test
  (:require
   [clojure.test :refer :all]
   [gungnir.query :as q]
   [gungnir.core :refer [changeset]]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def user-1-email "user@test.com")

(def user-1-password "123456")

(def user-1
  {:user/email user-1-email
   :user/password user-1-password})

(def user-2-email "user-2@test.com")

(def user-2-password "123456")

(def user-2
  {:user/email user-2-email
   :user/password user-2-password})

(def post-1-title "post-1 title")
(def post-1-content "post-1 content")

(def post-1
  {:post/title post-1-title
   :post/content post-1-content})

(def post-2-title "post-2 title")
(def post-2-content "post-2 content")

(def post-2
  {:post/title post-2-title
   :post/content post-2-content})

(def comment-1-content "comment-1 content")

(def comment-1
  {:comment/content comment-1-content})

(def comment-2-content "comment-2 content")

(def comment-2
  {:comment/content comment-2-content})

(def token-1
  {:token/type :token/verify})

(def token-2
  {:token/type :token/reset})

(deftest test-find!
  (let [{:user/keys [id]} (-> user-1 changeset q/insert!)]
    (testing "Find user by primary key"
      (is (= user-1-email (-> (q/find! :user id) :user/email))))

    (testing "Find user by primary key, automatic uuid translation"
      (is (= user-1-email (-> (q/find! :user (str id)) :user/email)))))

  (testing "Find unknown user by primary key returns nil"
    (is (nil? (-> (q/find! :user #uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))
    (is (nil? (-> (q/find! :user "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))))

(deftest test-find-by!
  (let [{:user/keys [id] :as user} (-> user-1 changeset q/insert!)
        _post (-> post-1 (assoc :post/user-id id) changeset q/insert!)]
    (testing "Find user by email"
      (is (= user-1-email (-> (q/find-by! :user/email user-1-email) :user/email))))

    (testing "Find user by unknown email returns nil"
      (is (nil? (q/find-by! :user/email "random@email.com"))))

    (testing "Find post by title and user-id"
      (is (= post-1-title
             (-> (q/find-by! :post/title post-1-title
                             :post/user-id id)
                 :post/title))))
    (testing "Find post by user-id with auto uuid"
      (is (= post-1-title
             (-> (q/find-by! :post/user-id (str id))
                 :post/title))))))

(deftest test-all!
  (let [{:user/keys [id] :as user} (-> user-1 changeset q/insert!)
        _post-1 (-> post-1 (assoc :post/user-id id) changeset q/insert!)
        _post-2 (-> post-2 (assoc :post/user-id id) changeset q/insert!)]
    (testing "Find posts by user id"
      (is (= #{post-1-title post-2-title}
             (-> (q/all! :post/user-id id) (->> (mapv :post/title)) set))))

    (testing "Find posts by user id with limit 1"
      (is (= 1
             (-> (q/limit 1)
                 (q/all! :post/user-id id)
                 (count)))))

    (testing "Find posts by user id and title"
      (is (= post-1-title
             (-> (q/limit 1)
                 (q/all! :post/user-id id
                         :post/title post-1-title)
                 first
                 :post/title))))))

(deftest test-insert!
  (testing "inserting a new user"
    (let [user (-> user-1 changeset q/insert!)]
      (is (nil? (:changeset/errors user)))
      (is (uuid? (:user/id user)))
      (is (some? (q/find! :user (:user/id user))))))

  (testing "inserting an invalid user"
    (let [user (-> user-1 (assoc :user/password "123") changeset q/insert!)]
      (is (some? (:changeset/errors user)))
      (is (nil? (:user/id user)))
      (is (nil? (q/find! :user (:user/id user)))))))

(deftest test-update!
  (let [user (-> user-1 changeset q/insert!)
        user-2 (-> user-2 changeset q/insert! (update :user/id str))]
    (testing "updating an existing user"
      (let [new-email "user-updated@test.com"
            new-user (q/update! (changeset user {:user/email new-email}))]
        (is (nil? (:changeset/errors new-user)))
        (is (uuid? (:user/id new-user)))
        (is (some? (q/find! :user (:user/id user))))))

    (testing "updating an existing user with str uuid"
      (let [new-email "user-updated-2@test.com"
            new-user (q/update! (changeset user-2 {:user/email new-email}))]
        (is (nil? (:changeset/errors new-user)))
        (is (uuid? (:user/id new-user)))
        (is (some? (q/find! :user (:user/id user))))))

    (testing "updating an invalid user"
      (let [new-user (q/update! (changeset user {:user/password "123"}))]
        (is (some? (:changeset/errors new-user)))
        (is (nil? (:user/id new-user)))
        (is (= user-1-password (:user/password (q/find! :user (:user/id user)))))))))

(deftest test-delete!
  (testing "deleting existing user"
    (let [user (-> user-1 changeset q/insert!)]
      (is (= true (q/delete! user)))
      (is (nil? (q/find! :user (:user/id user))))))

  (testing "deleting existing user with str uuid"
    (let [user (-> user-1 changeset q/insert! (update :user/id str))]
      (is (= true (q/delete! user)))
      (is (nil? (q/find! :user (:user/id user))))))

  (testing "deleting non existing user"
    (let [uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"]
      (is (= false (q/delete! {:user/id uuid})))
      (is (nil? (q/find! :user uuid))))))

(deftest test-relation-has-one
  (let [user (-> user-1 changeset q/insert!)
        token (-> token-1 (assoc :token/user-id (:user/id user)) changeset q/insert!)]
    (testing "user has one token"
      (is (= (:token/id token)
             (-> user
                 :user/token
                 (deref)
                 :token/id))))

    (testing "find! user has one token"
      (is (= (:token/id token)
             (-> (q/find! :user (:user/id user))
                 :user/token
                 (deref)
                 :token/id))))

    (testing "user has one token, back to user"
      (is (= (:user/id user)
             (-> user
                 :user/token
                 (deref)
                 :token/user
                 (deref)
                 :user/id))))

    (testing "find! user has one token, back to user"
      (is (= (:user/id user)
             (-> (q/find! :user (:user/id user))
                 :user/token
                 (deref)
                 :token/user
                 (deref)
                 :user/id))))))

(deftest test-relation-has-many
  (let [user (-> user-1 changeset q/insert!)
        post-1 (-> post-1 (assoc :post/user-id (:user/id user)) changeset q/insert!)
        post-2 (-> post-2 (assoc :post/user-id (:user/id user)) changeset q/insert!)
        comment-1 (-> comment-1 (assoc :comment/user-id (:user/id user)
                                       :comment/post-id (:post/id post-1))
                      changeset
                      q/insert!)
        comment-2 (-> comment-2 (assoc :comment/user-id (:user/id user)
                                       :comment/post-id (:post/id post-2))
                      changeset
                      q/insert!)]

    (testing "user has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> user
                 :user/posts
                 (deref)
                 (->> (map :post/id))
                 (set)))))

    (testing "find! user has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> (q/find! :user (:user/id user))
                 :user/posts
                 (deref)
                 (->> (map :post/id))
                 (set)))))

    (testing "all! posts have many comments"
      (is (= #{(:comment/id comment-1) (:comment/id comment-2)}
             (-> (q/all! :post)
                 (->> (map (comp deref :post/comments)))
                 (flatten)
                 (->> (map :comment/id))
                 (set)))))))

(deftest test-relation-belongs-to
  (let [user (-> user-1 changeset q/insert!)
        post (-> post-1 (assoc :post/user-id (:user/id user)) changeset q/insert!)
        comment (-> comment-1
                    (assoc :comment/user-id (:user/id user)
                           :comment/post-id (:post/id post))
                    changeset
                    q/insert!)]

    (testing "comment belongs to post"
      (is (= (:post/id post)
             (-> comment
                 :comment/post
                 (deref)
                 :post/id))))

    (testing "comment belongs to post, belongs to user"
      (is (= (:user/id user)
             (-> comment
                 :comment/post
                 (deref)
                 :post/user
                 (deref)
                 :user/id))))

    (testing "comment belongs to post, belongs to user, back to comment"
      (is (= (:comment/id comment)
             (-> comment
                 :comment/post
                 (deref)
                 :post/user
                 (deref)
                 :user/posts
                 (deref)
                 (first)
                 :post/comments
                 (deref)
                 (first)
                 :comment/id))))))

(deftest test-before-save)

(deftest test-before-read)

(deftest test-after-read)

