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

const CUBES = [
  // ==========================================
  // --- A. LOWER CHASSIS (lower_chassis) ---
  // ==========================================
  { name: "pedestal_plate", parent: "lower_chassis", from: [0.5, 0, 0.5], to: [15.5, 2, 15.5] },
  // 4 Bracket Feet with brass rivets
  { name: "foot_screen_right_f", parent: "lower_chassis", from: [-0.4, 0, -0.4], to: [2.8, 2.8, 2.8] },
  { name: "foot_screen_left_f", parent: "lower_chassis", from: [13.2, 0, -0.4], to: [16.4, 2.8, 2.8] },
  { name: "foot_screen_right_b", parent: "lower_chassis", from: [-0.4, 0, 13.2], to: [2.8, 2.8, 16.4] },
  { name: "foot_screen_left_b", parent: "lower_chassis", from: [13.2, 0, 13.2], to: [16.4, 2.8, 16.4] },
  // 4 Corner Pillars
  { name: "lower_pillar_rf", parent: "lower_chassis", from: [0.5, 2, 0.5], to: [2.6, 14.5, 2.6] },
  { name: "lower_pillar_lf", parent: "lower_chassis", from: [13.4, 2, 0.5], to: [15.5, 14.5, 2.6] },
  { name: "lower_pillar_rb", parent: "lower_chassis", from: [0.5, 2, 13.4], to: [2.6, 14.5, 15.5] },
  { name: "lower_pillar_lb", parent: "lower_chassis", from: [13.4, 2, 13.4], to: [15.5, 14.5, 15.5] },
  // Lower Body Main Core
  { name: "lower_body_core", parent: "lower_chassis", from: [1.8, 2, 1.8], to: [14.2, 14.5, 14.2] },
  // Lower Side Inlay Panels
  { name: "lower_side_panel_r", parent: "lower_chassis", from: [0.8, 3, 3], to: [1.6, 13.5, 13] },
  { name: "lower_side_panel_l", parent: "lower_chassis", from: [14.4, 3, 3], to: [15.2, 13.5, 13] },

  // ==========================================
  // --- B. LOWER DISPENSER (lower_dispenser) ---
  // ==========================================
  // Stepped forward delivery tray at front (Z = -2 to 2)
  { name: "chute_tray_floor", parent: "lower_dispenser", from: [2.5, 0.8, -2], to: [13.5, 2.2, 2.8] },
  { name: "chute_tray_lip", parent: "lower_dispenser", from: [2.2, 2.2, -2.4], to: [13.8, 3.4, -1.6] },
  { name: "chute_side_lip_r", parent: "lower_dispenser", from: [2.2, 2.2, -1.6], to: [3.4, 5.5, 2.2] },
  { name: "chute_side_lip_l", parent: "lower_dispenser", from: [12.6, 2.2, -1.6], to: [13.8, 5.5, 2.2] },
  // Void Scrap Drawer underneath
  { name: "scrap_drawer_face", parent: "lower_dispenser", from: [3.4, 2.2, -1.5], to: [12.6, 5.6, 1.6] },
  { name: "scrap_drawer_handle", parent: "lower_dispenser", from: [6.2, 3.6, -1.9], to: [9.8, 4.4, -1.3] },
  // Recessed Delivery Void Chute (glowing interior)
  { name: "chute_interior_floor", parent: "lower_dispenser", from: [3.4, 5.6, -0.4], to: [12.6, 6.2, 3.5] },
  { name: "chute_interior_back_glow", parent: "lower_dispenser", from: [3.5, 6.2, 3.2], to: [12.5, 11.2, 3.6] },
  { name: "chute_interior_roof", parent: "lower_dispenser", from: [3.4, 11, -0.4], to: [12.6, 11.6, 3.5] },
  { name: "chute_arch_outer", parent: "lower_dispenser", from: [3, 11.2, -0.8], to: [13, 12.8, 1.8] },
  { name: "chute_light_r_glow", parent: "lower_dispenser", from: [3.6, 7.8, 1], to: [4.2, 9.8, 2] },
  { name: "chute_light_l_glow", parent: "lower_dispenser", from: [11.8, 7.8, 1], to: [12.4, 9.8, 2] },

  // ==========================================
  // --- C. LOWER MACHINERY (lower_machinery) ---
  // ==========================================
  // Flanking Glowing Tubes (Lower Half)
  { name: "tube_lower_bracket_bot_r", parent: "lower_machinery", from: [-1.4, 3.2, 7.6], to: [0.8, 4.4, 9.8] },
  { name: "tube_lower_bracket_top_r", parent: "lower_machinery", from: [-1.4, 14.2, 7.6], to: [0.8, 15.4, 9.8] },
  { name: "tube_lower_glass_r", parent: "lower_machinery", from: [-1.1, 4.4, 7.9], to: [0.5, 14.2, 9.5] },
  { name: "tube_lower_core_r_glow", parent: "lower_machinery", from: [-0.8, 4.8, 8.2], to: [0.2, 13.8, 9.2] },

  { name: "tube_lower_bracket_bot_l", parent: "lower_machinery", from: [15.2, 3.2, 7.6], to: [17.4, 4.4, 9.8] },
  { name: "tube_lower_bracket_top_l", parent: "lower_machinery", from: [15.2, 14.2, 7.6], to: [17.4, 15.4, 9.8] },
  { name: "tube_lower_glass_l", parent: "lower_machinery", from: [15.5, 4.4, 7.9], to: [17.1, 14.2, 9.5] },
  { name: "tube_lower_core_l_glow", parent: "lower_machinery", from: [15.8, 4.8, 8.2], to: [16.8, 13.8, 9.2] },

  { name: "pipe_lower_loop_r", parent: "lower_machinery", from: [-1.2, 2.2, 7.2], to: [0.6, 3.4, 9.2] },
  { name: "pipe_lower_loop_l", parent: "lower_machinery", from: [15.4, 2.2, 7.2], to: [17.2, 3.4, 9.2] },

  // Rear Cooling Vents (Back View)
  { name: "back_lower_frame", parent: "lower_machinery", from: [2, 2.5, 14.2], to: [14, 13.5, 15] },
  { name: "back_lower_vent1", parent: "lower_machinery", from: [2.8, 3.5, 14.8], to: [7.4, 11.8, 15.4] },
  { name: "back_lower_vent2", parent: "lower_machinery", from: [8.6, 3.5, 14.8], to: [13.2, 11.8, 15.4] },
  { name: "back_lower_conduit", parent: "lower_machinery", from: [7.6, 2, 14.6], to: [8.4, 14, 15.2] },

  // ==========================================
  // --- D. UPPER CONSOLE (upper_console) ---
  // ==========================================
  // Waist Belt (Recessed slightly below the shelf: Y = 13.8 to 15.2)
  { name: "waist_trim_belt", parent: "upper_console", from: [0.2, 13.8, 0.2], to: [15.8, 15.2, 15.8] },
  { name: "waist_boss_rf", parent: "upper_console", from: [-0.2, 13.6, -0.2], to: [2.8, 15.4, 2.8] },
  { name: "waist_boss_lf", parent: "upper_console", from: [13.2, 13.6, -0.2], to: [16.2, 15.4, 2.8] },
  { name: "waist_boss_rb", parent: "upper_console", from: [-0.2, 13.6, 13.2], to: [2.8, 15.4, 16.2] },
  { name: "waist_boss_lb", parent: "upper_console", from: [13.2, 13.6, 13.2], to: [16.2, 15.4, 16.2] },

  // Angled Control Console Shelf (Protruding forward at front Z = -0.8 to 2.8)
  { name: "console_desk_slope", parent: "upper_console", from: [2.2, 14.8, -0.8], to: [13.8, 16.8, 2.8] },
  // Big Center Golden Button (Prominently mounted forward)
  { name: "button_pedestal", parent: "upper_console", from: [6.8, 16.2, -0.4], to: [9.2, 17, 1.8] },
  { name: "button_housing_ring", parent: "upper_console", from: [6.6, 16.8, -0.2], to: [9.4, 17.5, 1.6] },
  { name: "button_golden_cap_glow", parent: "upper_console", from: [7, 17.2, 0.1], to: [9, 18.4, 1.3] },

  // Screen Left (+X: Toggle Switch + Green & Red LEDs)
  { name: "indicator_toggle", parent: "upper_console", from: [11.2, 16.6, 0.4], to: [11.8, 18, 1] },
  { name: "indicator_gem_green1_glow", parent: "upper_console", from: [10.2, 16.8, 0.8], to: [10.8, 17.5, 1.4] },
  { name: "indicator_gem_red1_glow", parent: "upper_console", from: [10.2, 16.8, -0.1], to: [10.8, 17.5, 0.5] },

  // Screen Right (-X: 4 LEDs Cluster in 2x2 grid)
  { name: "indicator_gem_green2_glow", parent: "upper_console", from: [5.2, 16.8, 0.8], to: [5.8, 17.5, 1.4] },
  { name: "indicator_gem_red2_glow", parent: "upper_console", from: [5.2, 16.8, -0.1], to: [5.8, 17.5, 0.5] },
  { name: "indicator_gem_green3_glow", parent: "upper_console", from: [4.2, 16.8, 0.8], to: [4.8, 17.5, 1.4] },
  { name: "indicator_gem_red3_glow", parent: "upper_console", from: [4.2, 16.8, -0.1], to: [4.8, 17.5, 0.5] },

  // Slot Machine Pull Lever on Screen Right (-X) flank
  { name: "lever_base_box", parent: "upper_console", from: [-1.4, 14.5, 4.2], to: [0.4, 17.5, 7.8] },
  { name: "lever_shaft", parent: "upper_console", from: [-1, 16.8, 5.5], to: [-0.2, 23.2, 6.3], rotation: [-15, 0, 0], origin: [-0.6, 16.8, 5.9] },
  { name: "lever_ball_knob_glow", parent: "upper_console", from: [-1.6, 22.5, 4.4], to: [0.4, 24.5, 6.4], rotation: [-15, 0, 0], origin: [-0.6, 16.8, 5.9] },

  // ==========================================
  // --- E. UPPER OBSERVATORY (upper_observatory) ---
  // ==========================================
  // Rear Housing Core (Z = 5.2 to 15)
  { name: "upper_rear_housing", parent: "upper_observatory", from: [1.5, 16.2, 5.2], to: [14.5, 28.5, 14.5] },
  // 4 Corner Upper Pillars
  { name: "upper_pillar_rf", parent: "upper_observatory", from: [0.5, 16.2, 0.5], to: [2.5, 28.5, 2.5] },
  { name: "upper_pillar_lf", parent: "upper_observatory", from: [13.5, 16.2, 0.5], to: [15.5, 28.5, 2.5] },
  { name: "upper_pillar_rb", parent: "upper_observatory", from: [0.5, 16.2, 13.5], to: [2.5, 28.5, 15.5] },
  { name: "upper_pillar_lb", parent: "upper_observatory", from: [13.5, 16.2, 13.5], to: [15.5, 28.5, 15.5] },
  // Side Walls of the upper chamber
  { name: "upper_side_wall_r", parent: "upper_observatory", from: [1.2, 16.5, 2], to: [2.5, 28, 5.6] },
  { name: "upper_side_wall_l", parent: "upper_observatory", from: [13.5, 16.5, 2], to: [14.8, 28, 5.6] },

  // Recessed Dark Starfield Chamber Wall (Z = 4.8 to 5.4)
  { name: "chamber_starfield_wall_glow", parent: "upper_observatory", from: [2.5, 16.5, 4.8], to: [13.5, 28, 5.4] },

  // Heavy Round/Octagonal Brass Portal Frame at Front (Z = 1.4 to 2.4)
  { name: "chamber_frame_top", parent: "upper_observatory", from: [4.2, 24.8, 1.4], to: [11.8, 25.8, 2.4] },
  { name: "chamber_frame_bot", parent: "upper_observatory", from: [4.2, 17.2, 1.4], to: [11.8, 18.2, 2.4] },
  { name: "chamber_frame_left", parent: "upper_observatory", from: [12, 18.2, 1.4], to: [13, 24.8, 2.4] },
  { name: "chamber_frame_right", parent: "upper_observatory", from: [3, 18.2, 1.4], to: [4, 24.8, 2.4] },
  { name: "chamber_corner_tl", parent: "upper_observatory", from: [11.2, 23.8, 1.5], to: [12.4, 25, 2.3] },
  { name: "chamber_corner_tr", parent: "upper_observatory", from: [3.6, 23.8, 1.5], to: [4.8, 25, 2.3] },
  { name: "chamber_corner_bl", parent: "upper_observatory", from: [11.2, 18, 1.5], to: [12.4, 19.2, 2.3] },
  { name: "chamber_corner_br", parent: "upper_observatory", from: [3.6, 18, 1.5], to: [4.8, 19.2, 2.3] },

  // Floating Celestial D20 Violet Die + Rings + Satellite INSIDE CHAMBER (Z = 2.4 to 4.5)
  { name: "celestial_die_core_glow", parent: "upper_observatory", from: [6.6, 20.1, 2.6], to: [9.4, 22.9, 4.6] },
  { name: "celestial_facet_x_glow", parent: "upper_observatory", from: [6.8, 20.3, 2.8], to: [9.2, 22.7, 4.4], rotation: [45, 0, 0], origin: [8, 21.5, 3.6] },
  { name: "celestial_facet_y_glow", parent: "upper_observatory", from: [6.8, 20.3, 2.8], to: [9.2, 22.7, 4.4], rotation: [0, 45, 0], origin: [8, 21.5, 3.6] },
  { name: "star_ring_orbit_glow", parent: "upper_observatory", from: [4.8, 21.2, 1.6], to: [11.2, 21.8, 5.6], rotation: [20, 0, 15], origin: [8, 21.5, 3.6] },
  { name: "celestial_satellite_glow", parent: "upper_observatory", from: [10.4, 23, 2.8], to: [11.2, 23.8, 3.6] },

  // ==========================================
  // --- F. UPPER PROBABILITY GAUGE (upper_dial) ---
  // ==========================================
  { name: "gauge_plate_base", parent: "upper_dial", from: [4.6, 25.8, 1.2], to: [11.4, 28.5, 2] },
  { name: "gauge_brass_arch", parent: "upper_dial", from: [4.4, 28.1, 0.9], to: [11.6, 28.9, 2.1] },
  { name: "gauge_needle_pivot", parent: "upper_dial", from: [7.6, 26, 0.8], to: [8.4, 26.8, 1.4] },
  { name: "gauge_needle", parent: "upper_dial", from: [7.8, 26.6, 0.6], to: [8.2, 28.4, 1], rotation: [0, 0, -28], origin: [8, 26.4, 0.8] },
  { name: "gauge_flank_lamp_r", parent: "upper_dial", from: [3.4, 26.5, 1.2], to: [4.4, 27.8, 1.8] },
  { name: "gauge_flank_lamp_l", parent: "upper_dial", from: [11.6, 26.5, 1.2], to: [12.6, 27.8, 1.8] },

  // ==========================================
  // --- G. ROOF & AMETHYST CREST (upper_roof) ---
  // ==========================================
  { name: "roof_base_cornice", parent: "upper_roof", from: [-0.2, 28.5, -0.2], to: [16.2, 29.8, 16.2] },
  { name: "roof_arch_center", parent: "upper_roof", from: [3, 29.8, 0.8], to: [13, 32.2, 15.2] },
  { name: "roof_arch_side_r", parent: "upper_roof", from: [0.8, 29.8, 0.8], to: [3, 31.2, 15.2] },
  { name: "roof_arch_side_l", parent: "upper_roof", from: [13, 29.8, 0.8], to: [15.2, 31.2, 15.2] },
  { name: "roof_rim_center", parent: "upper_roof", from: [2.8, 31.8, 0.4], to: [13.2, 32.5, 1.6] },
  { name: "roof_rim_side_r", parent: "upper_roof", from: [0.6, 30.8, 0.4], to: [3, 31.8, 1.6] },
  { name: "roof_rim_side_l", parent: "upper_roof", from: [13, 30.8, 0.4], to: [15.4, 31.8, 1.6] },
  { name: "roof_center_crest", parent: "upper_roof", from: [6, 32.2, 6], to: [10, 33, 10] },

  // Multi-Shard Glowing Violet Amethyst Crystal Cluster Spire (Y = 33 to 37.6)
  { name: "crystal_shard_center_glow", parent: "upper_roof", from: [7.2, 33, 7.2], to: [8.8, 37.6, 8.8] },
  { name: "crystal_shard_r_glow", parent: "upper_roof", from: [5.6, 33, 7.4], to: [7.2, 36, 8.6], rotation: [0, 0, -18], origin: [6.4, 33, 8] },
  { name: "crystal_shard_l_glow", parent: "upper_roof", from: [8.8, 33, 7.4], to: [10.4, 36, 8.6], rotation: [0, 0, 18], origin: [9.6, 33, 8] },
  { name: "crystal_shard_f_glow", parent: "upper_roof", from: [7.4, 33, 5.6], to: [8.6, 36, 7.2], rotation: [-18, 0, 0], origin: [8, 33, 6.4] },
  { name: "crystal_shard_b_glow", parent: "upper_roof", from: [7.4, 33, 8.8], to: [8.6, 36, 10.4], rotation: [18, 0, 0], origin: [8, 33, 9.6] },

  // ==========================================
  // --- H. UPPER MACHINERY & PIPES (upper_machinery) ---
  // ==========================================
  { name: "tube_upper_bracket_bot_r", parent: "upper_machinery", from: [-1.4, 15.8, 7.6], to: [0.8, 17, 9.8] },
  { name: "tube_upper_bracket_top_r", parent: "upper_machinery", from: [-1.4, 26.5, 7.6], to: [0.8, 27.7, 9.8] },
  { name: "tube_upper_glass_r", parent: "upper_machinery", from: [-1.1, 16.8, 7.9], to: [0.5, 26.6, 9.5] },
  { name: "tube_upper_core_r_glow", parent: "upper_machinery", from: [-0.8, 17.2, 8.2], to: [0.2, 26.2, 9.2] },

  { name: "tube_upper_bracket_bot_l", parent: "upper_machinery", from: [15.2, 15.8, 7.6], to: [17.4, 17, 9.8] },
  { name: "tube_upper_bracket_top_l", parent: "upper_machinery", from: [15.2, 26.5, 7.6], to: [17.4, 27.7, 9.8] },
  { name: "tube_upper_glass_l", parent: "upper_machinery", from: [15.5, 16.8, 7.9], to: [17.1, 26.6, 9.5] },
  { name: "tube_upper_core_l_glow", parent: "upper_machinery", from: [15.8, 17.2, 8.2], to: [16.8, 26.2, 9.2] },

  // Copper steam piping on Screen Left flank (+X)
  { name: "pipe_upper_side_l", parent: "upper_machinery", from: [15.5, 16.8, 6.2], to: [16.7, 23.5, 7.4] },
  // Circular Analog Pressure Gauge on side pipe (+X flank)
  { name: "side_pressure_gauge_body", parent: "upper_machinery", from: [16.6, 19.8, 6.2], to: [18.2, 23.2, 9.4] },
  { name: "side_pressure_gauge_face", parent: "upper_machinery", from: [18.2, 20.2, 6.6], to: [18.5, 22.8, 9] },

  // Rear Industrial Ventilation Slats (Back View)
  { name: "back_upper_frame", parent: "upper_machinery", from: [2, 17.5, 14.2], to: [14, 27.5, 15] },
  { name: "back_upper_slats", parent: "upper_machinery", from: [2.8, 18.2, 14.8], to: [13.2, 26.8, 15.4] },
  { name: "back_upper_conduit", parent: "upper_machinery", from: [7.6, 16, 14.6], to: [8.4, 28, 15.2] }
];

