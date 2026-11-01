const fs = require("fs");
const path = require("path");

const BRIDGE_URL = "http://127.0.0.1:8787/command";

async function call(action, params = {}) {
  const res = await fetch(BRIDGE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action, params })
  });
  const data = await res.json();
  if (!data.ok) {
    throw new Error(`Command "${action}" failed: ${data.error || JSON.stringify(data)}`);
  }
  return data.result;
}

function sanitizeRotation(rot, origin) {
  if (!rot) return null;
  const rx = rot[0] || 0;
  const ry = rot[1] || 0;
  const rz = rot[2] || 0;

  let axis = "x";
  let angle = rx;
  if (Math.abs(ry) > Math.abs(angle)) {
    axis = "y";
    angle = ry;
  }
  if (Math.abs(rz) > Math.abs(angle)) {
    axis = "z";
    angle = rz;
  }

  if (Math.abs(angle) < 0.01) {
    return null;
  }

  // Snap to Minecraft allowed angles: -45, -22.5, 22.5, 45
  let snappedAngle = 0;
  if (angle <= -33.75) snappedAngle = -45.0;
  else if (angle <= -11.25) snappedAngle = -22.5;
  else if (angle < 11.25) return null;
  else if (angle < 33.75) snappedAngle = 22.5;
  else snappedAngle = 45.0;

  return {
    angle: snappedAngle,
    axis: axis,
    origin: [
      Math.round(origin[0] * 100) / 100,
      Math.round(origin[1] * 100) / 100,
      Math.round(origin[2] * 100) / 100
    ]
  };
}

const ANIMATED_ELEMENT_NAMES = new Set([
  "celestial_die_core_glow",
  "celestial_facet_x_glow",
  "celestial_facet_y_glow",
  "star_ring_orbit_glow",
  "celestial_satellite_glow",
  "gauge_needle",
  "gauge_needle_pivot"
]);

