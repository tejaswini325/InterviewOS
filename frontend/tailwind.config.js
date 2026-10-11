/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'], mono: ['JetBrains Mono', 'ui-monospace', 'monospace'] },
      colors: { ink: { 950: '#0b0e14', 900: '#11151d', 800: '#181d28', 700: '#232a38', 600: '#2f3849' }, accent: { DEFAULT: '#7c8cff', soft: '#2a3070' } },
    },
  },
  plugins: [],
}