async function main() {
  console.log("Connecting to Blockbench...");
  await call("get_status");

  console.log("Setting Java Block project with 256x256 resolution...");
  await call("new_project", {
    format: "java_block",
    name: "universal_lottery_machine",
    texture_size: [256, 256]
  });

  await call("set_project_meta", {
    texture_width: 256,
    texture_height: 256
  });

  // Clear existing
  await call("execute_script", {
    code: `
      Cube.all.slice().forEach(c => c.remove());
      Group.all.slice().forEach(g => g.remove());
      Texture.all.slice().forEach(t => t.remove());
      Canvas.updateAll();
      return { ok: true };
    `
  });

  // Create Outliner Groups
  console.log("Creating Groups...");
  await call("add_group", { name: "lower_half", origin: [8, 0, 8] });
  await call("add_group", { name: "lower_chassis", parent: "lower_half", origin: [8, 0, 8] });
  await call("add_group", { name: "lower_dispenser", parent: "lower_half", origin: [8, 0, 8] });
  await call("add_group", { name: "lower_machinery", parent: "lower_half", origin: [8, 0, 8] });

  await call("add_group", { name: "upper_half", origin: [8, 16, 8] });
  await call("add_group", { name: "upper_console", parent: "upper_half", origin: [8, 16, 8] });
  await call("add_group", { name: "upper_observatory", parent: "upper_half", origin: [8, 16, 8] });
  await call("add_group", { name: "upper_dial", parent: "upper_half", origin: [8, 16, 8] });
  await call("add_group", { name: "upper_roof", parent: "upper_half", origin: [8, 16, 8] });
  await call("add_group", { name: "upper_machinery", parent: "upper_half", origin: [8, 16, 8] });

  // Add all cubes
  console.log("Adding cubes...");
  const res = await call("execute_script", {
    params: { cubes: CUBES },
    code: `
      const groups = {};
      Group.all.forEach(g => { groups[g.name] = g; });

      params.cubes.forEach(spec => {
        const parent = spec.parent ? groups[spec.parent] : null;
        const cube = new Cube({
          name: spec.name,
          from: spec.from,
          to: spec.to,
          origin: spec.origin || [8, 0, 8],
          rotation: spec.rotation || [0, 0, 0]
        }).init();
        if (parent) {
          cube.addTo(parent);
        }
      });
      Canvas.updateAll();
      return { totalCubes: Cube.all.length };
    `
  });
  console.log("Total cubes added:", res.totalCubes);

  // Pack UVs
  console.log("Packing UVs...");
  await call("pack_uv", { padding: 2, auto_resize: true });

  // Create texture
  console.log("Creating texture...");
  await call("create_texture", {
    name: "universal_lottery_machine",
    width: 256,
    height: 256,
    particle: true
  });

  // Apply detailed shading pass
  console.log("Applying detailed shading pass...");
  await call("detail_cubes", {
    base: "#221e26",
    glow_regex: "_glow$",
    noise: 0.03,
    blur: 0.4,
    top_light: 0.16,
    bottom_dark: 0.24,
    colors: {
      "pedestal": "#1a161e",
      "foot": "#2c2834",
      "pillar": "#2b2732",
      "lower_body": "#221e26",
      "upper_rear": "#221e26",
      "side_wall": "#221e26",
      "panel": "#26222c",
      "chute_tray": "#2c2734",
      "chute_arch": "#2b2732",
      "scrap_drawer_face": "#2c2634",
      "scrap_drawer_handle": "#f0be3a",
      "trim": "#d8a832",
      "boss": "#e5b338",
      "brass": "#dfad34",
      "frame": "#dfad34",
      "corner": "#d8a832",
      "rim": "#f6ce46",
      "crest": "#e5b338",
      "console_desk": "#231f28",
      "button_pedestal": "#2a2533",
      "button_housing": "#dfad34",
      "button_golden": "#ffd84d",
      "knob": "#f6ce46",
      "lever_shaft": "#b88a24",
      "lever_base": "#201c24",
      "pipe": "#c46944",
      "bracket": "#dfad34",
      "tube.*glass": "#1c1426",
      "tube.*core": "#c274fc",
      "chamber_starfield": "#0c0418",
      "celestial_die": "#b466eb",
      "celestial_facet": "#d39cf9",
      "star_ring": "#ffdc5e",
      "celestial_satellite": "#e2a4ff",
      "gauge_plate": "#f4ede0",
      "gauge_needle": "#1c1610",
      "gauge_flank": "#dfad34",
      "side_pressure_gauge_body": "#c46944",
      "side_pressure_gauge_face": "#f4ede0",
      "crystal_shard": "#b466eb",
      "indicator_gem_green": "#34dc72",
      "indicator_gem_red": "#ff3858",
      "indicator_toggle": "#3c342c",
      "back_lower_frame": "#1e1a22",
      "back_upper_frame": "#1e1a22",
      "slats": "#110e14",
      "vent": "#131016",
      "conduit": "#c46944"
    }
  });

  // Second pass: Paint crisp details (gauge dial markings, stars, LEDs, rivets)
  console.log("Painting crisp pixel art details...");
  await call("execute_script", {
    code: `
      const tex = Texture.all[0];
      if (!tex) return { painted: false };

      tex.edit((canvas) => {
        const ctx = canvas.getContext("2d");

        // Helper: get face bounding box in canvas pixels
        function getFaceRect(cubeName, faceName) {
          const cube = Cube.all.find(c => c.name === cubeName);
          if (!cube || !cube.faces[faceName]) return null;
          const uv = cube.faces[faceName].uv;
          const x = Math.round(Math.min(uv[0], uv[2]));
          const y = Math.round(Math.min(uv[1], uv[3]));
          const w = Math.round(Math.abs(uv[2] - uv[0]));
          const h = Math.round(Math.abs(uv[3] - uv[1]));
          return { x, y, w, h };
        }

        // 1. Paint Probability Gauge Dial (tick marks & arc)
        const dialRect = getFaceRect("gauge_plate_base", "north");
        if (dialRect) {
          ctx.save();
          // Dark arch border
          ctx.strokeStyle = "#8c6a20";
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.arc(dialRect.x + dialRect.w / 2, dialRect.y + dialRect.h - 1, dialRect.w * 0.42, Math.PI, 0);
          ctx.stroke();

          // Black ticks on left, Red ticks on right (high probability zone!)
          for (let angle = Math.PI * 0.15; angle <= Math.PI * 0.85; angle += Math.PI * 0.12) {
            const isRed = angle < Math.PI * 0.4;
            ctx.strokeStyle = isRed ? "#dc283c" : "#282420";
            ctx.lineWidth = 1;
            const r1 = dialRect.w * 0.35;
            const r2 = dialRect.w * 0.43;
            const cx = dialRect.x + dialRect.w / 2;
            const cy = dialRect.y + dialRect.h - 1;
            ctx.beginPath();
            ctx.moveTo(cx + Math.cos(angle) * r1, cy - Math.sin(angle) * r1);
            ctx.lineTo(cx + Math.cos(angle) * r2, cy - Math.sin(angle) * r2);
            ctx.stroke();
          }

          // Brass center mounting rim
          ctx.fillStyle = "#dfad34";
          ctx.beginPath();
          ctx.arc(dialRect.x + dialRect.w / 2, dialRect.y + dialRect.h - 1, 2.5, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }

        // 2. Paint Side Pressure Gauge Face
        const pDialRect = getFaceRect("side_pressure_gauge_face", "east");
        if (pDialRect) {
          ctx.save();
          ctx.fillStyle = "#f4eee2";
          ctx.fillRect(pDialRect.x, pDialRect.y, pDialRect.w, pDialRect.h);
          ctx.strokeStyle = "#9c7228";
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.arc(pDialRect.x + pDialRect.w / 2, pDialRect.y + pDialRect.h / 2, pDialRect.w * 0.44, 0, Math.PI * 2);
          ctx.stroke();

          // Dial Needle
          ctx.strokeStyle = "#221c16";
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.moveTo(pDialRect.x + pDialRect.w / 2, pDialRect.y + pDialRect.h / 2);
          ctx.lineTo(pDialRect.x + pDialRect.w * 0.28, pDialRect.y + pDialRect.h * 0.28);
          ctx.stroke();
          ctx.restore();
        }

        // 3. Paint Starfield in Observatory Chamber Wall
        const starRect = getFaceRect("chamber_starfield_wall_glow", "north");
        if (starRect) {
          ctx.save();
          // Deep cosmic void gradient
          const bg = ctx.createRadialGradient(
            starRect.x + starRect.w / 2, starRect.y + starRect.h / 2, 2,
            starRect.x + starRect.w / 2, starRect.y + starRect.h / 2, starRect.w / 2
          );
          bg.addColorStop(0, "#281048");
          bg.addColorStop(0.6, "#140828");
          bg.addColorStop(1, "#070210");
          ctx.fillStyle = bg;
          ctx.fillRect(starRect.x, starRect.y, starRect.w, starRect.h);

          // Twinkling stars
          const stars = [
            [0.2, 0.25, "#ffffff", 1],
            [0.8, 0.22, "#a0e0ff", 1.2],
            [0.15, 0.75, "#e0b0ff", 1],
            [0.85, 0.8, "#ffffff", 0.9],
            [0.35, 0.15, "#ffe0ff", 0.8],
            [0.72, 0.68, "#80c0ff", 1.1],
            [0.5, 0.88, "#ffffff", 1]
          ];
          stars.forEach(([sx, sy, color, rad]) => {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(starRect.x + starRect.w * sx, starRect.y + starRect.h * sy, rad, 0, Math.PI * 2);
            ctx.fill();
          });
          ctx.restore();
        }

        // 4. Paint Status LED Gem Highlights
        const greenLEDs = ["indicator_gem_green1_glow", "indicator_gem_green2_glow", "indicator_gem_green3_glow"];
        greenLEDs.forEach(name => {
          const r = getFaceRect(name, "up");
          if (r) {
            ctx.fillStyle = "#8affb8";
            ctx.fillRect(r.x + 1, r.y + 1, Math.max(1, r.w - 2), Math.max(1, r.h - 2));
          }
        });
        const redLEDs = ["indicator_gem_red1_glow", "indicator_gem_red2_glow", "indicator_gem_red3_glow"];
        redLEDs.forEach(name => {
          const r = getFaceRect(name, "up");
          if (r) {
            ctx.fillStyle = "#ff8aa0";
            ctx.fillRect(r.x + 1, r.y + 1, Math.max(1, r.w - 2), Math.max(1, r.h - 2));
          }
        });

        // 5. Paint Rear Ventilation Slats
        const slatsRect = getFaceRect("back_upper_slats", "south");
        if (slatsRect) {
          ctx.save();
          ctx.fillStyle = "#0e0b12";
          ctx.fillRect(slatsRect.x, slatsRect.y, slatsRect.w, slatsRect.h);
          // Vertical grille slats
          for (let gx = slatsRect.x + 2; gx < slatsRect.x + slatsRect.w - 2; gx += 3) {
            ctx.fillStyle = "#2c2636";
            ctx.fillRect(gx, slatsRect.y + 1, 1.5, slatsRect.h - 2);
          }
          ctx.restore();
        }

        // 6. Paint Lower Louvers
        ["back_lower_vent1", "back_lower_vent2"].forEach(name => {
          const r = getFaceRect(name, "south");
          if (r) {
            ctx.save();
            ctx.fillStyle = "#100d14";
            ctx.fillRect(r.x, r.y, r.w, r.h);
            // Horizontal louvers
            for (let gy = r.y + 2; gy < r.y + r.h - 2; gy += 3) {
              ctx.fillStyle = "#282230";
              ctx.fillRect(r.x + 1, gy, r.w - 2, 1.5);
            }
            ctx.restore();
          }
        });
      });

      return { painted: true };
    `
  });

  console.log("Fine texture details painted!");
}

main().catch(err => {
  console.error("Error:", err);
  process.exit(1);
});
