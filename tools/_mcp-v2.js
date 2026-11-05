(() => {
  try {
    const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];

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

    function makeTex(name, base, edge, hi, grid) {
      const existing = Texture.all.find(t => t.name === name);
      if (existing) return existing;
      const c = document.createElement('canvas');
      c.width = 16; c.height = 16;
      const g = c.getContext('2d');
      g.fillStyle = base; g.fillRect(0, 0, 16, 16);
      g.fillStyle = edge;
      g.fillRect(0, 0, 16, 1); g.fillRect(0, 15, 16, 1);
      g.fillRect(0, 0, 1, 16); g.fillRect(15, 0, 1, 16);
      g.fillStyle = hi;
      g.fillRect(1, 1, 14, 1); g.fillRect(1, 1, 1, 14);
      if (grid) {
        g.fillStyle = grid;
        for (let i = 4; i < 16; i += 4) {
          g.fillRect(i, 1, 1, 14);
          g.fillRect(1, i, 14, 1);
        }
      }
      const t = new Texture({ name, width: 16, height: 16 });
      t.fromDataURL(c.toDataURL());
      t.add(false);
      return t;
    }

    function texUuid(name) {
      const t = Texture.all.find(x => x.name === name);
      if (!t) throw new Error('missing texture: ' + name);
      return t.uuid;
    }

    makeTex('purple', '#732BF5', '#4A1A9E', '#B07CFF');
    makeTex('aqua_display', '#2D6BFF', '#1A3FA0', '#6FA0FF');
    makeTex('display_off', '#0B1118', '#05080C', '#1A2733', '#243645');

    function cube(name, from, to, mat, parent) {
      const c = new Cube({ name, from, to, box_uv: false, autouv: 0 });
      c.addTo(parent);
      const uuid = texUuid(mat);
      for (const dir of DIRS) {
        const f = c.faces[dir];
        f.texture = uuid;
        f.uv = [0, 0, 16, 16];
      }
      return c;
    }

    const groupBase = find('01 Base');
    const groupIntake = find('03 Intake');
    const groupPorts = find('05 Ports');
    if (!groupBase || !groupIntake || !groupPorts) throw new Error('missing groups');
    for (const name of [
      'HazardStripe', 'GlassMesh', 'Louver_0', 'Louver_1', 'Louver_2',
      'ControlPanel', 'Light_Aetheron', 'Light_Thermion', 'Light_Cryon',
      'Light_Voltix', 'Light_Aquantum'
    ]) {
      const el = find(name);
      if (el) el.remove();
    }
    cube('HazardPlate_FL', [2.6, 2.1, 0.82], [5.0, 2.8, 1.02], 'hazard', groupBase);
    cube('HazardPlate_FR', [11.0, 2.1, 0.82], [13.4, 2.8, 1.02], 'hazard', groupBase);
    cube('HazardPlate_BL', [2.6, 2.1, 13.98], [5.0, 2.8, 14.18], 'hazard', groupBase);
    cube('HazardPlate_BR', [11.0, 2.1, 13.98], [13.4, 2.8, 14.18], 'hazard', groupBase);
    cube('DisplayBack', [3.7, 5.0, 1.88], [12.3, 9.1, 2.02], 'blue', groupIntake);

    const cells = [
      ['Ae', 4.0, 7.2, 'white'],
      ['Th', 6.9, 7.2, 'red'],
      ['Cr', 9.8, 7.2, 'cyan'],
      ['Vp', 4.0, 5.4, 'yellow'],
      ['Vm', 6.9, 5.4, 'purple'],
      ['Aq', 9.8, 5.4, 'aqua_display'],
    ];
    for (const [id, x, y, mat] of cells) {
      cube('DisplayFrame_' + id, [x, y, 1.78], [x + 2.4, y + 1.4, 2.08], 'charcoal', groupIntake);
      const screen = cube('DisplayScreen_' + id, [x + 0.2, y + 0.2, 1.74], [x + 2.2, y + 1.2, 1.84], mat, groupIntake);
      for (const dir of ['south', 'east', 'west', 'up', 'down']) {
        screen.faces[dir].texture = texUuid('charcoal');
      }
    }
    const filterSlot = find('FilterSlot');
    filterSlot.from = [14, 5.2, 6.4];
    filterSlot.to = [14.3, 8.6, 9.6];
    const filterLiner = find('FilterLiner');
    filterLiner.from = [14.05, 5.4, 6.8];
    filterLiner.to = [14.33, 8.4, 9.2];
    const filterHandle = find('FilterHandle');
    filterHandle.from = [14.3, 6.2, 7.7];
    filterHandle.to = [14.58, 7.6, 8.3];

    const sidePort = find('SidePort');
    sidePort.from = [0.4, 5.8, 6.9];
    sidePort.to = [2.2, 8.0, 9.1];
    const portCollar = find('PortCollar');
    portCollar.from = [0.2, 6.15, 7.25];
    portCollar.to = [0.55, 7.65, 8.75];
    const all = [];
    const walk = arr => { for (const e of arr) { if (e.type === 'cube') all.push(e); if (e.children) walk(e.children); } };
    walk(Outliner.root);
    for (const c of all) {
      if (!c.mesh) Cube.preview_controller.setup(c);
      else {
        Cube.preview_controller.updateTransform(c);
        Cube.preview_controller.updateGeometry(c);
        Cube.preview_controller.updateFaces(c);
      }
    }

    const lit = { Ae: 'white', Th: 'red', Cr: 'cyan', Vp: 'yellow', Vm: 'purple', Aq: 'aqua_display' };
    const off = texUuid('display_off');
    globalThis.__setDisplayState = function (mask) {
      const ids = ['Ae', 'Th', 'Cr', 'Vp', 'Vm', 'Aq'];
      for (let i = 0; i < ids.length; i++) {
        const screen = find('DisplayScreen_' + ids[i]);
        if (!screen) continue;
        screen.faces.north.texture = mask[i] ? texUuid(lit[ids[i]]) : off;
        Cube.preview_controller.updateFaces(screen);
      }
      if (Preview.selected && Preview.selected.render) Preview.selected.render();
      return JSON.stringify({ mask });
    };
    globalThis.__setDisplayState([1, 1, 0, 0, 0, 1]);

    Project.name = 'Intake Tower v2';
    Project.view_mode = 'textured';
    return JSON.stringify({
      cubes: all.length,
      groups: Group.all.length,
      textures: Texture.all.length,
      hazard: ['FL', 'FR', 'BL', 'BR'],
      display: cells.map(c => c[0]),
      ports: { slot: [6.4, 9.6], side: [6.9, 9.1], center: 8.0 }
    });
  } catch (e) {
    return JSON.stringify({ error: e.message, stack: String(e.stack).slice(0, 2000) });
  }
})()
