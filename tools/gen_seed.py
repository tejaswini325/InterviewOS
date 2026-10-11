#!/usr/bin/env python3
"""Generates V2__seed_questions.sql from the curated bank below. The question bank lives here, in version control."""
import json, pathlib

E, M, H = "EASY", "MEDIUM", "HARD"
def mcq(code, topic, sub, d, stmt, opts, key, why, tags, secs=60):
    return dict(code=code, topic=topic, sub=sub, d=d, type="MCQ", stmt=stmt,
                options=[{"id": "ABCD"[i], "text": o} for i, o in enumerate(opts)], key=key, why=why,
                rubric=None, tests=None, tags=tags, secs=secs)
def txt(code, topic, sub, d, typ, stmt, concepts, tags, secs):
    return dict(code=code, topic=topic, sub=sub, d=d, type=typ, stmt=stmt, options=None, key=None, why=None,
                rubric=[dict(name=n, keywords=k, weight=w) for n, k, w in concepts], tests=None, tags=tags, secs=secs)
def code(code_, topic, sub, d, stmt, tests, tags, secs=600):
    return dict(code=code_, topic=topic, sub=sub, d=d, type="CODING", stmt=stmt, options=None, key=None, why=None,
                rubric=None, tests=tests, tags=tags, secs=secs)

