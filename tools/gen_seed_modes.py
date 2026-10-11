#!/usr/bin/env python3
"""Generates V4__seed_mode_questions.sql: HR, project-based and AI/ML interview questions with predefined follow-up rules.
follow_ups maps an EXPECTED CONCEPT NAME (exactly as in the rubric) to the question the rule-based interviewer asks when the answer missed it."""
import json, pathlib
E, M, H = "EASY", "MEDIUM", "HARD"

def mcq(code, topic, sub, d, stmt, opts, key, why, tags, secs=60):
    return dict(code=code, topic=topic, sub=sub, d=d, type="MCQ", stmt=stmt, options=[{"id": "ABCD"[i], "text": o} for i, o in enumerate(opts)],
                key=key, why=why, rubric=None, fu=None, tags=tags, secs=secs)
def open_(code, topic, sub, d, typ, stmt, concepts, fu, tags, secs):
    names = [c[0] for c in concepts]
    assert all(k in names for k in fu), (code, "follow-up key must match a concept name")
    return dict(code=code, topic=topic, sub=sub, d=d, type=typ, stmt=stmt, options=None, key=None, why=None,
                rubric=[dict(name=n, keywords=k, weight=w) for n, k, w in concepts], fu=fu, tags=tags, secs=secs)

Q = [
 # ---------------- HR / behavioural ----------------
 open_("HR-001","HR","Introduction",E,"LONG","Tell me about yourself and why you are interested in this role.",
   [("Relevant background",["background","studied","degree","experience","worked","graduat"],1),("Skills and projects",["skill","technolog","built","project","learned"],1),
    ("Motivation for the role",["interested","motivat","passion","excited","because i"],1),("Future goals",["goal","grow","future","looking to","aim"],1)],
   {"Motivation for the role":"What specifically about this role made you apply?","Skills and projects":"Which project best shows the skills this role needs?"},["behavioural","intro"],240),
 open_("HR-002","HR","Teamwork",M,"LONG","Describe a time you disagreed with a teammate. What happened and how was it resolved?",
   [("Situation",["situation","when we","project","team","was working"],1),("Your responsibility or goal",["responsib","goal","task","needed to","my role"],1),
    ("Actions you took",["i decided","i talked","i proposed","i spoke","i listened","i suggested","i organized","i asked"],1),("Result",["result","outcome","as a result","resolved","delivered","improved"],1)],
   {"Actions you took":"What exactly did you do to resolve it, personally?","Result":"How did it end, and what changed afterwards?"},["behavioural","star","conflict"],300),
 open_("HR-003","HR","Failure",M,"LONG","Tell me about a mistake you made at work, study or a project and what you did about it.",
   [("The mistake",["mistake","failed","wrong","error","missed"],1),("Taking ownership",["i took","my responsibility","i should","i owned","accountab","my fault"],1),
    ("How it was fixed",["fixed","corrected","recover","rolled back","resolved","repaired"],1),("Lesson learned",["learned","lesson","now i","since then","process","changed how"],1)],
   {"Taking ownership":"What was your own part in it?","Lesson learned":"What do you do differently now because of it?"},["behavioural","failure"],300),
 open_("HR-004","HR","Self-awareness",E,"SHORT","What are your strengths and one weakness you are working on?",
   [("A strength with an example",["strength","good at","for example","for instance","example"],1),("An honest weakness",["weakness","struggle","used to","tend to","not good"],1),
    ("Steps to improve",["working on","course","practice","feedback","plan","improve"],1)],
   {"Steps to improve":"What concrete step have you taken to improve it?","A strength with an example":"Can you give an example where that strength made a difference?"},["behavioural"],180),
 open_("HR-005","HR","Pressure",M,"LONG","Tell me about a time you had to deliver under a tight deadline.",
   [("Prioritisation",["priorit","most important","scope","trade","cut"],1),("Communication",["communicat","informed","told","stakeholder","manager","team"],1),
    ("What you did",["worked","focused","delegat","plan","split","automated"],1),("Outcome",["deliver","met the deadline","result","shipped","outcome","on time"],1)],
   {"Prioritisation":"How did you decide what to leave out?","Communication":"Who did you keep informed, and how?"},["behavioural","deadline"],300),
 open_("HR-006","HR","Goals",E,"SHORT","Where do you want to be in five years, and how does this role help?",
   [("Growth intent",["grow","learn","skill","senior","lead","develop"],1),("Fit with the role or company",["company","role","team","contribute","value","opportunit"],1),
    ("A realistic path",["step","plan","path","first","then","by"],1)],
   {"A realistic path":"What would the first year of that path look like?","Fit with the role or company":"How would you contribute to the team along the way?"},["behavioural","goals"],180),
 # ---------------- Project-based ----------------
 open_("PRJ-001","PROJECT","Overview",M,"LONG","Describe a project you are proud of: the problem it solved, your role and the outcome.",
   [("The problem",["problem","goal","needed","users","pain"],1),("Your role",["i built","i designed","i implemented","my role","i was responsible","i developed"],1),
    ("Technology used",["stack","framework","database","api","react","spring","python","node","java"],1),("Outcome or impact",["result","outcome","deployed","users","impact","improved","launched"],1)],
   {"Your role":"Which part did you personally build, as opposed to your teammates?","Outcome or impact":"How did you know the project worked? Any numbers or feedback?"},["project","overview"],360),
 open_("PRJ-002","PROJECT","Design decisions",H,"LONG","Walk me through the architecture of a project and one design trade-off you made.",
   [("Components and layers",["architecture","component","service","layer","module","frontend","backend"],1),("A trade-off and the alternative",["trade-off","tradeoff","alternative","instead","chose","because"],1),
    ("Data storage",["database","schema","cache","storage","table","index"],1),("Scalability or performance",["scale","load","performance","bottleneck","latency","concurrent"],1)],
   {"A trade-off and the alternative":"What alternative did you reject, and why?","Scalability or performance":"What would break first if usage grew tenfold?"},["project","architecture"],420),
 open_("PRJ-003","PROJECT","Quality",M,"LONG","How did you test and maintain the quality of one of your projects?",
   [("Kinds of tests",["unit test","integration","test","coverage","e2e"],1),("Automation and CI",["ci","pipeline","automated","github actions","jenkins"],1),
    ("Debugging and monitoring",["bug","debug","logging","monitor","trace"],1),("Code review or standards",["review","lint","standard","pull request","format"],1)],
   {"Kinds of tests":"What did you choose to test, and what did you leave untested?","Debugging and monitoring":"Tell me about the hardest bug you found and how you found it."},["project","testing"],300),
 open_("PRJ-004","PROJECT","Challenges",M,"LONG","What was the hardest technical challenge in a project and how did you solve it?",
   [("The challenge",["challenge","problem","issue","difficult","hard","stuck"],1),("How you investigated",["investigat","profil","logs","debug","reproduce","research","read the"],1),
    ("The solution",["solution","fixed","implemented","approach","resolved","solved"],1),("What you learned",["learned","next time","would","lesson"],1)],
   {"How you investigated":"What did you try first, and what did that tell you?","What you learned":"What would you do differently next time?"},["project","debugging"],300),
 open_("PRJ-005","PROJECT","Reflection",E,"SHORT","If you rebuilt one of your projects today, what would you change and why?",
   [("Specific changes",["would change","improve","refactor","redesign","replace","add"],1),("Reasons",["because","limitation","technical debt","bottleneck","hard to"],1),
    ("Priorities",["first","priority","most important","then","start with"],1)],
   {"Reasons":"What problem in the current version motivates that change?","Priorities":"Which change would you make first?"},["project","reflection"],200),
 # ---------------- AI / ML ----------------
 mcq("ML-001","ML","Fundamentals",E,"Which task is a classification problem?",["Predicting a house price","Grouping customers with no labels","Deciding whether an email is spam","Reducing the number of features"],"C","Classification predicts a discrete label such as spam / not spam.",["ml","classification"],45),
 mcq("ML-002","ML","Generalisation",E,"A model reaches 99% accuracy on training data and 70% on validation data. What is the most likely cause?",["Underfitting","Overfitting","Too little training time","Label noise only"],"B","A large train/validation gap indicates the model memorised the training data.",["ml","overfitting"],60),
 mcq("ML-003","ML","Evaluation",M,"Which metric is most informative on a heavily imbalanced fraud dataset?",["Accuracy","Precision and recall","Mean squared error","R-squared"],"B","Accuracy is dominated by the majority class; precision and recall expose minority-class performance.",["ml","metrics"],75),
 mcq("ML-004","ML","Evaluation",M,"What is the purpose of a validation set?",["To report the final unbiased performance","To tune hyperparameters and choose between models","To train the weights","To increase the amount of data"],"B","The validation set guides model selection; the test set is kept for the final estimate.",["ml","validation"],60),
 open_("ML-005","ML","Bias and variance",M,"SHORT","Explain the bias-variance trade-off.",
   [("Bias and underfitting",["bias","underfit"],1),("Variance and overfitting",["variance","overfit"],1),("The trade-off with model complexity",["trade-off","tradeoff","balance","complexity"],1)],
   {"The trade-off with model complexity":"How does increasing model complexity change bias and variance?","Variance and overfitting":"How would you detect high variance in practice?"},["ml","bias-variance"],200),
 open_("ML-006","ML","Regularisation",M,"LONG","Compare L1 and L2 regularisation. When would you use each?",
   [("Purpose: reduce overfitting",["overfit","penalt","regulariz"],1),("L1 gives sparse weights",["l1","lasso","sparse","zero"],1),("L2 shrinks weights",["l2","ridge","shrink","small weights"],1),("Strength hyperparameter",["lambda","alpha","strength","hyperparameter"],1)],
   {"L1 gives sparse weights":"Why does L1 tend to produce exactly-zero weights?","Strength hyperparameter":"How would you choose the regularisation strength?"},["ml","regularization"],300),
 open_("ML-007","ML","Optimisation",H,"LONG","Explain gradient descent and the effect of the learning rate.",
   [("Minimising a loss with gradients",["gradient","loss","minimi","steepest"],1),("The weight update step",["step","update","weights","subtract","negative gradient"],1),("Learning-rate effects",["learning rate","too large","diverge","too small","slow"],1),("Variants",["sgd","stochastic","mini-batch","batch","adam","momentum"],1)],
   {"Learning-rate effects":"What happens if the learning rate is too large? Too small?","Variants":"How does mini-batch SGD differ from full-batch gradient descent?"},["ml","optimization"],360),
 open_("ML-008","ML","Data handling",H,"LONG","How do you split data and avoid data leakage when training a model?",
   [("Train, validation and test splits",["train","validation","test"],1),("What data leakage is",["leak","leakage","test data","information"],1),("Cross-validation",["cross-validation","k-fold","kfold","cross validation"],1),("Fit preprocessing on training data only",["fit on","only on the training","scaler","pipeline","training data only"],1)],
   {"What data leakage is":"Can you give an example of leakage you could cause by accident?","Fit preprocessing on training data only":"Where in the pipeline should scaling be fitted, and why?"},["ml","leakage"],360),
]

