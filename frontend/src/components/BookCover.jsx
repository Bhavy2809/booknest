import { callNumber, genreStyle } from '../genres.js';

// There are no cover images in the database, so every cover is drawn from the book's own data:
// background = genre colour, white label = library call number.
export default function BookCover({ book, size = 'card' }) {
   const { color } = genreStyle(book.genre);
   const { shelf, line } = callNumber(book);
   // Smaller type for long titles AND for titles with one very long word, so words never split in the middle.
   const longestWord = Math.max(...book.title.split(/\s+/).map((w) => w.length));
   const byLength = book.title.length > 38 ? 2 : book.title.length > 24 ? 1 : 0;
   const byWord = longestWord >= 12 ? 3 : longestWord >= 10 ? 2 : longestWord >= 8 ? 1 : 0;
   const titleClass = ['t-md', 't-sm', 't-xs', 't-2xs'][Math.max(byLength, byWord)];

  return (
     <div className={`cover cover-${size}`} style={{ '--genre': color }} aria-hidden="true">
       <div className="cover-inner">
         <div>
            <strong className={`cover-title ${titleClass}`}>{book.title}</strong>
            <span className="cover-author">{book.author}</span>
         </div>
         <div className="cover-label">
            <span className="cover-shelf">{shelf}</span>
            <span className="cover-line">{line}</span>
         </div>
       </div>
     </div>
  );
}