Q = [
 # ---------------- JAVA ----------------
 mcq("JAVA-001","JAVA","Strings",E,"Given String a = new String(\"hi\"); String b = \"hi\"; what does (a == b) evaluate to?",
     ["true, both hold the same characters","false, == compares references and a is a new heap object","Compile error","Depends on the JVM vendor"],
     "B","The == operator compares references. new String(...) always allocates a new object, so it differs from the pooled literal.",["strings","equality"]),
 mcq("JAVA-002","JAVA","Collections",E,"Which collection guarantees insertion-order iteration and permits null keys?",
     ["HashMap","TreeMap","LinkedHashMap","ConcurrentHashMap"],"C","LinkedHashMap keeps a linked list of entries in insertion order and, like HashMap, allows one null key.",["collections","map"]),
 txt("JAVA-003","JAVA","Collections",M,"SHORT","Explain how HashMap uses hashCode() and equals() when storing and retrieving a key.",
     [("hashCode selects bucket",["hashcode","hash code"],1),("equals resolves collisions",["equals"],1),("bucket / collision handling",["bucket","collision","chain","tree"],1)],
     ["hashmap","hashcode"],180),
 mcq("JAVA-004","JAVA","Concurrency",M,"What does the volatile keyword guarantee for a field?",
     ["Atomic compound updates such as i++","Visibility of writes across threads and a happens-before edge","Mutual exclusion","Thread-local storage"],"B",
     "volatile gives visibility and ordering guarantees but does not make compound operations atomic.",["concurrency","jmm"]),
 txt("JAVA-005","JAVA","Memory",M,"LONG","Describe how garbage collection decides an object can be reclaimed, and name two generations or regions the heap is divided into.",
     [("reachability from GC roots",["reachab","gc root","root"],1),("generational heap",["young","old","eden","survivor","generation","region"],1),("mark phase / sweep or copy",["mark","sweep","copy","compact"],1),("stop-the-world or pauses",["stop-the-world","pause","stop the world"],1)],
     ["gc","jvm"],300),
 txt("JAVA-006","JAVA","Concurrency",H,"LONG","Compare synchronized blocks with java.util.concurrent.locks.ReentrantLock. When would you pick each?",
     [("intrinsic monitor / automatic release",["monitor","automatic","intrinsic","block scope"],1),("tryLock / timeout / interruptible",["trylock","timeout","interrupt"],1),("fairness option",["fair"],1),("condition variables",["condition","await","signal"],1),("must unlock in finally",["finally","unlock"],1)],
     ["concurrency","locks"],360),
 code("JAVA-007","JAVA","Strings",M,"Write a method that returns true if two strings are anagrams, ignoring case and spaces.",
      [dict(input='"Dormitory","Dirty room"',expected="true"),dict(input='"abc","abd"',expected="false")],["strings","coding"]),
 # ---------------- OOP ----------------
 mcq("OOP-001","OOP","Polymorphism",E,"Method overriding is an example of which kind of polymorphism?",
     ["Compile-time (static)","Runtime (dynamic)","Parametric only","None of the above"],"B","The method actually invoked is chosen at runtime from the object's concrete class.",["polymorphism"]),
 mcq("OOP-002","OOP","Abstraction",E,"Which statement about an abstract class is correct?",
     ["It can be instantiated directly","It can contain both abstract and concrete methods","It cannot have constructors","It cannot have fields"],"B","Abstract classes may mix concrete and abstract members and may declare constructors and fields.",["abstract"]),
 txt("OOP-003","OOP","Principles",M,"SHORT","What is the Liskov Substitution Principle? Give a violation example.",
     [("subtypes substitutable for base type",["substitut","replace"],1),("without breaking correctness",["correct","behavio","contract","break"],1),("example such as Square/Rectangle",["square","rectangle","example","throws"],1)],
     ["solid","lsp"],180),
 txt("OOP-004","OOP","Design",M,"LONG","Explain the difference between composition and inheritance and why 'favor composition over inheritance' is common advice.",
     [("is-a vs has-a",["is-a","has-a","is a","has a"],1),("tight coupling of inheritance",["coupl","fragile","base class"],1),("flexibility / runtime change of composition",["flexib","runtime","swap","delegat"],1),("reuse via delegation",["reuse","delegat"],1)],
     ["design","inheritance"],300),
 mcq("OOP-005","OOP","Encapsulation",M,"Why are fields usually declared private with accessor methods?",
     ["It makes the program faster","It allows invariants to be enforced and internals to change without breaking callers","Java requires it","It removes the need for constructors"],"B","Encapsulation hides representation and lets a class protect its invariants.",["encapsulation"]),
 txt("OOP-006","OOP","Patterns",H,"LONG","Describe the Strategy pattern, a situation where it fits, and how it relates to the Open/Closed Principle.",
     [("family of interchangeable algorithms",["interchangeable","family","algorithm"],1),("context delegates to strategy interface",["context","interface","delegate"],1),("add new strategy without modifying context",["open","closed","extend","without modif"],1),("example such as sorting or pricing",["sort","pricing","payment","discount","compress"],1)],
     ["patterns","solid"],300),
 # ---------------- DSA ----------------
 mcq("DSA-001","DSA","Complexity",E,"What is the average time complexity of lookup in a hash table?",["O(n)","O(log n)","O(1)","O(n log n)"],"C","With a good hash function and load factor, lookups are constant time on average.",["hashing","complexity"],45),
 mcq("DSA-002","DSA","Stacks",E,"Which data structure is the natural fit for checking balanced parentheses?",["Queue","Stack","Heap","Trie"],"B","A stack records the most recent unmatched opener, which is what a closer must match.",["stack"],45),
 mcq("DSA-003","DSA","Searching",M,"Binary search on a sorted array of n elements takes how many comparisons in the worst case?",["O(1)","O(log n)","O(n)","O(n log n)"],"B","Each comparison halves the remaining range.",["binary-search"],60),
 txt("DSA-004","DSA","Sorting",M,"SHORT","Compare quicksort and merge sort in terms of time complexity, space and stability.",
     [("quicksort average n log n, worst n^2",["n log n","nlogn","n^2","quadratic"],1),("merge sort n log n always",["merge"],1),("merge sort extra space / quicksort in place",["extra space","in-place","in place","o(n) space"],1),("stability",["stable","stability"],1)],
     ["sorting","complexity"],240),
 txt("DSA-005","DSA","Graphs",H,"LONG","Explain Dijkstra's algorithm, the data structure that makes it efficient, its time complexity and why negative edge weights break it.",
     [("greedy shortest path from source",["greedy","shortest","source"],1),("priority queue / min-heap",["priority queue","min-heap","heap"],1),("complexity (E+V) log V",["log v","e log","(e + v)","e+v"],1),("negative edges invalidate finalized distances",["negative"],1)],
     ["graphs","shortest-path"],360),
 code("DSA-006","DSA","Arrays",E,"Given an int array and a target, return the indices of two numbers that sum to target in O(n) time.",
      [dict(input="[2,7,11,15], 9",expected="[0,1]"),dict(input="[3,2,4], 6",expected="[1,2]")],["arrays","hashing","coding"]),
 code("DSA-007","DSA","Linked lists",M,"Reverse a singly linked list iteratively and return the new head.",
      [dict(input="1->2->3->null",expected="3->2->1->null"),dict(input="null",expected="null")],["linked-list","coding"]),
 mcq("DSA-008","DSA","Trees",M,"What is the in-order traversal of a binary search tree?",["Nodes in level order","Nodes in ascending key order","Nodes in descending key order","Nodes in insertion order"],"B","Left-root-right visits keys in sorted order for a BST.",["bst","traversal"],60),
 txt("DSA-009","DSA","Dynamic programming",H,"LONG","What are overlapping subproblems and optimal substructure? Explain how memoization versus tabulation applies them to the Fibonacci or knapsack problem.",
     [("overlapping subproblems",["overlapping"],1),("optimal substructure",["optimal substructure"],1),("memoization top-down",["memo","top-down","top down","cache"],1),("tabulation bottom-up",["tabulation","bottom-up","bottom up","table"],1)],
     ["dp"],360),
 # ---------------- DBMS ----------------
 mcq("DBMS-001","DBMS","Keys",E,"Which constraint uniquely identifies each row and cannot be NULL?",["Foreign key","Primary key","Check constraint","Index"],"B","A primary key is unique and not null.",["keys"],45),
 mcq("DBMS-002","DBMS","Transactions",E,"In ACID, what does the 'I' stand for?",["Integrity","Isolation","Indexing","Idempotency"],"B","Isolation: concurrent transactions do not see each other's partial effects.",["acid"],45),
 txt("DBMS-003","DBMS","Normalization",M,"SHORT","What is the difference between 2NF and 3NF?",
     [("2NF removes partial dependency on part of a composite key",["partial"],1),("3NF removes transitive dependency",["transitive"],1),("non-key attributes depend on the key",["non-key","non key","nonkey","candidate key","primary key"],1)],
     ["normalization"],200),
 mcq("DBMS-004","DBMS","Indexing",M,"A composite B-tree index on (a, b) can efficiently serve which query?",["WHERE b = 5","WHERE a = 5 AND b > 2","WHERE b > 2 ORDER BY a","None of these"],"B","The leftmost prefix column must be constrained for the index to be seekable.",["index","sql"],75),
 txt("DBMS-005","DBMS","Transactions",H,"LONG","Explain isolation levels and the anomalies they prevent: dirty read, non-repeatable read and phantom read.",
     [("dirty read",["dirty"],1),("non-repeatable read",["non-repeatable","nonrepeatable","non repeatable"],1),("phantom read",["phantom"],1),("read committed / repeatable read / serializable",["read committed","repeatable read","serializable"],1)],
     ["isolation","transactions"],360),
 code("DBMS-006","DBMS","SQL",M,"Write a SQL query returning the second highest distinct salary from table employees(id, salary), or NULL if none.",
      [dict(input="salaries 100,200,300",expected="200"),dict(input="salaries 100,100",expected="NULL")],["sql","coding"]),
 # ---------------- OS ----------------
 mcq("OS-001","OS","Processes",E,"Which is shared between threads of the same process?",["Stack","Registers","Heap memory","Program counter"],"C","Threads share the heap and globals but each has its own stack, registers and program counter.",["threads"],45),
 mcq("OS-002","OS","Scheduling",M,"Which scheduling algorithm is optimal for minimizing average waiting time when burst times are known?",["Round robin","FCFS","Shortest Job First","Priority with aging"],"C","SJF minimizes average waiting time for a given set of jobs (non-preemptive).",["scheduling"],60),
 txt("OS-003","OS","Deadlock",M,"SHORT","List the four Coffman conditions for deadlock and name one way to prevent it.",
     [("mutual exclusion",["mutual exclusion"],1),("hold and wait",["hold and wait","hold-and-wait"],1),("no preemption",["preempt"],1),("circular wait",["circular"],1)],
     ["deadlock"],200),
 txt("OS-004","OS","Memory",M,"LONG","Explain virtual memory, paging and what happens on a page fault.",
     [("virtual to physical mapping / page table",["page table","virtual address","mapping"],1),("fixed-size pages and frames",["frame","page"],1),("page fault loads page from disk",["disk","swap","fault"],1),("page replacement algorithm",["replace","lru","fifo"],1)],
     ["memory","paging"],300),
 mcq("OS-005","OS","Synchronization",M,"What is the key difference between a mutex and a binary semaphore?",["There is none","A mutex has ownership: only the locker should unlock it","A semaphore cannot be shared","A mutex can count above one"],"B","Mutexes are owned by the thread that locked them; semaphores are signalling mechanisms without ownership.",["sync"],60),
 txt("OS-006","OS","Scheduling",H,"LONG","Describe priority inversion and one technique to mitigate it.",
     [("low priority holds lock needed by high priority",["low","holds","lock"],1),("medium priority preempts low",["medium"],1),("priority inheritance",["inherit"],1),("priority ceiling alternative",["ceiling"],1)],
     ["scheduling","realtime"],300),
 # ---------------- CN ----------------
 mcq("CN-001","CN","Models",E,"At which OSI layer does IP operate?",["Data link","Network","Transport","Session"],"B","IP is a network-layer (layer 3) protocol.",["osi","ip"],45),
 mcq("CN-002","CN","Transport",E,"Which protocol provides reliable, ordered delivery?",["UDP","TCP","ICMP","ARP"],"B","TCP uses acknowledgements, retransmission and sequencing.",["tcp"],45),
 txt("CN-003","CN","Transport",M,"SHORT","Describe the TCP three-way handshake.",
     [("SYN",["syn"],1),("SYN-ACK",["syn-ack","syn ack","synack"],1),("ACK",["ack"],1)],["tcp","handshake"],150),
 txt("CN-004","CN","Application",M,"LONG","What happens, at the network level, from typing a URL in a browser to receiving the page over HTTPS?",
     [("DNS resolution",["dns"],1),("TCP connection",["tcp","handshake"],1),("TLS handshake",["tls","ssl","certificate"],1),("HTTP request and response",["http","request","response"],1)],
     ["http","dns","tls"],360),
 mcq("CN-005","CN","Application",M,"Which HTTP status code means the resource was created?",["200","201","204","301"],"B","201 Created is returned after a successful creation, often with a Location header.",["http"],45),
 txt("CN-006","CN","Congestion",H,"LONG","Explain how TCP congestion control works: slow start, congestion avoidance and what happens on packet loss.",
     [("slow start exponential growth",["slow start","exponential"],1),("congestion avoidance additive increase",["additive","avoidance","linear"],1),("loss halves window / multiplicative decrease",["multiplicative","halve","half","reduce"],1),("congestion window cwnd",["cwnd","window"],1)],
     ["tcp","congestion"],360),
]