sql = lambda s: "'" + s.replace("'", "''") + "'"
js = lambda o: sql(json.dumps(o, ensure_ascii=False, separators=(",", ":")))
rows, tags = [], []
for q in Q:
    rows.append("(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NULL,%s,%d,1,TRUE)" % (
        sql(q["code"]), sql(q["topic"]), sql(q["sub"]), sql(q["d"]), sql(q["type"]), sql(q["stmt"]),
        js(q["options"]) if q["options"] else "NULL", sql(q["key"]) if q["key"] else "NULL", sql(q["why"]) if q["why"] else "NULL",
        js(q["rubric"]) if q["rubric"] else "NULL", js(q["fu"]) if q["fu"] else "NULL", q["secs"]))
    tags += ["((SELECT id FROM questions WHERE code=%s),%s)" % (sql(q["code"]), sql(t)) for t in q["tags"]]
out = ["-- GENERATED by tools/gen_seed_modes.py - edit the generator, not this file.",
       "INSERT INTO questions (code, topic, subtopic, difficulty, type, statement, options, answer_key, explanation, rubric, test_cases, follow_ups, time_estimate_sec, version, active) VALUES",
       ",\n".join(rows) + ";", "INSERT INTO question_tags (question_id, tag) VALUES", ",\n".join(tags) + ";"]
pathlib.Path(__file__).resolve().parent.parent.joinpath("backend/src/main/resources/db/migration/V4__seed_mode_questions.sql").write_text("\n".join(out) + "\n", encoding="utf-8")
from collections import Counter
print(len(Q), dict(Counter(q["topic"] for q in Q)), dict(Counter(q["type"] for q in Q)))
