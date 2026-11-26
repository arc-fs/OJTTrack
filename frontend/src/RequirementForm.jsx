import { MAX_DESCRIPTION, MAX_TITLE } from './requirements.js'

/**
 * Create or edit a requirement. Fully CONTROLLED: the draft lives in CoordinatorRequirementsPage,
 * so closing this form (or the detail that holds it) never loses what was typed.
 *
 * mode 'create': title, description, due date and who gets it (all current students or chosen ones).
 * mode 'edit':   title, description and due date. When `warning` is set (students already
 *                submitted), Save stays disabled until the coordinator ticks "I understand".
 */
function RequirementForm({ mode, draft, onChange, onSave, onCancel, students = [], busy, error, warning,
                           onArchiveAndReplace }) {
    const set = (field, value) => onChange({ ...draft, [field]: value })
    const idPrefix = mode === 'create' ? 'new-req' : `edit-req-${draft.requirementId}`

    function toggleStudent(id, checked) {
        set('studentIds', checked ? [...draft.studentIds, id] : draft.studentIds.filter((s) => s !== id))
    }

    return (
        <form onSubmit={(e) => { e.preventDefault(); onSave() }}>
            <h2>{mode === 'create' ? 'New requirement' : 'Edit requirement'}</h2>

            {draft.replacing && (
                <p role="status">
                    Replacement for &quot;{draft.replacing}&quot; (archived).
                    {draft.droppedCount > 0 && <> {draft.droppedCount} student(s) who are no longer yours were left out.</>}
                </p>
            )}

            <div>
                <label htmlFor={`${idPrefix}-title`}>Title</label>
                <input id={`${idPrefix}-title`} required maxLength={MAX_TITLE} value={draft.title}
                       onChange={(e) => set('title', e.target.value)} />
            </div>
            <div>
                <label htmlFor={`${idPrefix}-description`}>Description (optional)</label>
                <textarea id={`${idPrefix}-description`} maxLength={MAX_DESCRIPTION} value={draft.description}
                          onChange={(e) => set('description', e.target.value)} />
            </div>
            <div>
                <label htmlFor={`${idPrefix}-due`}>Due date (optional)</label>
                <input id={`${idPrefix}-due`} type="date" value={draft.dueDate}
                       onChange={(e) => set('dueDate', e.target.value)} />
            </div>

            {mode === 'create' && (
                <fieldset>
                    <legend>Who gets it</legend>
                    <label>
                        <input type="radio" name="audience" checked={draft.audience === 'ALL'}
                               onChange={() => set('audience', 'ALL')} />
                        {' '}All my current students ({students.length})
                    </label>
                    <label>
                        <input type="radio" name="audience" checked={draft.audience === 'CHOSEN'}
                               onChange={() => set('audience', 'CHOSEN')} />
                        {' '}Choose students
                    </label>
                    {draft.audience === 'CHOSEN' && (
                        <ul>
                            {students.map((s) => (
                                <li key={s.id}>
                                    <label>
                                        <input type="checkbox" checked={draft.studentIds.includes(s.id)}
                                               onChange={(e) => toggleStudent(s.id, e.target.checked)} />
                                        {' '}{s.fullName}
                                    </label>
                                </li>
                            ))}
                        </ul>
                    )}
                    <p>Students you add later don&apos;t get it automatically. Use Assign on the requirement.</p>
                </fieldset>
            )}

            {mode === 'edit' && warning && (
                <div role="alert">
                    <p>{warning}</p>
                    <label>
                        <input type="checkbox" checked={draft.understood}
                               onChange={(e) => set('understood', e.target.checked)} />
                        {' '}I understand: this only clarifies the same requirement.
                    </label>
                </div>
            )}

            {error && <p role="alert">{error}</p>}

            <button type="submit" data-variant="primary"
                    disabled={busy || (mode === 'edit' && warning && !draft.understood)}>
                {mode === 'create' ? 'Create requirement' : 'Save changes'}
            </button>{' '}
            {onArchiveAndReplace && (
                <><button type="button" disabled={busy} onClick={onArchiveAndReplace}>
                    Archive and create replacement
                </button>{' '}</>
            )}
            {/* Discarding a draft destroys typed work (danger); cancelling an edit is routine */}
            <button type="button" data-variant={mode === 'create' ? 'danger' : undefined} onClick={onCancel}>
                {mode === 'create' ? 'Discard draft' : 'Cancel edit'}
            </button>
        </form>
    )
}

export default RequirementForm
