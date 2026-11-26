import { useState } from 'react'

/**
 * A simple tab bar. Every panel stays MOUNTED; inactive ones are only hidden.
 * React keeps a component's state only while it is rendered, so this is what keeps
 * unsaved work (a half-typed log, coordinator feedback) when the user switches tabs.
 *
 * tabs: [{ id, label, render: (active) => JSX }]. `active` lets a page refresh its
 * lists when it becomes visible, without touching its form/draft state.
 */
function Tabs({ tabs }) {
    const [activeId, setActiveId] = useState(tabs[0].id)

    return (
        <div>
            <nav aria-label="Sections">
                {tabs.map((tab) => (
                    <button
                        key={tab.id}
                        type="button"
                        aria-pressed={tab.id === activeId}
                        onClick={() => setActiveId(tab.id)}
                    >
                        {tab.label}
                    </button>
                ))}
            </nav>
            {tabs.map((tab) => (
                <section key={tab.id} hidden={tab.id !== activeId}>
                    {tab.render(tab.id === activeId)}
                </section>
            ))}
        </div>
    )
}

export default Tabs
