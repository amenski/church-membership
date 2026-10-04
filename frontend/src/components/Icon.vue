<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    :fill="filled ? 'currentColor' : 'none'"
    :stroke="filled ? 'none' : 'currentColor'"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(d, index) in paths" :key="index" :d="d" />
  </svg>
</template>

<script>
// The whole icon set, inline: no icon font, and only the glyphs named here reach the bundle.
// 24x24, stroked with currentColor unless the glyph is filled. A caller sizes it with a class
// (`class="size-4"`) or the `size` prop.
const ICONS = {
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  x: ['M6 6l12 12', 'M18 6L6 18'],
  plus: ['M12 5v14', 'M5 12h14'],
  download: ['M12 3v12', 'M7 10l5 5 5-5', 'M4 20h16'],
  'more-horizontal': ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
  home: ['M3 11 12 3l9 8', 'M5 9.5V21h14V9.5'],
  phone: ['M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z'],
  users: [
    'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2',
    'M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75'
  ],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0'],
  banknote: ['M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z', 'M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0'],
  'message-square': ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'],
  clock: ['M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0', 'M12 7v5l3 2'],
  'log-out': ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'M16 17l5-5-5-5', 'M21 12H9'],
  'chevron-left': ['M15 6l-6 6 6 6'],
  'chevron-right': ['M9 6l6 6-6 6'],
  'layout-grid': ['M3 3h7v7H3z', 'M14 3h7v7h-7z', 'M3 14h7v7H3z', 'M14 14h7v7h-7z'],
  'chevron-up': ['M6 15l6-6 6 6'],
  'chevron-down': ['M6 9l6 6 6-6'],
  'chevrons-up-down': ['M7 15l5 5 5-5', 'M7 9l5-5 5 5']
}

// Filled glyphs: a direction marker reads better solid than as a hairline
const FILLED = {
  'caret-up': ['M12 8l6 8H6z'],
  'caret-down': ['M12 16l6-8H6z']
}

export default {
  name: 'Icon',
  props: {
    name: {
      type: String,
      required: true,
      validator: value => value in ICONS || value in FILLED
    },
    size: { type: [Number, String], default: 16 },
    strokeWidth: { type: [Number, String], default: 1.75 }
  },
  computed: {
    paths() {
      return ICONS[this.name] || FILLED[this.name]
    },
    filled() {
      return this.name in FILLED
    }
  }
}
</script>
