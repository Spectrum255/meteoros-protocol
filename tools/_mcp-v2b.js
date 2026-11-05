(() => {
  try {
    const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
    const COLOR = {
      Ae: ['#F7F4EC', '#FFFFFF'],
      Th: ['#EB3324', '#FF7A5C'],
      Cr: ['#73FBFD', '#C8FFFF'],
      Vp: ['#FFFD55', '#FFFFB0'],
      Vm: ['#732BF5', '#B07CFF'],
      Aq: ['#2D6BFF', '#7FA8FF'],
    };

    function find(name) {
      let found = null;
      const walk = arr => {
        for (const e of arr) {
          if (e.name === name) found = e;
          if (e.children) walk(e.children);
        }
      };
      walk(Outliner.root);
      return found;
    }

    function texUuid(name) {
      const t = Texture.all.find(x => x.name === name);
      if (!t) throw new Error('missing texture: ' + name);
      return t.uuid;
    }

    function panelTexture(name, lit) {
      const old = Texture.all.find(t => t.name === name);
      if (old) old.remove();
      const c = document.createElement('canvas');
      c.width = 32; c.height = 32;
      const g = c.getContext('2d');
      g.fillStyle = '#05080C'; g.fillRect(0, 0, 32, 32);
      g.fillStyle = '#1B1D21'; g.fillRect(1, 1, 30, 30);
      g.fillStyle = '#0B1118'; g.fillRect(2, 2, 28, 28);
      const xs = [3, 12, 21];
      const ys = [3, 17];
      const ids = ['Ae', 'Th', 'Cr', 'Vp', 'Vm', 'Aq'];
      for (let i = 0; i < 6; i++) {
        const x = xs[i % 3];
        const y = ys[Math.floor(i / 3)];
        const on = lit.includes(ids[i]);
        if (on) {
          g.fillStyle = '#000000'; g.fillRect(x, y, 9, 13);
          g.fillStyle = COLOR[ids[i]][0]; g.fillRect(x + 1, y + 1, 7, 11);
          g.fillStyle = COLOR[ids[i]][1]; g.fillRect(x + 1, y + 1, 7, 1);
          g.fillStyle = '#FFFFFF'; g.fillRect(x + 2, y + 2, 1, 1);
          g.fillStyle = 'rgba(0,0,0,0.28)'; g.fillRect(x + 1, y + 10, 7, 2);
        } else {
          g.fillStyle = '#000000'; g.fillRect(x, y, 9, 13);
          g.fillStyle = '#101A24'; g.fillRect(x + 1, y + 1, 7, 11);
          g.fillStyle = '#1E2B36';
          for (let gy = y + 2; gy < y + 11; gy += 3) g.fillRect(x + 1, gy, 7, 1);
          for (let gx = x + 3; gx < x + 7; gx += 3) g.fillRect(gx, y + 1, 1, 11);
        }
      }
      const t = new Texture({ name, width: 32, height: 32 });
      t.fromDataURL(c.toDataURL());
      t.add(false);
      return t;
    }

    for (const name of ['purple', 'aqua_display', 'display_off']) {
      const t = Texture.all.find(x => x.name === name);
      if (t) t.remove();
    }
    panelTexture('display_empty', []);
    panelTexture('display_partial', ['Ae', 'Th', 'Aq']);
    panelTexture('display_full', ['Ae', 'Th', 'Cr', 'Vp', 'Vm', 'Aq']);
    panelTexture('display_charge', ['Vp', 'Vm']);

    function cube(name, from, to, mat, parent, uvSize) {
      const c = new Cube({ name, from, to, box_uv: false, autouv: 0 });
      c.addTo(parent);
      const uuid = texUuid(mat);
      const uv = uvSize || 16;
      for (const dir of DIRS) {
        const f = c.faces[dir];
        f.texture = uuid;
        f.uv = [0, 0, uv, uv];
      }
      return c;
    }

    const groupIntake = find('03 Intake');
    if (!groupIntake) throw new Error('missing 03 Intake');

    const removals = [];
    const walk = arr => {
      for (const e of [...arr]) {
        if (e.name && e.name.indexOf('Display') === 0) {
          removals.push(e.name);
          e.remove();
        } else if (e.children) walk(e.children);
      }
    };
    walk(Outliner.root);

    cube('DisplayHousing', [3.7, 5.0, 1.78], [12.3, 9.1, 2.08], 'charcoal', groupIntake);
    const panel = cube('DisplayPanel', [3.9, 5.2, 1.72], [12.1, 9.0, 1.82], 'display_partial', groupIntake, 32);
    for (const dir of ['south', 'east', 'west', 'up', 'down']) {
      panel.faces[dir].texture = texUuid('charcoal');
      panel.faces[dir].uv = [0, 0, 16, 16];
    }

    const all = [];
    const walk2 = arr => { for (const e of arr) { if (e.type === 'cube') all.push(e); if (e.children) walk2(e.children); } };
    walk2(Outliner.root);
    for (const c of all) {
      if (!c.mesh) Cube.preview_controller.setup(c);
      else {
        Cube.preview_controller.updateTransform(c);
        Cube.preview_controller.updateGeometry(c);
        Cube.preview_controller.updateFaces(c);
      }
    }

    const states = {
      empty: 'display_empty',
      partial: 'display_partial',
      full: 'display_full',
      charge: 'display_charge',
    };
    globalThis.__setDisplayState = function (state) {
      const name = states[state] || state;
      const uuid = texUuid(name);
      panel.faces.north.texture = uuid;
      panel.faces.north.uv = [0, 0, 32, 32];
      Cube.preview_controller.updateFaces(panel);
      if (Preview.selected && Preview.selected.render) Preview.selected.render();
      return JSON.stringify({ state, texture: name });
    };
    globalThis.__setDisplayState('partial');

    Project.name = 'Intake Tower v2';
    Project.view_mode = 'textured';
    return JSON.stringify({
      removed: removals,
      cubes: all.length,
      textures: Texture.all.length,
      panel: { from: panel.from, to: panel.to },
      states: Object.keys(states),
    });
  } catch (e) {
    return JSON.stringify({ error: e.message, stack: String(e.stack).slice(0, 2000) });
  }
})()