async function main() {
  console.log("Fetching project and texture from Blockbench...");

  // 1. Get Texture DataURL and save texture PNG
  const texData = await call("execute_script", {
    code: `
      const tex = Texture.all[0];
      return {
        dataURL: tex.getDataURL(),
        name: tex.name,
        width: tex.width,
        height: tex.height
      };
    `
  });

  if (texData && texData.dataURL) {
    const base64 = texData.dataURL.replace(/^data:image\/png;base64,/, "");
    const texturePath = path.resolve(__dirname, "../src/main/resources/assets/universal_tool/textures/block/universal_lottery_machine.png");
    fs.writeFileSync(texturePath, Buffer.from(base64, "base64"));
    console.log("Saved texture to:", texturePath);
  }

  // 2. Export .bbmodel file
  const bbmodelContent = await call("execute_script", {
    code: `
      return Codecs.project.compile();
    `
  });

  const bbmodelPath = path.resolve(__dirname, "assets/universal_lottery_machine.bbmodel");
  fs.writeFileSync(bbmodelPath, typeof bbmodelContent === "string" ? bbmodelContent : JSON.stringify(bbmodelContent, null, 2), "utf8");
  console.log("Saved bbmodel to:", bbmodelPath);

  // 3. Extract Block Elements from Blockbench
  const blockModelData = await call("execute_script", {
    code: `
      const elements = [];
      Cube.all.forEach(c => {
        const from = [c.from[0], c.from[1], c.from[2]];
        const to = [c.to[0], c.to[1], c.to[2]];
        const faces = {};
        for (const dir in c.faces) {
          const f = c.faces[dir];
          if (f && f.texture) {
            faces[dir] = {
              uv: [
                Math.round(f.uv[0] / 16 * 1000) / 1000,
                Math.round(f.uv[1] / 16 * 1000) / 1000,
                Math.round(f.uv[2] / 16 * 1000) / 1000,
                Math.round(f.uv[3] / 16 * 1000) / 1000
              ],
              texture: "#0"
            };
          }
        }
        elements.push({
          name: c.name,
          from: [
            Math.round(from[0] * 100) / 100,
            Math.round(from[1] * 100) / 100,
            Math.round(from[2] * 100) / 100
          ],
          to: [
            Math.round(to[0] * 100) / 100,
            Math.round(to[1] * 100) / 100,
            Math.round(to[2] * 100) / 100
          ],
          rawRotation: [c.rotation[0] || 0, c.rotation[1] || 0, c.rotation[2] || 0],
          origin: [c.origin[0] || 8, c.origin[1] || 0, c.origin[2] || 8],
          faces: faces
        });
      });
      return { elements };
    `
  });

  const rawElements = blockModelData.elements;

  // Split into bottom and top block elements
  const bottomElements = [];
  const topElements = [];

  rawElements.forEach(c => {
    const midY = (c.from[1] + c.to[1]) / 2;
    if (midY < 16) {
      const el = {
        name: c.name,
        from: c.from,
        to: c.to,
        faces: c.faces
      };
      const rot = sanitizeRotation(c.rawRotation, c.origin);
      if (rot) el.rotation = rot;
      bottomElements.push(el);
    } else {
      // In block model, animated celestial elements are rendered dynamically by BlockEntityRenderer!
      if (ANIMATED_ELEMENT_NAMES.has(c.name)) {
        return;
      }
      const originShifted = [c.origin[0], c.origin[1] - 16, c.origin[2]];
      const el = {
        name: c.name,
        from: [
          c.from[0],
          Math.round((c.from[1] - 16) * 100) / 100,
          c.from[2]
        ],
        to: [
          c.to[0],
          Math.round((c.to[1] - 16) * 100) / 100,
          c.to[2]
        ],
        faces: c.faces
      };
      const rot = sanitizeRotation(c.rawRotation, originShifted);
      if (rot) el.rotation = rot;
      topElements.push(el);
    }
  });

  // Build Full & Item elements scaled by 0.5 so that ALL coordinates fit within [0, 18.8] <= 32.0!
  // Center item model around (8, 9.4, 8) with deltaX = +3.775, deltaZ = +4.5
  const itemElements = rawElements.map(c => {
    const el = {
      name: c.name,
      from: [
        Math.round((c.from[0] * 0.5 + 3.775) * 100) / 100,
        Math.round(c.from[1] * 0.5 * 100) / 100,
        Math.round((c.from[2] * 0.5 + 4.5) * 100) / 100
      ],
      to: [
        Math.round((c.to[0] * 0.5 + 3.775) * 100) / 100,
        Math.round(c.to[1] * 0.5 * 100) / 100,
        Math.round((c.to[2] * 0.5 + 4.5) * 100) / 100
      ],
      faces: c.faces
    };
    const originScaled = [c.origin[0] * 0.5 + 3.775, c.origin[1] * 0.5, c.origin[2] * 0.5 + 4.5];
    const rot = sanitizeRotation(c.rawRotation, originScaled);
    if (rot) el.rotation = rot;
    return el;
  });

  console.log(`Processed elements: ${bottomElements.length} bottom, ${topElements.length} top (static), ${itemElements.length} item`);

  // Verify all bounds and rotations
  function verifyElements(list, label) {
    list.forEach(el => {
      for (let i = 0; i < 3; i++) {
        if (el.from[i] < -16.0 || el.from[i] > 32.0 || el.to[i] < -16.0 || el.to[i] > 32.0) {
          throw new Error(`[${label}] Bound violation on ${el.name}: from=${el.from}, to=${el.to}`);
        }
      }
      if (el.rotation) {
        const a = el.rotation.angle;
        if (a !== -45 && a !== -22.5 && a !== 22.5 && a !== 45) {
          throw new Error(`[${label}] Invalid rotation angle on ${el.name}: ${a}`);
        }
      }
    });
  }

  verifyElements(bottomElements, "bottom");
  verifyElements(topElements, "top");
  verifyElements(itemElements, "item/full");
  console.log("All bounds and rotations 100% verified against Minecraft BlockElement specification!");

  const baseModelHeader = {
    format_version: "26.3",
    credit: "Universal Lottery Machine remodeled via BlockbenchMCP",
    texture_size: [256, 256],
    textures: {
      "0": "universal_tool:block/universal_lottery_machine",
      "particle": "universal_tool:block/universal_lottery_machine"
    }
  };

  const bottomModel = { ...baseModelHeader, elements: bottomElements };
  const topModel = { ...baseModelHeader, elements: topElements };
  const fullModel = { ...baseModelHeader, elements: itemElements };

  const bottomPath = path.resolve(__dirname, "../src/main/resources/assets/universal_tool/models/block/universal_lottery_machine_bottom.json");
  const topPath = path.resolve(__dirname, "../src/main/resources/assets/universal_tool/models/block/universal_lottery_machine_top.json");
  const fullPath = path.resolve(__dirname, "../src/main/resources/assets/universal_tool/models/block/universal_lottery_machine_full.json");
  const itemPath = path.resolve(__dirname, "../src/main/resources/assets/universal_tool/models/item/universal_lottery_machine.json");

  fs.writeFileSync(bottomPath, JSON.stringify(bottomModel, null, 2) + "\n", "utf8");
  fs.writeFileSync(topPath, JSON.stringify(topModel, null, 2) + "\n", "utf8");
  fs.writeFileSync(fullPath, JSON.stringify(fullModel, null, 2) + "\n", "utf8");

  // Item Model with comprehensive display transformations (accounting for 0.5 element scale)
  const itemModel = {
    parent: "universal_tool:block/universal_lottery_machine_full",
    display: {
      gui: {
        rotation: [30, 225, 0],
        translation: [0, -1.0, 0],
        scale: [0.46, 0.46, 0.46]
      },
      ground: {
        rotation: [0, 0, 0],
        translation: [0, 2, 0],
        scale: [0.35, 0.35, 0.35]
      },
      firstperson_righthand: {
        rotation: [0, 135, 0],
        translation: [1.2, 2.0, 0.8],
        scale: [0.26, 0.26, 0.26]
      },
      firstperson_lefthand: {
        rotation: [0, -135, 0],
        translation: [1.2, 2.0, 0.8],
        scale: [0.26, 0.26, 0.26]
      },
      thirdperson_righthand: {
        rotation: [75, 135, 0],
        translation: [0, 2.0, 0],
        scale: [0.26, 0.26, 0.26]
      },
      thirdperson_lefthand: {
        rotation: [75, -135, 0],
        translation: [0, 2.0, 0],
        scale: [0.26, 0.26, 0.26]
      },
      fixed: {
        rotation: [0, 180, 0],
        translation: [0, -1, 0],
        scale: [0.55, 0.55, 0.55]
      },
      head: {
        rotation: [0, 0, 0],
        translation: [0, -10, 0],
        scale: [0.8, 0.8, 0.8]
      }
    }
  };

  fs.writeFileSync(itemPath, JSON.stringify(itemModel, null, 2) + "\n", "utf8");
  console.log("Models and item successfully exported and validated!");
}

main().catch(err => {
  console.error("Export error:", err);
  process.exit(1);
});
