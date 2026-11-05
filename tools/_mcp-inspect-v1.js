(() => {
  try {
    const rows = [];
    const walk = (arr, group) => {
      for (const e of arr) {
        if (e.children) {
          walk(e.children, e.name);
        } else if (e.from && e.to) {
          rows.push({
            name: e.name,
            group,
            from: e.from,
            to: e.to,
            texture: e.faces && e.faces.north ? e.faces.north.texture : null
          });
        }
      }
    };
    walk(Outliner.root, null);
    return JSON.stringify({ project: Project.name, count: rows.length, rows });
  } catch (e) {
    return JSON.stringify({ error: e.message, stack: e.stack });
  }
})()
