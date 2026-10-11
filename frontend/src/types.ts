export type Topic = 'JAVA' | 'OOP' | 'DSA' | 'DBMS' | 'OS' | 'CN' | 'HR' | 'PROJECT' | 'ML'
export type InterviewMode = 'TECHNICAL' | 'RESUME' | 'DSA' | 'HR' | 'PROJECT' | 'AI_ML'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type TargetRole = 'JAVA_DEVELOPER' | 'BACKEND_DEVELOPER' | 'SOFTWARE_ENGINEER'
export type ExperienceLevel = 'FRESHER' | 'JUNIOR' | 'MID' | 'SENIOR'
export type QuestionType = 'MCQ' | 'SHORT' | 'LONG' | 'CODING'
export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

export const TOPIC_LABEL: Record<Topic, string> = {
  JAVA: 'Java', OOP: 'OOP', DSA: 'Data structures & algorithms', DBMS: 'Databases', OS: 'Operating systems', CN: 'Computer networks',
  HR: 'Behavioural / HR', PROJECT: 'Project deep-dive', ML: 'AI / ML',
}
export const MODE_INFO: Record<InterviewMode, { label: string; blurb: string; topics: Topic[] }> = {
  TECHNICAL: { label: 'Technical', blurb: 'Core CS: Java, OOP, databases, OS, networks and DSA.', topics: ['JAVA', 'OOP', 'DSA', 'DBMS', 'OS', 'CN'] },
  RESUME: { label: 'Resume + technical', blurb: 'Questions about your resume, mixed with technical questions for your target role.', topics: ['JAVA', 'OOP', 'DSA', 'DBMS', 'OS', 'CN', 'PROJECT'] },
  DSA: { label: 'DSA', blurb: 'Data structures and algorithms concepts and complexity.', topics: ['DSA'] },
  HR: { label: 'HR / behavioural', blurb: 'Situational and behavioural questions (STAR style).', topics: ['HR'] },
  PROJECT: { label: 'Project-based', blurb: 'Explain your own projects, design choices and trade-offs.', topics: ['PROJECT'] },
  AI_ML: { label: 'AI / ML', blurb: 'Machine-learning fundamentals, evaluation and optimisation.', topics: ['ML'] },
}
export const ROLE_LABEL: Record<TargetRole, string> = {
  JAVA_DEVELOPER: 'Java Developer', BACKEND_DEVELOPER: 'Backend Developer', SOFTWARE_ENGINEER: 'Software Engineer',
}

export interface User { id: number; email: string; displayName: string; role: 'USER' | 'ADMIN' }
export interface AuthResponse { token: string; expiresAt: string; user: User }
export interface Profile { targetRole: TargetRole; experienceLevel: ExperienceLevel; skillPreferences: Topic[]; displayName: string; email: string; weeklyGoal: number }

export interface TopicInfo { topic: Topic; total: number; byDifficulty: Record<Difficulty, number> }
export interface TopicsResponse { topics: TopicInfo[]; codingEnabled: boolean }

export type TurnSource = 'AI' | 'RULE_BASED' | 'CANDIDATE'
export interface TurnView { id: number; interviewQuestionId: number; kind: string; source: TurnSource; content: string }
export interface TranscriptMessage {
  role: 'INTERVIEWER' | 'CANDIDATE'; kind: 'QUESTION' | 'ANSWER' | 'SKIPPED' | 'FOLLOW_UP' | 'FOLLOW_UP_ANSWER'
  source: 'QUESTION_BANK' | TurnSource; content: string; position: number; interviewQuestionId: number
}
export interface BookmarkView { questionId: number; topic: Topic; subtopic: string; difficulty: Difficulty; type: QuestionType; statement: string; createdAt: string }
export interface PracticeSuggestion { title: string; reason: string; basis: 'MEASURED' | 'BOOKMARKS' | 'NEW_TOPIC'; mode: InterviewMode; topics: Topic[]; difficulty: Difficulty }

export interface QuestionView {
  interviewQuestionId: number; questionId: number; bookmarked: boolean; position: number; topic: Topic; subtopic: string; difficulty: Difficulty; type: QuestionType
  statement: string; options: { id: string; text: string }[] | null; sampleTests: { input: string; expected: string }[] | null
  tags: string[]; timeEstimateSec: number; presentedAt: string
}
export interface SessionView {
  id: number; status: SessionStatus; targetRole: TargetRole; mode: InterviewMode; topics: Topic[]; baseDifficulty: Difficulty; currentDifficulty: Difficulty
  durationMinutes: number; questionCount: number; answeredCount: number; startedAt: string; deadlineAt: string
  completedAt?: string; remainingSeconds: number; currentQuestion?: QuestionView | null; pendingFollowUp?: TurnView | null; aiAvailable: boolean
}
export interface AnswerAck { answerId: number; duplicate: boolean; answeredCount: number; questionCount: number; followUp?: TurnView | null }
export interface NextQuestionResponse { done: boolean; doneReason?: string; question?: QuestionView | null; answeredCount: number; questionCount: number }
export interface SessionSummary {
  id: number; status: SessionStatus; targetRole: TargetRole; mode?: InterviewMode; topics: Topic[]; baseDifficulty: Difficulty
  questionCount: number; answeredCount: number; startedAt: string; completedAt?: string; overallScore?: number
}
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface AnswerRow {
  position: number; topic: string; subtopic: string; difficulty: string; type: string; statement: string; answered: boolean
  yourAnswer?: string; selectedOption?: string; correctAnswer?: string; score?: number | null; correctness: string
  strengths: string[]; missing: string[]; feedback: string; evaluator: string; finalized: boolean; timeSpentSec: number; timeEstimateSec: number
  evaluationKind?: 'ANSWER_KEY' | 'HEURISTIC' | 'AI_ASSISTED' | 'NONE'; followUps?: { role: string; source: string; content: string }[]
}
export interface ReportData {
  mode?: InterviewMode; aiUsed?: boolean; overallScore: number; questionCount: number; answeredCount: number; totalTimeSec: number; hasPendingEvaluations: boolean
  topics: { topic: string; questions: number; answered: number; avgScore: number; correct: number }[]
  difficulties: { difficulty: string; questions: number; avgScore: number }[]
  strongestTopic?: string; weakestTopic?: string; answers: AnswerRow[]
  difficultyHistory: { position: number; difficulty: string; reason: string }[]
  recommendations: { topic: string; reason: string; subtopics: string[] }[]
}
export interface ReportView {
  sessionId: number; status: 'READY' | 'GENERATING' | 'FAILED'; targetRole: string; sessionTopics: string[]
  startedAt: string; completedAt?: string; data?: ReportData; aiSummary?: { text: string; source: string }; generatedAt?: string
}
export interface Overview {
  total: number; completed: number; inProgress: number; failed: number; averageScore?: number; bestScore?: number; latestScore?: number
  trend: { sessionId: number; date: string; targetRole: string; overallScore: number }[]; completedThisWeek: number; weeklyGoal: number
}
export interface SkillView { topic: string; score: number; attempts: number; updatedAt: string }
export interface JobView { id: number; kind: string; sessionId: number; status: string; attempts: number }
