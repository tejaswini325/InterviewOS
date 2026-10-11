import { useCallback, useEffect, useRef, useState } from 'react'

// Browser speech APIs are optional (Chromium and Safari have them, Firefox mostly does not). Text input always works.
interface RecognitionLike {
  lang: string; interimResults: boolean; continuous: boolean
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal?: boolean }> }) => void) | null
  onerror: (() => void) | null; onend: (() => void) | null
  start(): void; stop(): void
}
type RecognitionCtor = new () => RecognitionLike

function recognitionCtor(): RecognitionCtor | null {
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export const speechInputSupported = () => typeof window !== 'undefined' && recognitionCtor() !== null
export const speechOutputSupported = () => typeof window !== 'undefined' && 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'

/** Dictation into a text field. `onText` receives the full transcript so far for this recording. */
export function useDictation(onText: (text: string) => void) {
  const [listening, setListening] = useState(false)
  const rec = useRef<RecognitionLike | null>(null)
  const supported = speechInputSupported()

  const stop = useCallback(() => { rec.current?.stop(); setListening(false) }, [])
  const start = useCallback(() => {
    const Ctor = recognitionCtor()
    if (!Ctor) return
    const r = new Ctor()
    r.lang = navigator.language || 'en-US'; r.interimResults = true; r.continuous = true
    r.onresult = (e) => onText(Array.from(e.results).map((x) => x[0].transcript).join(' ').trim())
    r.onerror = () => setListening(false)
    r.onend = () => setListening(false)
    rec.current = r; r.start(); setListening(true)
  }, [onText])

  useEffect(() => () => rec.current?.stop(), [])
  return { supported, listening, start, stop }
}

export function speak(text: string) {
  if (!speechOutputSupported()) return
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(new SpeechSynthesisUtterance(text))
}
export const stopSpeaking = () => { if (speechOutputSupported()) window.speechSynthesis.cancel() }
