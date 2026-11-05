(() => {
  try {
    const cubes = [];
    const walk = arr => { for (const e of arr) { if (e.type === 'cube') cubes.push(e); if (e.children) walk(e.children); } };
    walk(Outliner.root);
    for (const c of cubes) {
      if (!Cube.all.includes(c)) Cube.all.push(c);
      if (!Project.elements.includes(c)) Project.elements.push(c);
    }
    const txt = Codecs.project.compile();
    const d = JSON.parse(txt);
    Codecs.project.write(txt, 'E:/minecraft mod/meteorosprotocol-new/art/intake-tower.bbmodel');
    return JSON.stringify({ length: txt.length, elements: d.elements.length, groups: d.groups.length });
  } catch (e) { return JSON.stringify({ error: e.message, stack: String(e.stack).slice(0, 1600) }); }
})()
