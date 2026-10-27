import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  ApiClientError,
  fetchToken,
  getEmployee,
  initTransaction,
  pollStatus,
  submitEmployee,
} from './api/client'
import type { EmployeePayload, EmployeeResponse, Gender, TransactionResponse } from './api/types'
import './App.css'

const NAME_PATTERN = /^[\p{L}][\p{L} .'-]*$/u
const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]$/
const MOBILE_PATTERN = /^[6-9][0-9]{9}$/

const emptyForm: EmployeePayload = {
  name: '',
  dob: '',
  gender: 'MALE',
  pan: '',
  mobile_number: '',
}

function formatError(error: unknown): string {
  if (error instanceof ApiClientError) {
    const fields = error.details
      ? Object.entries(error.details)
          .map(([field, message]) => `${field}: ${message}`)
          .join('; ')
      : ''
    const code = error.code ? ` [${error.code}]` : ''
    return `${error.message}${code}${fields ? ` — ${fields}` : ''}`
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Something went wrong'
}

function validateForm(form: EmployeePayload): Record<string, string> {
  const errors: Record<string, string> = {}
  const name = form.name.trim()
  if (name.length < 2 || name.length > 100) {
    errors.name = 'Name must be between 2 and 100 characters'
  } else if (!NAME_PATTERN.test(name)) {
    errors.name = 'Name may contain only letters, spaces, periods, apostrophes and hyphens'
  }
  if (!form.dob) {
    errors.dob = 'Date of birth is required'
  } else if (form.dob >= new Date().toISOString().slice(0, 10)) {
    errors.dob = 'Date of birth must be in the past'
  }
  if (!['MALE', 'FEMALE', 'OTHER'].includes(form.gender)) {
    errors.gender = 'Gender must be one of MALE, FEMALE, OTHER'
  }
  if (!PAN_PATTERN.test(form.pan)) {
    errors.pan = 'PAN must be 5 uppercase letters, 4 digits and 1 uppercase letter'
  }
  if (!MOBILE_PATTERN.test(form.mobile_number)) {
    errors.mobile_number = 'Mobile number must contain 10 digits and start with 6, 7, 8, or 9'
  }
  return errors
}

export default function App() {
  const tokenRef = useRef<string | null>(null)
  const clientId = import.meta.env.VITE_CLIENT_ID?.trim() ?? ''
  const clientSecret = import.meta.env.VITE_CLIENT_SECRET?.trim() ?? ''

  const [authState, setAuthState] = useState<'missing-env' | 'loading' | 'ready' | 'error'>(
    clientId && clientSecret ? 'loading' : 'missing-env',
  )
  const [authError, setAuthError] = useState('')
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState<EmployeePayload>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [transaction, setTransaction] = useState<TransactionResponse | null>(null)
  const [employee, setEmployee] = useState<EmployeeResponse | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!clientId || !clientSecret) {
      return
    }
    let cancelled = false
    fetchToken(clientId, clientSecret)
      .then((token) => {
        if (cancelled) {
          return
        }
        tokenRef.current = token.access_token
        setAuthState('ready')
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        setAuthError(formatError(err))
        setAuthState('error')
      })
    return () => {
      cancelled = true
    }
  }, [clientId, clientSecret])

  async function onStartTransaction() {
    const token = tokenRef.current
    if (!token) {
      return
    }
    setBusy(true)
    setError('')
    setMessage('')
    setEmployee(null)
    setTransaction(null)
    try {
      const created = await initTransaction(token)
      setTransaction(created)
      setMessage(created.message ?? 'Transaction initialized')
    } catch (err) {
      setError(formatError(err))
    } finally {
      setBusy(false)
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const token = tokenRef.current
    const transactionId = transaction?.transaction_id
    if (!token || !transactionId) {
      return
    }
    const nextErrors = validateForm(form)
    setFieldErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      return
    }

    setBusy(true)
    setError('')
    setMessage('')
    setEmployee(null)
    try {
      const accepted = await submitEmployee(token, transactionId, {
        ...form,
        name: form.name.trim(),
      })
      setTransaction(accepted)
      setMessage(accepted.message ?? 'Accepted for processing')
      const finished = await pollStatus(token, transactionId)
      setTransaction(finished)
      if (finished.status === 'FAILED') {
        const failed = finished.error
        setError(failed ? `${failed.message} [${failed.code}]` : 'Transaction failed')
        setMessage('')
        return
      }
      const saved = await getEmployee(token, transactionId)
      setEmployee(saved)
      setMessage(finished.message ?? 'Employee data saved successfully')
    } catch (err) {
      setError(formatError(err))
    } finally {
      setBusy(false)
    }
  }

  const canSubmit = authState === 'ready' && transaction?.status === 'INITIALIZED' && !busy

  return (
    <main className="page">
      <h1>Employee API</h1>
      <p className="lede">Start a transaction, submit employee data, then poll until it is saved.</p>

      <section className="card">
        <h2>Authentication</h2>
        {authState === 'missing-env' && (
          <p>
            Copy <code>frontend/.env.example</code> to <code>frontend/.env</code>, set{' '}
            <code>VITE_CLIENT_SECRET</code> from <code>.env.secrets</code>, then restart{' '}
            <code>npm run dev</code>.
          </p>
        )}
        {authState === 'loading' && <p>Requesting access token…</p>}
        {authState === 'ready' && (
          <p className="ok">Authenticated as <code>{clientId}</code></p>
        )}
        {authState === 'error' && <p className="err">{authError}</p>}
      </section>

      <section className="card">
        <h2>Transaction</h2>
        <button type="button" onClick={onStartTransaction} disabled={authState !== 'ready' || busy}>
          Start transaction
        </button>
        {transaction && (
          <p>
            <span className={`badge status-${transaction.status.toLowerCase()}`}>{transaction.status}</span>{' '}
            <code>{transaction.transaction_id}</code>
          </p>
        )}
      </section>

      <section className="card">
        <h2>Employee</h2>
        <form onSubmit={onSubmit}>
          <label>
            Name
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              autoComplete="name"
            />
            {fieldErrors.name && <span className="field-err">{fieldErrors.name}</span>}
          </label>
          <label>
            Date of birth
            <input
              type="date"
              value={form.dob}
              onChange={(e) => setForm({ ...form, dob: e.target.value })}
            />
            {fieldErrors.dob && <span className="field-err">{fieldErrors.dob}</span>}
          </label>
          <label>
            Gender
            <select
              value={form.gender}
              onChange={(e) => setForm({ ...form, gender: e.target.value as Gender })}
            >
              <option value="MALE">MALE</option>
              <option value="FEMALE">FEMALE</option>
              <option value="OTHER">OTHER</option>
            </select>
            {fieldErrors.gender && <span className="field-err">{fieldErrors.gender}</span>}
          </label>
          <label>
            PAN
            <input
              value={form.pan}
              onChange={(e) => setForm({ ...form, pan: e.target.value.toUpperCase() })}
              maxLength={10}
            />
            {fieldErrors.pan && <span className="field-err">{fieldErrors.pan}</span>}
          </label>
          <label>
            Mobile number
            <input
              value={form.mobile_number}
              onChange={(e) => setForm({ ...form, mobile_number: e.target.value })}
              inputMode="numeric"
              maxLength={10}
            />
            {fieldErrors.mobile_number && <span className="field-err">{fieldErrors.mobile_number}</span>}
          </label>
          <button type="submit" disabled={!canSubmit}>
            {busy ? 'Working…' : 'Submit employee'}
          </button>
        </form>
      </section>

      {(message || error || employee) && (
        <section className="card">
          <h2>Result</h2>
          {message && <p className="ok">{message}</p>}
          {error && <p className="err">{error}</p>}
          {employee && (
            <dl>
              <dt>Name</dt>
              <dd>{employee.employee.name}</dd>
              <dt>Date of birth</dt>
              <dd>{employee.employee.dob}</dd>
              <dt>Gender</dt>
              <dd>{employee.employee.gender}</dd>
              <dt>PAN</dt>
              <dd>{employee.employee.pan}</dd>
              <dt>Mobile</dt>
              <dd>{employee.employee.mobile_number}</dd>
            </dl>
          )}
        </section>
      )}
    </main>
  )
}
