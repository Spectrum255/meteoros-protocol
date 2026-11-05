(() => {
  try {
    const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];

    [...Cube.all].forEach(c => c.remove());
    [...Group.all].forEach(g => { if (g !== 'root') g.remove(); });
    [...Texture.all].forEach(t => t.remove());

    function tex(name, base, edge, hi, grid) {
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

    function hazardTex() {
      const c = document.createElement('canvas');
      c.width = 16; c.height = 16;
      const g = c.getContext('2d');
      g.fillStyle = '#FFFD55'; g.fillRect(0, 0, 16, 16);
      g.strokeStyle = '#1B1D21'; g.lineWidth = 3;
      for (let i = -16; i < 32; i += 8) {
        g.beginPath(); g.moveTo(i, 16); g.lineTo(i + 16, 0); g.stroke();
      }
      const t = new Texture({ name: 'hazard', width: 16, height: 16 });
      t.fromDataURL(c.toDataURL());
      t.add(false);
      return t;
    }

    tex('porcelain', '#F7F4EC', '#CFC7B8', '#FFFFFF');
    tex('steel', '#9AA3A8', '#6F777C', '#C4CCD0');
    tex('charcoal', '#1B1D21', '#0D0F12', '#2C3036');
    tex('blue', '#00023D', '#000024', '#1A2E6E');
    tex('yellow', '#FFFD55', '#C9B52A', '#FFFFA0');
    tex('red', '#EB3324', '#A61F14', '#FF7A5E');
    tex('cyan', '#73FBFD', '#2EA9B5', '#C8FFFF');
    tex('white', '#FFFFFF', '#D8D8D8', '#FFFFFF');
    tex('glass', '#12333B', '#73FBFD', '#2A5A66', '#73FBFD');
    hazardTex();

    const matIndex = name => Texture.all.findIndex(t => t.name === name);

    function group(name) {
      return new Group({ name }).addTo('root');
    }

    function cube(name, from, to, mat, parent) {
      const c = new Cube({ name, from, to, box_uv: false, autouv: 0 });
      c.addTo(parent);
      const idx = matIndex(mat);
      for (const dir of DIRS) {
        const f = c.faces[dir];
        f.texture = idx;
        f.uv = [0, 0, 16, 16];
      }
      return c;
    }

    const gBase = group('01 Base');
    const gBody = group('02 Tower');
    const gWindow = group('03 Intake');
    const gFan = group('04 Fan');
    const gPort = group('05 Ports');

    cube('Foot_NW', [0, 0, 0], [3, 2, 3], 'charcoal', gBase);
    cube('Foot_NE', [13, 0, 0], [16, 2, 3], 'charcoal', gBase);
    cube('Foot_SW', [0, 0, 13], [3, 2, 16], 'charcoal', gBase);
    cube('Foot_SE', [13, 0, 13], [16, 2, 16], 'charcoal', gBase);
    cube('BasePlate', [1, 2, 1], [15, 3, 15], 'porcelain', gBase);
    cube('HazardStripe', [2.2, 2.15, 0.82], [13.8, 2.65, 1.02], 'hazard', gBase);

    cube('TowerBody', [2, 3, 2], [14, 11, 14], 'porcelain', gBody);
    cube('Pillar_NW', [2, 3, 2], [3, 11, 3], 'steel', gBody);
    cube('Pillar_NE', [13, 3, 2], [14, 11, 3], 'steel', gBody);
    cube('Pillar_SW', [2, 3, 13], [3, 11, 14], 'steel', gBody);
    cube('Pillar_SE', [13, 3, 13], [14, 11, 14], 'steel', gBody);

    cube('RearPanel', [4, 4.2, 14], [12, 9.2, 14.25], 'charcoal', gBody);
    for (let i = 0; i < 3; i++) {
      cube(`RearGrille_${i}`, [4.6, 5.2 + i * 1.2, 14.25], [11.4, 5.55 + i * 1.2, 14.42], 'blue', gBody);
    }

    cube('FrameTop', [3, 9.2, 1.85], [13, 9.8, 2.2], 'blue', gWindow);
    cube('FrameBottom', [3, 4.4, 1.85], [13, 5.0, 2.2], 'blue', gWindow);
    cube('FrameLeft', [3, 4.4, 1.85], [3.6, 9.8, 2.2], 'blue', gWindow);
    cube('FrameRight', [12.4, 4.4, 1.85], [13, 9.8, 2.2], 'blue', gWindow);
    cube('GlassMesh', [3.6, 5.0, 2.02], [12.4, 9.2, 2.12], 'glass', gWindow);
    for (let i = 0; i < 3; i++) {
      cube(`Louver_${i}`, [3.8, 5.8 + i * 1.2, 1.86], [12.2, 6.08 + i * 1.2, 2.04], 'charcoal', gWindow);
    }

    cube('ControlPanel', [4, 3.25, 1.86], [12, 4.15, 2.08], 'charcoal', gWindow);
    const lights = [
      ['Aetheron', 'white', 4.4],
      ['Thermion', 'red', 5.95],
      ['Cryon', 'cyan', 7.5],
      ['Voltix', 'yellow', 9.05],
      ['Aquantum', 'blue', 10.6],
    ];
    for (const [name, mat, x] of lights) {
      cube(`Light_${name}`, [x, 3.55, 2.08], [x + 0.95, 3.95, 2.18], mat, gWindow);
    }

    cube('FilterSlot', [14, 5.4, 4.4], [14.28, 8.6, 8.6], 'blue', gPort);
    cube('FilterLiner', [14.05, 5.8, 4.8], [14.3, 8.2, 8.2], 'charcoal', gPort);
    cube('FilterHandle', [14.28, 6.5, 5.2], [14.55, 7.5, 6.4], 'red', gPort);

    cube('SidePort', [0.4, 5.0, 6.0], [2.2, 7.2, 8.2], 'steel', gPort);
    cube('PortCollar', [0.2, 5.35, 6.35], [0.55, 6.85, 7.85], 'cyan', gPort);

    cube('Shoulder', [1, 11, 1], [15, 13, 15], 'porcelain', gFan);
    cube('ShoulderTrim', [1, 11.8, 1], [15, 12.15, 15], 'steel', gFan);

    cube('Shroud_N', [1, 13, 1], [15, 15, 3], 'charcoal', gFan);
    cube('Shroud_S', [1, 13, 13], [15, 15, 15], 'charcoal', gFan);
    cube('Shroud_W', [1, 13, 3], [3, 15, 13], 'charcoal', gFan);
    cube('Shroud_E', [13, 13, 3], [15, 15, 13], 'charcoal', gFan);

    cube('FanHub', [7, 13.6, 7], [9, 15, 9], 'steel', gFan);
    cube('Blade_N', [7.15, 14.0, 3.2], [8.85, 14.45, 7.2], 'yellow', gFan);
    cube('Blade_S', [7.15, 14.0, 8.8], [8.85, 14.45, 12.8], 'yellow', gFan);
    cube('Blade_W', [3.2, 14.0, 7.15], [7.2, 14.45, 8.85], 'yellow', gFan);
    cube('Blade_E', [8.8, 14.0, 7.15], [12.8, 14.45, 8.85], 'yellow', gFan);

    cube('Cap_N', [1, 15, 1], [15, 16, 3], 'porcelain', gFan);
    cube('Cap_S', [1, 15, 13], [15, 16, 15], 'porcelain', gFan);
    cube('Cap_W', [1, 15, 3], [3, 16, 13], 'porcelain', gFan);
    cube('Cap_E', [13, 15, 3], [15, 16, 13], 'porcelain', gFan);
    cube('CapCorner_NW', [1, 15.2, 1], [3, 16.2, 3], 'yellow', gFan);
    cube('CapCorner_NE', [13, 15.2, 1], [15, 16.2, 3], 'yellow', gFan);
    cube('CapCorner_SW', [1, 15.2, 13], [3, 16.2, 15], 'yellow', gFan);
    cube('CapCorner_SE', [13, 15.2, 13], [15, 16.2, 15], 'yellow', gFan);

    Project.name = 'Intake Tower';
    return JSON.stringify({
      project: Project.name,
      cubes: Cube.all.length,
      groups: Group.all.length,
      textures: Texture.all.length,
      bounds: {
        min: [Math.min(...Cube.all.map(c => c.from[0])), Math.min(...Cube.all.map(c => c.from[1])), Math.min(...Cube.all.map(c => c.from[2]))],
        max: [Math.max(...Cube.all.map(c => c.to[0])), Math.max(...Cube.all.map(c => c.to[1])), Math.max(...Cube.all.map(c => c.to[2]))],
      },
    });
  } catch (e) {
    return JSON.stringify({ error: e.message, stack: String(e.stack).slice(0, 1600) });
  }
})()
