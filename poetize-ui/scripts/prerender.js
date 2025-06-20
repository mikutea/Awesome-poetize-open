const { renderIds } = require('./render');

(async () => {
  const ids = process.argv.slice(2);
  if (ids.length === 0) {
    console.error('用法: npm run prerender -- <id1> <id2> ...');
    process.exit(1);
  }

  try {
    await renderIds(ids);
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
})(); 