import { useState } from 'react';

// Read-only stars. Two layers of ★★★★★: grey underneath, gold on top, clipped to value/5 of the width.
export function Stars({ value = 0, count, size = 'sm' }) {
  return (
     <span className={`stars stars-${size}`} title={count ? `${value} out of 5 from ${count} ratings` : 'No ratings yet'}>
       <span className="stars-track" aria-hidden="true">
         ★★★★★
         <span className="stars-fill" style={{ width: `${(value / 5) * 100}%` }}>★★★★★</span>
       </span>
       <span className="stars-text">
         {count ? `${value.toFixed(1)}` : 'New'}
         {count ? <span className="muted"> ({count})</span> : null}
       </span>
     </span>
  );
}
// Clickable stars for the logged-in reader's own rating.
export function RatingInput({ value = 0, onChange, disabled }) {
  const [hover, setHover] = useState(0);
  const shown = hover || value;
  return (
     <div className="rating-input" role="radiogroup" aria-label="Your rating" onMouseLeave={() => setHover(0)}>
       {[1, 2, 3, 4, 5].map((n) => (
         <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n} star${n > 1 ? 's' : ''}`}
            disabled={disabled}
            className={n <= shown ? 'on' : ''}
            onMouseEnter={() => setHover(n)}
            onClick={() => onChange(n)}
         >
            ★
         </button>
       ))}
     </div>
  );
}