def sql(s):
    return "'" + s.replace("'", "''") + "'"
def js(o):
    return sql(json.dumps(o, ensure_ascii=False, separators=(",", ":")))

rows, tags = [], []
for q in Q:
    rows.append("(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,1,TRUE)" % (
        sql(q["code"]), sql(q["topic"]), sql(q["sub"]), sql(q["d"]), sql(q["type"]), sql(q["stmt"]),
        js(q["options"]) if q["options"] else "NULL", sql(q["key"]) if q["key"] else "NULL",
        sql(q["why"]) if q["why"] else "NULL", js(q["rubric"]) if q["rubric"] else "NULL",
        js(q["tests"]) if q["tests"] else "NULL", q["secs"]))
    for t in q["tags"]:
        tags.append("((SELECT id FROM questions WHERE code=%s),%s)" % (sql(q["code"]), sql(t)))

out = ["-- GENERATED by tools/gen_seed.py - edit the generator, not this file.",
       "INSERT INTO questions (code, topic, subtopic, difficulty, type, statement, options, answer_key, explanation, rubric, test_cases, time_estimate_sec, version, active) VALUES"]
out.append(",\n".join(rows) + ";")
out.append("INSERT INTO question_tags (question_id, tag) VALUES")
out.append(",\n".join(tags) + ";")
p = pathlib.Path(__file__).resolve().parent.parent / "backend/src/main/resources/db/migration/V2__seed_questions.sql"
p.write_text("\n".join(out) + "\n", encoding="utf-8")
from collections import Counter
print(len(Q), "questions;", dict(Counter(q["topic"] for q in Q)), dict(Counter(q["type"] for q in Q)), dict(Counter(q["d"] for q in Q)))
