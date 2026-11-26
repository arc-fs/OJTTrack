/**
 * A data-heavy table that scrolls sideways on narrow screens instead of squashing its
 * columns. The scroll area is a labelled region that can take keyboard focus, so
 * keyboard users can scroll it with the arrow keys. index.css adds the scroll shadow,
 * the sticky first column, and the phone-only hint.
 */
function TableScroll({ label, children }) {
    return (
        <>
            <p className="scroll-hint">Swipe sideways to see all columns.</p>
            <div className="table-scroll" role="region" aria-label={label} tabIndex={0}>
                {children}
            </div>
        </>
    )
}

export default TableScroll
